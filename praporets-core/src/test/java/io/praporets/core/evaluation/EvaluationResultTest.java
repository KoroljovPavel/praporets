package io.praporets.core.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Матриця узгодженості reason ↔ поля: неможливі стани мають бути непредставними.
 */
class EvaluationResultTest {

    @Test
    void flag_not_found_carries_no_variant_and_no_rule() {
        var result = new EvaluationResult("f", Reason.FLAG_NOT_FOUND, null, null, null);

        assertThat(result.variantKey()).isNull();
        assertThat(result.jsonValue()).isNull();
    }

    @Test
    void flag_not_found_rejects_variant_key() {
        assertThatThrownBy(() -> new EvaluationResult("f", Reason.FLAG_NOT_FOUND, "on", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flag_not_found_rejects_rule_id() {
        assertThatThrownBy(() -> new EvaluationResult("f", Reason.FLAG_NOT_FOUND, null, null, "r1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rule_match_requires_rule_id() {
        assertThatThrownBy(() -> new EvaluationResult("f", Reason.RULE_MATCH, "on", "true", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rule_match_requires_variant_key() {
        assertThatThrownBy(() -> new EvaluationResult("f", Reason.RULE_MATCH, null, null, "r1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void default_rejects_rule_id() {
        assertThatThrownBy(() -> new EvaluationResult("f", Reason.DEFAULT, "on", "true", "r1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabled_rejects_rule_id() {
        assertThatThrownBy(() -> new EvaluationResult("f", Reason.FLAG_DISABLED, "off", "false", "r1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rollout_allows_both_with_and_without_rule_id() {
        // з правила — ruleId є; з конфігурації флага — немає
        var fromRule = new EvaluationResult("f", Reason.ROLLOUT, "a", "1", "r1");
        var fromConfig = new EvaluationResult("f", Reason.ROLLOUT, "a", "1", null);

        assertThat(fromRule.ruleId()).isEqualTo("r1");
        assertThat(fromConfig.ruleId()).isNull();
    }

    @Test
    void json_value_may_be_null_when_variant_definition_is_missing() {
        // D6: variantKey відомий, але значення в variants не знайдено
        var result = new EvaluationResult("f", Reason.DEFAULT, "ghost", null, null);

        assertThat(result.jsonValue()).isNull();
    }

    @Test
    void rejects_blank_flag_key() {
        assertThatThrownBy(() -> new EvaluationResult(" ", Reason.DEFAULT, "on", "true", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_reason() {
        assertThatThrownBy(() -> new EvaluationResult("f", null, "on", "true", null))
                .isInstanceOf(NullPointerException.class);
    }
}
