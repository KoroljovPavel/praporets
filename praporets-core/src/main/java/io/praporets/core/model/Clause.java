package io.praporets.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Одна умова таргетингу: порівняння атрибута користувача зі списком значень.
 *
 * <p>Приклад: {@code country IN [UA, PL]}, {@code appVersion SEMVER_GREATER_OR_EQUAL [5.2]},
 * {@code negate=true} інвертує результат («НЕ з UA»).
 *
 * <p>Це «сирі» дані конфігурації (як вони приходять із proto). Поведінка живе в
 * {@code evaluation}: {@code ClauseMatcher.compile(clause)} перетворює clause на
 * типізований матчер, {@code ClauseEvaluator} обчислює його над контекстом.
 *
 * <p><b>Інваріанти:</b> {@code attribute} не blank; {@code operator} не {@code null};
 * {@code values} — незмінна захисна копія без {@code null}-елементів. Порожній
 * {@code values} легальний (такий clause просто нікого не матчить — рішення S6).
 * Для {@code IN_SEGMENT} у {@code values} лежать ключі сегментів, а {@code attribute}
 * за конвенцією {@code "segment"} (не інтерпретується).
 *
 * @param attribute ім'я атрибута контексту ({@code country}, {@code plan}, {@code appVersion},
 *                  псевдо-атрибут {@code userKey} — див. {@link EvaluationContext#attribute})
 * @param operator  оператор порівняння
 * @param values    значення для порівняння, OR-семантика між ними (S1)
 * @param negate    інвертувати результат порівняння (S2)
 */
public record Clause(String attribute, Operator operator, List<String> values, boolean negate) {

    /**
     * @throws NullPointerException     якщо {@code attribute}, {@code operator},
     *                                  {@code values} або елемент списку {@code null}
     * @throws IllegalArgumentException якщо {@code attribute} blank
     */
    public Clause {
        values = List.copyOf(values);

        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(operator, "operator");
        if (attribute.isBlank()) throw new IllegalArgumentException("attribute must be non-blank");
    }
}
