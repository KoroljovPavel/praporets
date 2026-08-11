package io.praporets.core.model;

/**
 * Оператор порівняння у {@link Clause}. Дзеркалить enum {@code Operator}
 * з proto-контракту {@code praporets.config.v1}.
 *
 * <p>Семантика кожного оператора визначена в {@link io.praporets.core.evaluation.ClauseEvaluator}.
 */
public enum Operator {
    /** Значення атрибута точно збігається з одним із {@code values} (регістрозалежно). */
    IN,
    /** Значення атрибута починається з одного з {@code values}. */
    STARTS_WITH,
    /** Значення атрибута закінчується одним із {@code values}. */
    ENDS_WITH,
    /** Значення атрибута містить один із {@code values}. */
    CONTAINS,
    /** Числове порівняння: атрибут строго більший за одне з {@code values}. */
    GREATER_THAN,
    /** Числове порівняння: атрибут строго менший за одне з {@code values}. */
    LESS_THAN,
    /** Семантичне версійне порівняння: атрибут ≥ одної з версій у {@code values}. */
    SEMVER_GREATER_OR_EQUAL,
    /** Користувач належить до одного з сегментів, чиї ключі перелічені у {@code values}. */
    IN_SEGMENT
}
