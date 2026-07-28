package io.praporets.core.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemverTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}.{2}.{3}")
    @CsvSource({
            "5,       5, 0, 0",
            "5.2,     5, 2, 0",
            "5.2.1,   5, 2, 1",
            "0.0.0,   0, 0, 0",
            "10.20.30, 10, 20, 30",
    })
    void parses_valid_versions_padding_missing_parts_with_zero(String raw, int major, int minor, int patch) {
        assertThat(Semver.parse(raw)).contains(new Semver(major, minor, patch));
    }

    @ParameterizedTest(name = "\"{0}\" is invalid")
    @ValueSource(strings = {
            "", " ", "v5", "5.2.1-beta", "5.x", "5..1", ".5", "5.",
            "5.2.1.4", "5,2", "five", "5 .2", "-1.0.0",
    })
    void rejects_invalid_formats(String raw) {
        assertThat(Semver.parse(raw)).isEmpty();
    }

    @Test
    void compares_numerically_not_lexicographically() {
        // лексикографічно "5.10.0" < "5.9.0" — числово навпаки; це суть класу
        assertThat(new Semver(5, 10, 0)).isGreaterThan(new Semver(5, 9, 9));
    }

    @ParameterizedTest(name = "{0} vs {1}")
    @CsvSource({
            "1.0.0, 1.0.0,  0",
            "1.0.1, 1.0.0,  1",
            "1.0.0, 1.0.1, -1",
            "1.1.0, 1.0.9,  1",
            "2.0.0, 1.9.9,  1",
    })
    void compareTo_orders_by_major_then_minor_then_patch(String left, String right, int expectedSign) {
        Semver l = Semver.parse(left).orElseThrow();
        Semver r = Semver.parse(right).orElseThrow();

        assertThat(Integer.signum(l.compareTo(r))).isEqualTo(expectedSign);
    }

    @Test
    void rejects_negative_components() {
        assertThatThrownBy(() -> new Semver(1, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_input_yields_npe() {
        assertThatThrownBy(() -> Semver.parse(null))
                .isInstanceOf(NullPointerException.class);
    }
}
