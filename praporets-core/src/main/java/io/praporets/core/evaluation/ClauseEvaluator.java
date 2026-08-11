package io.praporets.core.evaluation;

import io.praporets.core.model.Clause;
import io.praporets.core.model.EvaluationContext;
import io.praporets.core.model.Segment;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Обчислення умов таргетингу над контекстом користувача. Єдине місце зі switch
 * по sealed-ієрархії {@link ClauseMatcher} — <b>без {@code default}</b>, повноту
 * гарантує компілятор.
 *
 * <p><b>Семантика матчингу:</b>
 * <ul>
 *   <li>між clauses — AND ({@link #matchesAll}), між values одного clause — OR;</li>
 *   <li>відсутній атрибут → базовий результат false; {@code negate} інвертує
 *       будь-який базовий результат (negate + відсутній атрибут = true);</li>
 *   <li>атрибут {@code "userKey"} резолвиться з {@link EvaluationContext#userKey()};</li>
 *   <li>обчислення тотальне — ніколи не кидає через вміст даних (невалідне
 *       значення атрибута → false); NPE лише за null-аргументи;</li>
 *   <li>невідомий сегмент → false; IN_SEGMENT всередині сегмента → false
 *       (сегменти не вкладаються);</li>
 *   <li>порожні values → false; сегмент із порожніми clauses матчить усіх;</li>
 *   <li>рядкові порівняння чутливі до регістру.</li>
 * </ul>
 */
public final class ClauseEvaluator {

    private ClauseEvaluator() {
    }

    /**
     * Чи збігається одна умова з контекстом.
     *
     * @param clause   умова (не {@code null})
     * @param context  контекст користувача (не {@code null})
     * @param segments сегменти середовища за ключем, для {@code IN_SEGMENT} (не {@code null})
     * @return результат з урахуванням {@code clause.negate()}
     * @throws NullPointerException якщо будь-який аргумент {@code null}
     */
    public static boolean matches(Clause clause, EvaluationContext context, Map<String, Segment> segments) {
        Objects.requireNonNull(clause, "clause");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(segments, "segments");

        ClauseMatcher matcher = ClauseMatcher.compile(clause);

        boolean result = switch (matcher) {
            case ClauseMatcher.OfAttribute ofAttributeMatcher -> context.attribute(clause.attribute())
                .map(value -> matchesValue(ofAttributeMatcher, value)).orElse(false);
            case ClauseMatcher.InSegment inSegmentMatcher  -> inSegmentMatcher(inSegmentMatcher, context, segments);
        };

        return clause.negate() != result;
    }

    private static boolean matchesValue(ClauseMatcher.OfAttribute matcher, String value) {
        return switch (matcher) {
            case ClauseMatcher.In inMatcher                -> inMatcher(inMatcher, value);
            case ClauseMatcher.Text textMatcher            -> textMatcher(textMatcher, value);
            case ClauseMatcher.Numeric numericMatcher      -> numericMatcher(numericMatcher, value);
            case ClauseMatcher.SemverAtLeast semverMatcher -> semverMatcher(semverMatcher, value);
        };
    }

    private static boolean inMatcher(ClauseMatcher.In inMatcher, String attribute) {
        return inMatcher.values().contains(attribute);
    }

    private static boolean textMatcher(ClauseMatcher.Text textMatcher, String attribute) {
        return switch (textMatcher.op()) {
            case STARTS_WITH -> textMatcher.values().stream().anyMatch(attribute::startsWith);
            case ENDS_WITH   -> textMatcher.values().stream().anyMatch(attribute::endsWith);
            case CONTAINS    -> textMatcher.values().stream().anyMatch(attribute::contains);
        };
    }

    private static boolean numericMatcher(ClauseMatcher.Numeric numericMatcher, String attribute) {
        return Numbers.parse(attribute)
            .map(value ->
                switch (numericMatcher.op()) {
                    case GREATER_THAN -> numericMatcher.bounds().stream().anyMatch(bound -> value.compareTo(bound) > 0);
                    case LESS_THAN -> numericMatcher.bounds().stream().anyMatch(bound -> value.compareTo(bound) < 0);
                })
            .orElse(false);
    }

    private static boolean semverMatcher(ClauseMatcher.SemverAtLeast semverMatcher, String attribute) {
        return Semver.parse(attribute)
            .map(s -> semverMatcher.minimums().stream().anyMatch(m -> s.compareTo(m) >= 0))
            .orElse(false);
    }

    private static boolean inSegmentMatcher(ClauseMatcher.InSegment inSegmentMatcher, EvaluationContext context, Map<String, Segment> segments) {
        return inSegmentMatcher.segmentKeys().stream()
            .filter(segments::containsKey)
            .anyMatch(s -> matchesAll(segments.get(s).clauses(), context, Map.of()));
    }

    /**
     * Чи збігаються <b>усі</b> умови (AND, S1). Порожній список → {@code true}
     * (нема умов — нема заперечень; саме так сегмент без clauses матчить усіх, S6).
     *
     * @throws NullPointerException якщо будь-який аргумент {@code null}
     */
    public static boolean matchesAll(List<Clause> clauses, EvaluationContext context, Map<String, Segment> segments) {
        Objects.requireNonNull(clauses, "clauses");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(segments, "segments");

        return clauses.stream().allMatch(clause -> matches(clause, context, segments));
    }
}
