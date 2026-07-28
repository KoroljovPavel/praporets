package io.praporets.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Іменована група користувачів, визначена набором умов: «користувачі з UA
 * з версією застосунку ≥ 5.2».
 *
 * <p>Користувач належить до сегмента, якщо збігаються <b>усі</b> його clauses (AND, S1).
 * Порожній список clauses — сегмент «усі користувачі» (S6). Clause з оператором
 * {@code IN_SEGMENT} всередині сегмента не матчиться ніколи — сегменти не
 * вкладаються (S5).
 *
 * <p><b>Інваріанти:</b> {@code key} не blank; {@code clauses} — незмінна захисна
 * копія без {@code null}-елементів.
 *
 * @param key     унікальний ключ сегмента в межах середовища (напр. {@code "beta-testers"})
 * @param clauses умови членства, AND між ними
 */
public record Segment(String key, List<Clause> clauses) {

    /**
     * @throws NullPointerException     якщо {@code key}, {@code clauses} або елемент {@code null}
     * @throws IllegalArgumentException якщо {@code key} blank
     */
    public Segment {
        clauses = List.copyOf(clauses);

        Objects.requireNonNull(key, "key");
        if (key.isBlank()) throw new IllegalArgumentException("key must be non-blank");
    }
}
