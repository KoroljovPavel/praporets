package io.praporets.core.revision;

import io.praporets.core.model.EnvironmentConfig;
import io.praporets.core.model.FlagDefinition;
import io.praporets.core.model.Segment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Чиста функція застосування {@link Delta} до {@link EnvironmentConfig}:
 * повертає НОВИЙ конфіг, вхідний не змінюється (E-02: atomic swap на edge —
 * підмінюється цілий незмінний об'єкт, читачі бачать або старий, або новий).
 *
 * <p><b>Семантика (пінять тести, TDD):</b>
 * <ul>
 *   <li>upsert замінює сутність із тим самим ключем або додає нову;</li>
 *   <li>remove видаляє за ключем; невідомий ключ — тихий no-op (дельти
 *       можуть приходити повторно, ідемпотентність);</li>
 *   <li>той самий ключ і в remove, і в upsert однієї дельти → перемагає
 *       upsert (порядок: спершу видалення, потім вставки);</li>
 *   <li>порожня дельта → конфіг, рівний вхідному;</li>
 *   <li>інваріант {@code EnvironmentConfig} «ключ мапи = ключ сутності»
 *       зберігається конструктором результату.</li>
 * </ul>
 *
 * <p>Статичний утиліт-клас без стану — у стилі {@code Evaluator}.
 */
public final class DeltaApplier {

    private DeltaApplier() {
    }

    public static EnvironmentConfig apply(EnvironmentConfig current, Delta delta) {
        Map<String, FlagDefinition> flags = new HashMap<>(current.flags());
        Optional.ofNullable(delta.removedFlagKeys()).ifPresent(flags.keySet()::removeAll);
        Optional.ofNullable(delta.upsertedFlags()).ifPresent(list ->
            list.forEach(flag -> flags.put(flag.key(), flag)));

        Map<String, Segment> segments = new HashMap<>(current.segments());
        Optional.ofNullable(delta.removedSegmentKeys()).ifPresent(segments.keySet()::removeAll);
        Optional.ofNullable(delta.upsertedSegments()).ifPresent(list ->
            list.forEach(segment -> segments.put(segment.key(), segment)));

        return new EnvironmentConfig(flags, segments);
    }
}
