package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.*;
import io.praporets.controlplane.common.GroupingHelper;
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
 * Відкат середовища на стару ревізію: журнал відтворює старий стан НОВИМИ
 * ревізіями, історія не переписується. Це окупність рішення класти в журнал
 * повний стан сутності — журнал і є бекапом.
 *
 * <p>Алгоритм:
 * <ol>
 *   <li>записи журналу з {@code revision ≤ toRevision} групуються за
 *       сутністю: {@code FLAG_CONFIG_UPDATED} і {@code FLAG_TOGGLED} — разом
 *       «стан конфіга» за {@code payload.flagKey}, {@code SEGMENT_UPDATED} —
 *       за {@code payload.key}; з кожної групи береться НАЙПІЗНІШИЙ payload
 *       (вибірка вже відсортована desc — перший зустрінутий і є найпізніший);</li>
 *   <li>відновлення йде через ЗВИЧАЙНІ сервісні шляхи
 *       ({@code FlagConfigService}, {@code SegmentService}) — кожне
 *       відновлення = нова ревізія + аудит. If-Match сервісного upsert
 *       обходиться свідомо: поточна версія конфіга читається з БД і
 *       передається як {@code expectedVersion};</li>
 *   <li>сутності, створені ПІСЛЯ {@code toRevision} (немає записів ≤), не
 *       чіпаються — задокументоване обмеження: DELETE-ів у API немає, тож
 *       відкат не може їх прибрати;</li>
 *   <li>наприкінці пишеться підсумковий аудит-запис {@code action=ROLLBACK}
 *       по середовищу.</li>
 * </ol>
 *
 * <p><b>ВЕСЬ відкат — одна транзакція</b> ({@code @Transactional} на
 * верхньому методі; сервісні виклики джойняться): або все, або ніщо.
 * Порядок відновлення сутностей недетермінований (ітерація мапи) — на нього
 * не можна покладатися.
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
     *                                   {@code [1, поточна]}
     */
    @Transactional
    public RollbackResponse rollback(String environmentKey, long toRevision, String actor) {
        Environment environment = environmentRepository.findByKey(environmentKey)
            .orElseThrow(() -> new NotFoundException("Environment not found: " + environmentKey));
        if (!isValidRevision(toRevision, environment))
            throw new DomainValidationException("Revision out of range:  " + toRevision);

        JsonNode prevRevision = jsonMapper.createObjectNode().put("revision", environment.getRevision());

        Map<GroupingHelper.GroupingKey, RevisionLogEntry> revisions = revisionLogRepository.findByEnvironmentKeyAndRevisionLessThanEqualOrderByRevisionDesc(environmentKey, toRevision)
            .stream()
            .collect(Collectors.toMap(
                revisionLogEntry -> GroupingHelper.extractGroupingKey(revisionLogEntry, jsonMapper),
                Function.identity(),
                (existing, _) -> existing,
                LinkedHashMap::new
            ));

        for (Map.Entry<GroupingHelper.GroupingKey, RevisionLogEntry> entry : revisions.entrySet()) {
            switch (entry.getKey().kind()) {
                case GroupingHelper.EntityType.FLAG -> {
                    long currentVersion = flagConfigRepository.findByFlagKeyAndEnvironmentKey(entry.getKey().key(), environment.getKey())
                        .orElseThrow(() -> new IllegalStateException("FlagConfig with key [" + entry.getKey().key() + "] can't be absent"))
                        .getVersion();
                    FlagConfigResponse flagConfigResponse = jsonMapper.treeToValue(entry.getValue().getPayload(),  FlagConfigResponse.class);
                    UpsertFlagConfigRequest request = new UpsertFlagConfigRequest(flagConfigResponse.enabled(), flagConfigResponse.defaultVariant(),
                        flagConfigResponse.offVariant(), flagConfigResponse.rules(), flagConfigResponse.rollout());
                    flagConfigService.upsert(environment.getKey(), entry.getKey().key(), currentVersion, request, actor);
                }
                case GroupingHelper.EntityType.SEGMENT -> {
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

    private boolean isValidRevision(long revision, Environment environment) {
        return revision >= 1 && revision <= environment.getRevision();
    }
}
