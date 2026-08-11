package io.praporets.core.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagModelTest {

    private static final Rollout FIFTY_FIFTY = new Rollout("salt", List.of(
            new Bucket("a", 50_000), new Bucket("b", 50_000)));

    private static FlagDefinition minimalFlag(String key) {
        return new FlagDefinition(key, true, "on", "off",
                List.of(new Variant("on", "true"), new Variant("off", "false")),
                List.of(), null);
    }

    // --- Variant ---

    @Test
    void variant_rejects_blank_key() {
        assertThatThrownBy(() -> new Variant(" ", "true"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void variant_rejects_null_json_value() {
        assertThatThrownBy(() -> new Variant("on", null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Rule: рівно один результат ---

    @Test
    void rule_with_fixed_variant_is_valid() {
        var rule = new Rule("r1", List.of(), "on", null);

        assertThat(rule.variantKey()).isEqualTo("on");
        assertThat(rule.rollout()).isNull();
    }

    @Test
    void rule_with_rollout_is_valid() {
        var rule = new Rule("r1", List.of(), null, FIFTY_FIFTY);

        assertThat(rule.rollout()).isEqualTo(FIFTY_FIFTY);
    }

    @Test
    void rule_rejects_both_variant_and_rollout() {
        assertThatThrownBy(() -> new Rule("r1", List.of(), "on", FIFTY_FIFTY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rule_rejects_neither_variant_nor_rollout() {
        assertThatThrownBy(() -> new Rule("r1", List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rule_rejects_blank_id() {
        assertThatThrownBy(() -> new Rule(" ", List.of(), "on", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rule_clauses_are_defensively_copied() {
        var source = new ArrayList<Clause>();
        var rule = new Rule("r1", source, "on", null);

        source.add(new Clause("country", Operator.IN, List.of("UA"), false));

        assertThat(rule.clauses()).isEmpty();
    }

    // --- FlagDefinition ---

    @Test
    void flag_rejects_blank_key() {
        assertThatThrownBy(() -> new FlagDefinition(" ", true, "on", "off",
                List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flag_rejects_blank_default_variant() {
        assertThatThrownBy(() -> new FlagDefinition("f", true, " ", "off",
                List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flag_rejects_blank_off_variant() {
        assertThatThrownBy(() -> new FlagDefinition("f", true, "on", " ",
                List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flag_allows_null_rollout_and_empty_lists() {
        var flag = new FlagDefinition("f", true, "on", "off", List.of(), List.of(), null);

        assertThat(flag.rollout()).isNull();
        assertThat(flag.variants()).isEmpty();
        assertThat(flag.rules()).isEmpty();
    }

    @Test
    void flag_lists_are_immutable() {
        var flag = minimalFlag("f");

        assertThatThrownBy(() -> flag.variants().add(new Variant("x", "1")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> flag.rules().add(new Rule("r", List.of(), "on", null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- EnvironmentConfig ---

    @Test
    void config_allows_empty_maps() {
        var config = new EnvironmentConfig(Map.of(), Map.of());

        assertThat(config.flags()).isEmpty();
        assertThat(config.segments()).isEmpty();
    }

    @Test
    void config_rejects_flag_map_key_mismatch() {
        assertThatThrownBy(() -> new EnvironmentConfig(
                Map.of("wrong-key", minimalFlag("f")), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void config_rejects_segment_map_key_mismatch() {
        assertThatThrownBy(() -> new EnvironmentConfig(
                Map.of(), Map.of("wrong-key", new Segment("beta", List.of()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void config_maps_are_immutable() {
        var config = new EnvironmentConfig(Map.of(), Map.of());

        assertThatThrownBy(() -> config.flags().put("f", minimalFlag("f")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
