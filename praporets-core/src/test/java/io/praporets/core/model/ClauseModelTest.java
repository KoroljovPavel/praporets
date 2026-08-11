package io.praporets.core.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Валідація моделі таргетингу: NPE для null, IAE для неправильних значень,
 * захисні копії — той самий контракт, що в Rollout/Bucket (01b).
 */
class ClauseModelTest {

    // --- Clause ---

    @Test
    void clause_rejects_blank_attribute() {
        assertThatThrownBy(() -> new Clause(" ", Operator.IN, List.of("UA"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clause_rejects_null_operator() {
        assertThatThrownBy(() -> new Clause("country", null, List.of("UA"), false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void clause_rejects_null_values() {
        assertThatThrownBy(() -> new Clause("country", Operator.IN, null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void clause_allows_empty_values() {
        // порожній values легальний — просто нікого не матчить
        assertThat(new Clause("country", Operator.IN, List.of(), false).values()).isEmpty();
    }

    @Test
    void clause_values_are_defensively_copied() {
        var source = new ArrayList<>(List.of("UA"));
        var clause = new Clause("country", Operator.IN, source, false);

        source.add("PL");

        assertThat(clause.values()).containsExactly("UA");
    }

    // --- Segment ---

    @Test
    void segment_rejects_blank_key() {
        assertThatThrownBy(() -> new Segment(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void segment_allows_empty_clauses() {
        // сегмент без умов = «усі користувачі»
        assertThat(new Segment("everyone", List.of()).clauses()).isEmpty();
    }

    @Test
    void segment_clauses_are_immutable() {
        var segment = new Segment("beta", List.of());

        assertThatThrownBy(() -> segment.clauses()
                .add(new Clause("country", Operator.IN, List.of("UA"), false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- EvaluationContext ---

    @Test
    void context_rejects_blank_user_key() {
        assertThatThrownBy(() -> new EvaluationContext(" ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void context_rejects_null_attributes() {
        assertThatThrownBy(() -> new EvaluationContext("user-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void context_attributes_are_immutable() {
        var context = new EvaluationContext("user-1", Map.of("country", "UA"));

        assertThatThrownBy(() -> context.attributes().put("plan", "pro"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void attribute_lookup_returns_present_value() {
        var context = new EvaluationContext("user-1", Map.of("country", "UA"));

        assertThat(context.attribute("country")).contains("UA");
    }

    @Test
    void attribute_lookup_returns_empty_for_missing() {
        var context = new EvaluationContext("user-1", Map.of());

        assertThat(context.attribute("country")).isEmpty();
    }

    @Test
    void userKey_pseudo_attribute_resolves_to_user_key() {
        var context = new EvaluationContext("user-42", Map.of());

        assertThat(context.attribute("userKey")).contains("user-42");
    }

    @Test
    void userKey_pseudo_attribute_wins_over_attributes_map() {
        // ідентичність користувача не можна перекрити атрибутом
        var context = new EvaluationContext("user-42", Map.of("userKey", "spoofed"));

        assertThat(context.attribute("userKey")).contains("user-42");
    }
}
