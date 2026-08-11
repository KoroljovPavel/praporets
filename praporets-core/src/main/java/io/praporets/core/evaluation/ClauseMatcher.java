package io.praporets.core.evaluation;

import io.praporets.core.model.Clause;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Типізована («скомпільована») форма {@link Clause}: сирі рядки конфігурації
 * перетворені на структуру, готову до обчислення. Парсинг чисел і версій
 * відбувається <b>один раз</b> тут, а не на кожному evaluate.
 *
 * <p>Це sealed-ієрархія — компілятор гарантує, що switch у
 * {@link ClauseEvaluator} покриває всі варіанти <b>без</b> {@code default}
 * (додаси новий матчер — не скомпілюється, доки не обробиш).
 *
 * <p>{@code negate} свідомо НЕ входить у матчери: він застосовується один раз
 * у {@link ClauseEvaluator} до базового результату, інакше кожен матчер
 * дублював би цю логіку.
 */
public sealed interface ClauseMatcher {

    sealed interface OfAttribute extends ClauseMatcher {}

    /**
     * Точний збіг з одним із значень (оператор {@code IN}).
     *
     * @param values значення; {@code Set} — пошук O(1) і дедуплікація
     */
    record In(Set<String> values) implements OfAttribute {
    }

    /**
     * Рядкові операції {@code STARTS_WITH} / {@code ENDS_WITH} / {@code CONTAINS}.
     */
    record Text(TextOp op, List<String> values) implements OfAttribute {
    }

    /**
     * Числове порівняння {@code GREATER_THAN} / {@code LESS_THAN}.
     *
     * @param bounds межі, вже розпарсені; невалідні значення відкинуті при компіляції
     */
    record Numeric(NumericOp op, List<BigDecimal> bounds) implements OfAttribute {
    }

    /**
     * {@code SEMVER_GREATER_OR_EQUAL}: атрибут ≥ одної з мінімальних версій.
     *
     * @param minimums мінімальні версії; невалідні відкинуті при компіляції
     */
    record SemverAtLeast(List<Semver> minimums) implements OfAttribute {
    }

    /**
     * {@code IN_SEGMENT}: користувач належить до одного з сегментів.
     */
    record InSegment(Set<String> segmentKeys) implements ClauseMatcher {
    }

    /** Рядкова операція для {@link Text}. */
    enum TextOp { STARTS_WITH, ENDS_WITH, CONTAINS }

    /** Напрямок порівняння для {@link Numeric}. */
    enum NumericOp { GREATER_THAN, LESS_THAN }

    /**
     * Компілює clause у матчер за {@code clause.operator()}.
     *
     * <p>Значення {@code values}, які не парсяться (не число для
     * Numeric, не версія для SemverAtLeast), мовчки відкидаються — матчер із
     * порожнім списком просто нікого не матчить. Компіляція ніколи не кидає
     * через вміст values.
     *
     * @param clause умова (не {@code null})
     * @return матчер відповідного типу
     * @throws NullPointerException якщо {@code clause} {@code null}
     */
    static ClauseMatcher compile(Clause clause) {
        Objects.requireNonNull(clause, "clause");

        return switch (clause.operator()) {
            case IN -> new In(Set.copyOf(clause.values()));
            case STARTS_WITH, ENDS_WITH, CONTAINS -> new Text(TextOp.valueOf(clause.operator().name()), List.copyOf(clause.values()));
            case GREATER_THAN, LESS_THAN -> new Numeric(NumericOp.valueOf(clause.operator().name()),
                clause.values().stream()
                    .flatMap(v -> Numbers.parse(v).stream())
                    .toList());
            case SEMVER_GREATER_OR_EQUAL -> new SemverAtLeast(clause.values().stream().flatMap(v -> Semver.parse(v).stream()).toList());
            case IN_SEGMENT -> new InSegment(Set.copyOf(clause.values()));
        };
    }
}
