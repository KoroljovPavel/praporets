package io.praporets.controlplane.grpc;

import io.praporets.controlplane.common.GroupingHelper;
import io.praporets.controlplane.domain.FlagConfigRepository;
import io.praporets.controlplane.domain.RevisionLogEntry;
import io.praporets.controlplane.domain.RevisionLogRepository;
import io.praporets.controlplane.domain.SegmentRepository;
import io.praporets.grpc.config.v1.ConfigDelta;
import io.praporets.grpc.config.v1.FlagDefinition;
import io.praporets.grpc.config.v1.SegmentDefinition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Склеєна дельта ревізій {@code (fromRevision; поточна]} для одного
 * середовища (спека §7.4: edge прийшов із {@code fromRevision=137}, поточна
 * 142 → віддаємо одну дельту 138..142). Використовується двічі:
 * catch-up при підключенні стріму і live-push однієї ревізії
 * ({@code assembleSince(env, revision - 1)}).
 *
 * <p><b>Ключове рішення (B4):</b> дельта збирається з <b>поточного стану БД</b>
 * змінених сутностей, а НЕ з payload-ів журналу. Причини:
 * <ul>
 *   <li>«склеїти latest wins» по N payload-ах — це і є поточний стан;</li>
 *   <li>payload журналу ({@code FlagConfigResponse}) не містить
 *       {@code variants}/{@code value_type} — вони на глобальному
 *       {@code Flag}, а proto {@code FlagDefinition} їх вимагає.</li>
 * </ul>
 * Журнал потрібен лише щоб дізнатися, <b>які</b> сутності змінилися.
 *
 * <p><b>Реалізація (твоя робота):</b>
 * <ol>
 *   <li>{@code revisionLogRepository
 *       .findByEnvironmentKeyAndRevisionGreaterThanOrderByRevisionAsc};</li>
 *   <li>кожен запис → пара (вид, ключ), як у
 *       {@code RollbackService.extractGroupingKey}: {@code FLAG_CONFIG_UPDATED}
 *       / {@code FLAG_TOGGLED} → флаг, ключ у payload-полі {@code flagKey};
 *       {@code SEGMENT_UPDATED} → сегмент, поле {@code key}. Дублікати
 *       схлопнути (порядок далі неважливий — стан і так поточний);</li>
 *   <li>для кожного флага — {@code findByFlagKeyAndEnvironmentKey} →
 *       {@link ConfigProtoMapper#toFlag} в {@code upserted_flags}; для
 *       сегмента — аналогічно в {@code upserted_segments}. Сутності нема в БД
 *       (не буває без DELETE-ендпоінтів, але захищаємось) → пропустити;</li>
 *   <li>{@code removed_flag_keys}/{@code removed_segment_keys} завжди порожні
 *       — DELETE у API немає (свідомо, див. backlog «Symmetric deletes»).</li>
 * </ol>
 */
@Component
public class DeltaAssembler {

    private final JsonMapper jsonMapper;
    private final ConfigProtoMapper configProtoMapper;
    private final SegmentRepository segmentRepository;
    private final FlagConfigRepository flagConfigRepository;
    private final RevisionLogRepository revisionLogRepository;

    public DeltaAssembler(JsonMapper jsonMapper, ConfigProtoMapper configProtoMapper, SegmentRepository segmentRepository,
                          FlagConfigRepository flagConfigRepository, RevisionLogRepository revisionLogRepository) {
        this.jsonMapper = jsonMapper;
        this.configProtoMapper = configProtoMapper;
        this.segmentRepository = segmentRepository;
        this.flagConfigRepository = flagConfigRepository;
        this.revisionLogRepository = revisionLogRepository;
    }

    @Transactional(readOnly = true)
    public ConfigDelta assembleSince(String environmentKey, long fromRevision) {
        Map<GroupingHelper.GroupingKey, RevisionLogEntry> revisions = revisionLogRepository.findByEnvironmentKeyAndRevisionGreaterThanOrderByRevisionAsc(environmentKey, fromRevision)
            .stream()
            .collect(Collectors.toMap(
                revisionLogEntry -> GroupingHelper.extractGroupingKey(revisionLogEntry, jsonMapper),
                Function.identity(),
                (existing, _) -> existing,
                LinkedHashMap::new
            ));

        List<FlagDefinition> flagDefinitionList = new ArrayList<>();
        List<SegmentDefinition> segmentDefinitionList = new ArrayList<>();

        for (Map.Entry<GroupingHelper.GroupingKey, RevisionLogEntry> entry : revisions.entrySet()) {
            switch (entry.getKey().kind()) {
                case GroupingHelper.EntityType.FLAG -> {
                    flagConfigRepository.findByFlagKeyAndEnvironmentKey(entry.getKey().key(), environmentKey).ifPresent(flagConfig -> {
                        flagDefinitionList.add(configProtoMapper.toFlag(flagConfig));
                    });
                }
                case GroupingHelper.EntityType.SEGMENT -> {
                    segmentRepository.findByEnvironmentKeyAndKey(environmentKey, entry.getKey().key()).ifPresent(segment -> {
                        segmentDefinitionList.add(configProtoMapper.toSegment(segment));
                    });
                }
            }
        }

        return ConfigDelta.newBuilder()
            .addAllUpsertedFlags(flagDefinitionList)
            .addAllUpsertedSegments(segmentDefinitionList)
            .build();
    }
}
