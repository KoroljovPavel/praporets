package io.praporets.controlplane.grpc;

import io.praporets.controlplane.domain.FlagConfigRepository;
import io.praporets.controlplane.domain.RevisionLogRepository;
import io.praporets.controlplane.domain.SegmentRepository;
import io.praporets.grpc.config.v1.ConfigDelta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    private final ConfigProtoMapper mapper;
    private final SegmentRepository segmentRepository;
    private final FlagConfigRepository flagConfigRepository;
    private final RevisionLogRepository revisionLogRepository;

    public DeltaAssembler(ConfigProtoMapper mapper,
                          SegmentRepository segmentRepository,
                          FlagConfigRepository flagConfigRepository,
                          RevisionLogRepository revisionLogRepository) {
        this.mapper = mapper;
        this.segmentRepository = segmentRepository;
        this.flagConfigRepository = flagConfigRepository;
        this.revisionLogRepository = revisionLogRepository;
    }

    @Transactional(readOnly = true)
    public ConfigDelta assembleSince(String environmentKey, long fromRevision) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }
}
