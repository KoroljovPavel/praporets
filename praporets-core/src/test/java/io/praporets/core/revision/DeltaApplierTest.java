package io.praporets.core.revision;

import io.praporets.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 02d, TDD-пара для DeltaApplier: семантика застосування дельти. Ключові
 * властивості — незмінність вхідного конфігу (atomic swap на edge) та
 * ідемпотентність (дельти можуть приходити двічі — catch-up + live із 02b).
 */
class DeltaApplierTest {

    private static FlagDefinition flag(String key, boolean enabled) {
        return new FlagDefinition(key, enabled, "on", "off",
            List.of(new Variant("on", "true"), new Variant("off", "false")),
            List.of(), null);
    }

    private static Segment segment(String key, String plan) {
        return new Segment(key, List.of(new Clause("plan", Operator.IN, List.of(plan), false)));
    }

    private static Delta emptyDelta() {
        return new Delta(List.of(), Set.of(), List.of(), Set.of());
    }

    private final EnvironmentConfig base = new EnvironmentConfig(
        Map.of("checkout.new-flow", flag("checkout.new-flow", true)),
        Map.of("beta-testers", segment("beta-testers", "pro")));

    @Test
    void upserted_flag_replaces_existing_definition() {
        Delta delta = new Delta(List.of(flag("checkout.new-flow", false)), Set.of(), List.of(), Set.of());

        EnvironmentConfig result = DeltaApplier.apply(base, delta);

        assertThat(result.flags().get("checkout.new-flow").enabled()).isFalse();
        assertThat(result.flags()).hasSize(1);
    }

    @Test
    void upserted_flag_adds_new_key() {
        Delta delta = new Delta(List.of(flag("search.reranker", true)), Set.of(), List.of(), Set.of());

        EnvironmentConfig result = DeltaApplier.apply(base, delta);

        assertThat(result.flags()).containsOnlyKeys("checkout.new-flow", "search.reranker");
    }

    @Test
    void removed_flag_key_drops_flag() {
        Delta delta = new Delta(List.of(), Set.of("checkout.new-flow"), List.of(), Set.of());

        EnvironmentConfig result = DeltaApplier.apply(base, delta);

        assertThat(result.flags()).isEmpty();
        assertThat(result.segments()).containsKey("beta-testers");
    }

    @Test
    void removing_unknown_key_is_a_silent_noop() {
        Delta delta = new Delta(List.of(), Set.of("ghost.flag"), List.of(), Set.of("ghost-segment"));

        EnvironmentConfig result = DeltaApplier.apply(base, delta);

        assertThat(result.flags()).containsKey("checkout.new-flow");
        assertThat(result.segments()).containsKey("beta-testers");
    }

    @Test
    void segments_are_upserted_and_removed_symmetrically_to_flags() {
        Delta delta = new Delta(
            List.of(), Set.of(),
            List.of(segment("beta-testers", "trial"), segment("employees", "internal")),
            Set.of());

        EnvironmentConfig result = DeltaApplier.apply(base, delta);

        assertThat(result.segments()).containsOnlyKeys("beta-testers", "employees");
        assertThat(result.segments().get("beta-testers").clauses().getFirst().values())
            .containsExactly("trial");
    }

    @Test
    void original_config_is_not_mutated() {
        Delta delta = new Delta(
            List.of(flag("checkout.new-flow", false)), Set.of(),
            List.of(), Set.of("beta-testers"));

        DeltaApplier.apply(base, delta);

        // atomic swap працює лише якщо старий конфіг лишився цілим:
        // читачі можуть тримати посилання на нього під час підміни
        assertThat(base.flags().get("checkout.new-flow").enabled()).isTrue();
        assertThat(base.segments()).containsKey("beta-testers");
    }

    @Test
    void upsert_wins_over_removal_of_the_same_key_within_one_delta() {
        Delta delta = new Delta(
            List.of(flag("checkout.new-flow", false)), Set.of("checkout.new-flow"),
            List.of(), Set.of());

        EnvironmentConfig result = DeltaApplier.apply(base, delta);

        assertThat(result.flags().get("checkout.new-flow"))
            .as("remove+upsert одного ключа = заміна, не видалення")
            .isNotNull();
        assertThat(result.flags().get("checkout.new-flow").enabled()).isFalse();
    }

    @Test
    void empty_delta_returns_config_equal_to_current() {
        EnvironmentConfig result = DeltaApplier.apply(base, emptyDelta());

        assertThat(result).isEqualTo(base);
    }
}
