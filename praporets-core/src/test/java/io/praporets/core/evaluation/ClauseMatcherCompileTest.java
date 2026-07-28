package io.praporets.core.evaluation;

import io.praporets.core.model.Clause;
import io.praporets.core.model.Operator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * compile() перетворює сирі values на типізовані матчери, парсячи один раз
 * і мовчки відкидаючи невалідне (S4).
 */
class ClauseMatcherCompileTest {

    @Test
    void in_compiles_to_deduplicated_set() {
        var matcher = ClauseMatcher.compile(
                new Clause("country", Operator.IN, List.of("UA", "PL", "UA"), false));

        assertThat(matcher).isEqualTo(new ClauseMatcher.In(java.util.Set.of("UA", "PL")));
    }

    @Test
    void string_operators_compile_to_text_matcher() {
        var matcher = ClauseMatcher.compile(
                new Clause("plan", Operator.STARTS_WITH, List.of("pro"), false));

        assertThat(matcher).isEqualTo(
                new ClauseMatcher.Text(ClauseMatcher.TextOp.STARTS_WITH, List.of("pro")));
    }

    @Test
    void numeric_compile_parses_bounds_and_drops_invalid() {
        var matcher = ClauseMatcher.compile(
                new Clause("age", Operator.GREATER_THAN, List.of("18", "abc", "21.5"), false));

        assertThat(matcher).isEqualTo(new ClauseMatcher.Numeric(
                ClauseMatcher.NumericOp.GREATER_THAN,
                List.of(new BigDecimal("18"), new BigDecimal("21.5"))));
    }

    @Test
    void semver_compile_parses_versions_and_drops_invalid() {
        var matcher = ClauseMatcher.compile(
                new Clause("appVersion", Operator.SEMVER_GREATER_OR_EQUAL,
                        List.of("5.2", "not-a-version"), false));

        assertThat(matcher).isEqualTo(
                new ClauseMatcher.SemverAtLeast(List.of(new Semver(5, 2, 0))));
    }

    @Test
    void in_segment_compiles_to_segment_keys() {
        var matcher = ClauseMatcher.compile(
                new Clause("segment", Operator.IN_SEGMENT, List.of("beta-testers"), false));

        assertThat(matcher).isEqualTo(
                new ClauseMatcher.InSegment(java.util.Set.of("beta-testers")));
    }
}
