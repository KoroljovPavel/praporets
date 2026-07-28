package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.*;
import io.praporets.controlplane.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Відкат середовища на стару ревізію (CP-06, H4–H6): журнал відтворює старий
 * стан НОВИМИ ревізіями, історія не переписується. Окупність G8 — payload
 * у журналі є повним станом сутності, тож журнал і є бекапом.
 *
 * <p><b>Алгоритм (твоя робота):</b>
 * <ol>
 *   <li>середовище за ключем → немає → {@link NotFoundException}; H6:
 *       {@code toRevision} поза {@code [1, поточна]} →
 *       {@link DomainValidationException} → 400;</li>
 *   <li>усі записи журналу з {@code revision ≤ toRevision}
 *       (новий finder у {@code RevisionLogRepository}, вже додано);</li>
 *   <li>групування за сутністю: {@code FLAG_CONFIG_UPDATED} і
 *       {@code FLAG_TOGGLED} — разом «стан конфіга» за {@code payload.flagKey}
 *       (камінь #5); {@code SEGMENT_UPDATED} — за {@code payload.key}.
 *       З кожної групи береться НАЙПІЗНІШИЙ payload (сортування вже desc —
 *       перший зустрінутий і є найпізніший);</li>
 *   <li>десеріалізація payload: {@code jsonMapper.treeToValue(payload,
 *       FlagConfigResponse.class / SegmentResponse.class)};</li>
 *   <li>відновлення через ЗВИЧАЙНІ сервісні шляхи ({@code FlagConfigService},
 *       {@code SegmentService}) — кожне = нова ревізія + аудит. If-Match
 *       сервісного upsert обходь свідомо: внутрішній виклик знає поточну
 *       версію (напр., прочитай config з БД і передай його
 *       {@code getVersion()} як {@code expectedVersion}); сутності, що
 *       існують у журналі, але ще не в БД, тут неможливі (H5);</li>
 *   <li>сутності, створені ПІСЛЯ {@code toRevision} (немає записів ≤),
 *       не чіпаються — H5, задокументоване обмеження
 *       ({@code docs/backlog.md} → «symmetric deletes for rollback»);</li>
 *   <li>відповідь: {@code rolledBackTo = toRevision}, {@code revision} —
 *       нова поточна (з Environment ПІСЛЯ відновлень; пам'ятай про
 *       write-behind — recorder повертає нову ревізію, можеш взяти max).</li>
 * </ol>
 *
 * <p><b>Аудит ROLLBACK (H4):</b> сервісні шляхи запишуть UPDATE/аудит самі;
 * {@code action=ROLLBACK} додай як хочеш — або окремим підсумковим записом
 * аудиту по середовищу після відновлень, або протягни action через сервіси.
 * Тести пінять лише ревізії і стани, не аудит — рішення твоє.
 *
 * <p><b>ВЕСЬ відкат — одна транзакція</b> ({@code @Transactional} на верхньому
 * методі; сервісні виклики джойняться, камінь #3): або все, або ніщо.
 * Порядок відновлення сутностей недетермінований (мапа) — не покладайся
 * на нього (камінь #4).
 */
@Service
public class RollbackService {

    private final JsonMapper jsonMapper;
    private final SegmentService segmentService;
    private final RevisionRecorder revisionRecorder;
    private final FlagConfigService flagConfigService;
    private final FlagConfigRepository flagConfigRepository;
    private final EnvironmentRepository environmentRepository;
    private final RevisionLogRepository revisionLogRepository;

    public RollbackService(JsonMapper jsonMapper, EnvironmentRepository environmentRepository,
                           RevisionLogRepository revisionLogRepository, FlagConfigRepository flagConfigRepository,
                           FlagConfigService flagConfigService, SegmentService segmentService, RevisionRecorder revisionRecorder) {
        this.jsonMapper = jsonMapper;
        this.segmentService = segmentService;
        this.revisionRecorder = revisionRecorder;
        this.flagConfigService = flagConfigService;
        this.flagConfigRepository = flagConfigRepository;
        this.environmentRepository = environmentRepository;
        this.revisionLogRepository = revisionLogRepository;
    }

    /**
     * @throws NotFoundException         якщо середовища немає
     * @throws DomainValidationException якщо {@code toRevision} поза
     *                                   {@code [1, поточна]} (H6)
     */
    @Transactional
    public RollbackResponse rollback(String environmentKey, long toRevision, String actor) {
        Environment environment = environmentRepository.findByKey(environmentKey)
            .orElseThrow(() -> new NotFoundException("Environment not found: " + environmentKey));
        if (!isValidRevision(toRevision, environment))
            throw new DomainValidationException("Revision out of range:  " + toRevision);

        JsonNode prevRevision = jsonMapper.createObjectNode().put("revision", environment.getRevision());

        Map<GroupingKey, RevisionLogEntry> revisions = revisionLogRepository.findByEnvironmentKeyAndRevisionLessThanEqualOrderByRevisionDesc(environmentKey, toRevision)
            .stream()
            .collect(Collectors.toMap(
                this::extractGroupingKey,
                Function.identity(),
                (existing, replacement) -> existing,
                LinkedHashMap::new
            ));

        for (Map.Entry<GroupingKey, RevisionLogEntry> entry : revisions.entrySet()) {
            switch (entry.getKey().kind()) {
                case "FLAG" -> {
                    long currentVersion = flagConfigRepository.findByFlagKeyAndEnvironmentKey(entry.getKey().key(), environment.getKey())
                        .orElseThrow(() -> new IllegalStateException("FlagConfig with key [" + entry.getKey().key() + "] can't be absent"))
                        .getVersion();
                    FlagConfigResponse flagConfigResponse = jsonMapper.treeToValue(entry.getValue().getPayload(),  FlagConfigResponse.class);
                    UpsertFlagConfigRequest request = new UpsertFlagConfigRequest(flagConfigResponse.enabled(), flagConfigResponse.defaultVariant(),
                        flagConfigResponse.offVariant(), flagConfigResponse.rules(), flagConfigResponse.rollout());
                    flagConfigService.upsert(environment.getKey(), entry.getKey().key(), currentVersion, request, actor);
                }
                case "SEGMENT" -> {
                    SegmentResponse segmentResponse = jsonMapper.treeToValue(entry.getValue().getPayload(), SegmentResponse.class);
                    UpsertSegmentRequest request = new UpsertSegmentRequest(segmentResponse.conditions());
                    segmentService.upsert(environment.getKey(), entry.getKey().key(), request, actor);
                }
            }
        }

        RollbackResponse response = new RollbackResponse(environment.getKey(), toRevision, environment.getRevision());

        revisionRecorder.audit(actor, "ROLLBACK", "ENVIRONMENT", environment.getId(), prevRevision, jsonMapper.valueToTree(response));

        return response;
    }

    private GroupingKey extractGroupingKey(RevisionLogEntry revision) {
        return switch (revision.getChangeType()) {
            case FLAG_CONFIG_UPDATED, FLAG_TOGGLED -> new GroupingKey("FLAG", jsonMapper.treeToValue(revision.getPayload(), FlagConfigResponse.class).flagKey());
            case SEGMENT_UPDATED -> new GroupingKey("SEGMENT", jsonMapper.treeToValue(revision.getPayload(), SegmentResponse.class).key());
        };
    }

    private boolean isValidRevision(long revision, Environment environment) {
        return revision >= 1 && revision <= environment.getRevision();
    }

    private record GroupingKey(String kind, String key) {}
}
