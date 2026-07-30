package io.praporets.core.revision;

import io.praporets.core.model.FlagDefinition;
import io.praporets.core.model.Segment;

import java.util.List;
import java.util.Set;

/**
 * Доменна дельта конфігурації середовища — core-двійник proto
 * {@code praporets.config.v1.ConfigDelta}, без залежності від protobuf
 * (ядро не знає про транспорт).
 *
 * <p>Семантику застосування визначає {@link DeltaApplier}. Колекції
 * захисно копіюються — record незмінний, як і решта core-моделі.
 *
 * @param upsertedFlags      флаги для вставки/заміни (ключ — {@code key()})
 * @param removedFlagKeys    ключі флагів до видалення
 * @param upsertedSegments   сегменти для вставки/заміни
 * @param removedSegmentKeys ключі сегментів до видалення
 */
public record Delta(
    List<FlagDefinition> upsertedFlags,
    Set<String> removedFlagKeys,
    List<Segment> upsertedSegments,
    Set<String> removedSegmentKeys
) {
    public Delta {
        upsertedFlags = List.copyOf(upsertedFlags);
        removedFlagKeys = Set.copyOf(removedFlagKeys);
        upsertedSegments = List.copyOf(upsertedSegments);
        removedSegmentKeys = Set.copyOf(removedSegmentKeys);
    }
}
