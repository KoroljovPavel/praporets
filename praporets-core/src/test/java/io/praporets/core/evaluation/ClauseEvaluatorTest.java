package io.praporets.core.evaluation;

import io.praporets.core.model.Clause;
import io.praporets.core.model.EvaluationContext;
import io.praporets.core.model.Operator;
import io.praporets.core.model.Segment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Семантика матчингу — рішення S1–S9 зі спеки кроку 01c. Кожен тест пінить
 * одне продуктове правило.
 */
class ClauseEvaluatorTest {

    private static final Map<String, Segment> NO_SEGMENTS = Map.of();

    private static EvaluationContext user(String... kv) {
        var attrs = new java.util.HashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) {
            attrs.put(kv[i], kv[i + 1]);
        }
        return new EvaluationContext("user-42", attrs);
    }

    private static Clause clause(String attribute, Operator op, String... values) {
        return new Clause(attribute, op, List.of(values), false);
    }

    private static Clause negated(String attribute, Operator op, String... values) {
        return new Clause(attribute, op, List.of(values), true);
    }

    @Nested
    class InOperator {

        @Test
        void matches_exact_value() {
            assertThat(ClauseEvaluator.matches(
                    clause("country", Operator.IN, "UA"),
                    user("country", "UA"), NO_SEGMENTS)).isTrue();
        }

        @Test
        void values_are_or_ed() {
            // S1: OR між values
            assertThat(ClauseEvaluator.matches(
                    clause("country", Operator.IN, "PL", "UA"),
                    user("country", "UA"), NO_SEGMENTS)).isTrue();
        }

        @Test
        void is_case_sensitive() {
            // S7
            assertThat(ClauseEvaluator.matches(
                    clause("country", Operator.IN, "ua"),
                    user("country", "UA"), NO_SEGMENTS)).isFalse();
        }

        @Test
        void empty_values_match_nothing() {
            // S6
            assertThat(ClauseEvaluator.matches(
                    clause("country", Operator.IN),
                    user("country", "UA"), NO_SEGMENTS)).isFalse();
        }
    }

    @Nested
    class TextOperators {

        @Test
        void starts_with() {
            assertThat(ClauseEvaluator.matches(
                    clause("plan", Operator.STARTS_WITH, "pro"),
                    user("plan", "pro-annual"), NO_SEGMENTS)).isTrue();
            assertThat(ClauseEvaluator.matches(
                    clause("plan", Operator.STARTS_WITH, "pro"),
                    user("plan", "basic"), NO_SEGMENTS)).isFalse();
        }

        @Test
        void ends_with() {
            assertThat(ClauseEvaluator.matches(
                    clause("email", Operator.ENDS_WITH, "@praporets.io"),
                    user("email", "dev@praporets.io"), NO_SEGMENTS)).isTrue();
        }

        @Test
        void contains() {
            assertThat(ClauseEvaluator.matches(
                    clause("userAgent", Operator.CONTAINS, "Mobile"),
                    user("userAgent", "Mozilla/5.0 (Mobile; rv:1)"), NO_SEGMENTS)).isTrue();
        }
    }

    @Nested
    class NumericOperators {

        @Test
        void compares_numerically_not_lexicographically() {
            // S9: лексикографічно "10" < "9" — числово навпаки
            assertThat(ClauseEvaluator.matches(
                    clause("age", Operator.GREATER_THAN, "9"),
                    user("age", "10"), NO_SEGMENTS)).isTrue();
        }

        @Test
        void greater_than_is_strict() {
            assertThat(ClauseEvaluator.matches(
                    clause("age", Operator.GREATER_THAN, "18"),
                    user("age", "18"), NO_SEGMENTS)).isFalse();
        }

        @Test
        void less_than_works_with_decimals() {
            assertThat(ClauseEvaluator.matches(
                    clause("score", Operator.LESS_THAN, "1.5"),
                    user("score", "1.49"), NO_SEGMENTS)).isTrue();
        }

        @Test
        void non_numeric_attribute_value_does_not_match() {
            // S4: невалідні дані → false, не виключення
            assertThat(ClauseEvaluator.matches(
                    clause("age", Operator.GREATER_THAN, "18"),
                    user("age", "eighteen"), NO_SEGMENTS)).isFalse();
        }

        @Test
        void any_bound_may_match() {
            // S1: OR між values — 10 > 9, хоча 10 < 100
            assertThat(ClauseEvaluator.matches(
                    clause("age", Operator.GREATER_THAN, "100", "9"),
                    user("age", "10"), NO_SEGMENTS)).isTrue();
        }
    }

    @Nested
    class SemverOperator {

        @Test
        void attribute_at_least_minimum_matches() {
            assertThat(ClauseEvaluator.matches(
                    clause("appVersion", Operator.SEMVER_GREATER_OR_EQUAL, "5.2"),
                    user("appVersion", "5.2.1"), NO_SEGMENTS)).isTrue();
        }

        @Test
        void equal_version_matches() {
            assertThat(ClauseEvaluator.matches(
                    clause("appVersion", Operator.SEMVER_GREATER_OR_EQUAL, "5.2.0"),
                    user("appVersion", "5.2"), NO_SEGMENTS)).isTrue();
        }

        @Test
        void compares_numerically_per_component() {
            // S8: 5.10 > 5.9
            assertThat(ClauseEvaluator.matches(
                    clause("appVersion", Operator.SEMVER_GREATER_OR_EQUAL, "5.9"),
                    user("appVersion", "5.10.0"), NO_SEGMENTS)).isTrue();
        }

        @Test
        void older_version_does_not_match() {
            assertThat(ClauseEvaluator.matches(
                    clause("appVersion", Operator.SEMVER_GREATER_OR_EQUAL, "5.0"),
                    user("appVersion", "4.9.9"), NO_SEGMENTS)).isFalse();
        }

        @Test
        void invalid_attribute_version_does_not_match() {
            // S4
            assertThat(ClauseEvaluator.matches(
                    clause("appVersion", Operator.SEMVER_GREATER_OR_EQUAL, "5.0"),
                    user("appVersion", "beta"), NO_SEGMENTS)).isFalse();
        }
    }

    @Nested
    class InSegmentOperator {

        private final Map<String, Segment> segments = Map.of(
                "ua-users", new Segment("ua-users",
                        List.of(clause("country", Operator.IN, "UA"))),
                "everyone", new Segment("everyone", List.of()),
                "nested", new Segment("nested",
                        List.of(clause("segment", Operator.IN_SEGMENT, "ua-users"))));

        @Test
        void user_matching_all_segment_clauses_is_in_segment() {
            assertThat(ClauseEvaluator.matches(
                    clause("segment", Operator.IN_SEGMENT, "ua-users"),
                    user("country", "UA"), segments)).isTrue();
        }

        @Test
        void user_not_matching_segment_clauses_is_not_in_segment() {
            assertThat(ClauseEvaluator.matches(
                    clause("segment", Operator.IN_SEGMENT, "ua-users"),
                    user("country", "PL"), segments)).isFalse();
        }

        @Test
        void unknown_segment_key_does_not_match() {
            // S5
            assertThat(ClauseEvaluator.matches(
                    clause("segment", Operator.IN_SEGMENT, "ghosts"),
                    user("country", "UA"), segments)).isFalse();
        }

        @Test
        void any_of_segment_keys_may_match() {
            // S1: OR між values
            assertThat(ClauseEvaluator.matches(
                    clause("segment", Operator.IN_SEGMENT, "ghosts", "ua-users"),
                    user("country", "UA"), segments)).isTrue();
        }

        @Test
        void empty_clause_segment_matches_everyone() {
            // S6
            assertThat(ClauseEvaluator.matches(
                    clause("segment", Operator.IN_SEGMENT, "everyone"),
                    user(), segments)).isTrue();
        }

        @Test
        void segments_do_not_nest() {
            // S5: IN_SEGMENT всередині сегмента → false, навіть якщо вкладений збігся б
            assertThat(ClauseEvaluator.matches(
                    clause("segment", Operator.IN_SEGMENT, "nested"),
                    user("country", "UA"), segments)).isFalse();
        }
    }

    @Nested
    class NegateAndMissing {

        @Test
        void missing_attribute_does_not_match() {
            // S2
            assertThat(ClauseEvaluator.matches(
                    clause("country", Operator.IN, "UA"),
                    user(), NO_SEGMENTS)).isFalse();
        }

        @Test
        void negate_inverts_a_match() {
            assertThat(ClauseEvaluator.matches(
                    negated("country", Operator.IN, "UA"),
                    user("country", "UA"), NO_SEGMENTS)).isFalse();
        }

        @Test
        void negate_inverts_a_non_match() {
            assertThat(ClauseEvaluator.matches(
                    negated("country", Operator.IN, "UA"),
                    user("country", "PL"), NO_SEGMENTS)).isTrue();
        }

        @Test
        void negate_with_missing_attribute_matches() {
            // S2: negate інвертує БУДЬ-ЯКИЙ базовий результат, включно з «атрибут відсутній»
            assertThat(ClauseEvaluator.matches(
                    negated("country", Operator.IN, "UA"),
                    user(), NO_SEGMENTS)).isTrue();
        }

        @Test
        void user_key_pseudo_attribute_targets_specific_users() {
            // S3
            assertThat(ClauseEvaluator.matches(
                    clause("userKey", Operator.IN, "user-42"),
                    user(), NO_SEGMENTS)).isTrue();
        }
    }

    @Nested
    class MatchesAll {

        @Test
        void all_clauses_must_match() {
            // S1: AND між clauses
            var clauses = List.of(
                    clause("country", Operator.IN, "UA"),
                    clause("plan", Operator.IN, "pro"));

            assertThat(ClauseEvaluator.matchesAll(clauses,
                    user("country", "UA", "plan", "pro"), NO_SEGMENTS)).isTrue();
            assertThat(ClauseEvaluator.matchesAll(clauses,
                    user("country", "UA", "plan", "basic"), NO_SEGMENTS)).isFalse();
        }

        @Test
        void empty_clause_list_matches() {
            // S6
            assertThat(ClauseEvaluator.matchesAll(List.of(), user(), NO_SEGMENTS)).isTrue();
        }
    }
}
