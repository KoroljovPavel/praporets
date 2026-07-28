package io.praporets.core.model;

import java.util.Map;
import java.util.Objects;

/**
 * Уся конфігурація середовища, потрібна для обчислення: флаги + сегменти.
 * Це те, що edge тримає в пам'яті і атомарно підмінює при отриманні дельти
 * (у етапі 2 сюди додасться {@code revision}).
 *
 * <p><b>Інваріанти:</b> обидві мапи — незмінні захисні копії; для кожного запису
 * ключ мапи дорівнює ключу сутності ({@code flags.get(k).key().equals(k)},
 * аналогічно для сегментів) — розсинхрон цих ключів був би джерелом «невидимих»
 * багів пошуку, тому ловиться на конструюванні.
 *
 * @param flags    флаги середовища за ключем флага
 * @param segments сегменти середовища за ключем сегмента
 */
public record EnvironmentConfig(Map<String, FlagDefinition> flags, Map<String, Segment> segments) {

    /**
     * @throws NullPointerException     якщо мапа, ключ або значення {@code null}
     * @throws IllegalArgumentException якщо ключ мапи не збігається з ключем сутності
     */
    public EnvironmentConfig {
        flags = Map.copyOf(flags);
        segments = Map.copyOf(segments);
        validateMapKeys(flags, "flags");
        validateMapKeys(segments, "segments");
    }

    private static void validateMapKeys(Map<String, ?> map, String mapName) {
        if (!map.entrySet().stream()
            .allMatch(entry -> Objects.equals(entry.getKey(), getInternalKey(entry.getValue()))))
            throw new IllegalArgumentException(mapName + " map keys must match keys in map values keys");
    }

    private static String getInternalKey(Object value) {
        if (value instanceof FlagDefinition f) return f.key();
        if (value instanceof Segment s) return s.key();
        throw new IllegalArgumentException("Unknown type: " + value);
    }
}
