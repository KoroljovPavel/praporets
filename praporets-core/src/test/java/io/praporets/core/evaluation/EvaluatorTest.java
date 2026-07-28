package io.praporets.core.evaluation;

import io.praporets.core.model.Bucket;
import io.praporets.core.model.Clause;
import io.praporets.core.model.EnvironmentConfig;
import io.praporets.core.model.EvaluationContext;
import io.praporets.core.model.FlagDefinition;
import io.praporets.core.model.Operator;
import io.praporets.core.model.Rollout;
import io.praporets.core.model.Rule;
import io.praporets.core.model.Segment;
import io.praporets.core.model.Variant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Алгоритм зі спеки 7.1 + рішення D1–D8. Кожен тест пінить один крок
 * алгоритму або один пріоритет між кроками.
 */
class EvaluatorTest {

    private static final List<Variant> ON_OFF = List.of(
            new Variant("on", "true"), new Variant("off", "false"));

    private static final Rollout AB_ROLLOUT = new Rollout("test-salt", List.of(
            new Bucket("on", 50_000), new Bucket("off", 50_000)));

    private static EvaluationContext user(String userKey, String... kv) {
        var attrs = new java.util.HashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) {
            attrs.put(kv[i], kv[i + 1]);
        }
        return new EvaluationContext(userKey, attrs);
    }

    private static Clause countryIs(String country) {
        return new Clause("country", Operator.IN, List.of(country), false);
    }

    private static EnvironmentConfig envWith(FlagDefinition... flags) {
        var map = new java.util.HashMap<String, FlagDefinition>();
        for (FlagDefinition f : flags) {
            map.put(f.key(), f);
        }
        return new EnvironmentConfig(map, Map.of());
    }

    // --- крок 1: FLAG_NOT_FOUND ---

    @Test
    void unknown_flag_yields_flag_not_found_without_variant() {
        var result = Evaluator.evaluate(envWith(), "ghost.flag", user("user-1"));

        assertThat(result).isEqualTo(new EvaluationResult(
                "ghost.flag", Reason.FLAG_NOT_FOUND, null, null, null));
    }

    // --- крок 2: FLAG_DISABLED ---

    @Test
    void disabled_flag_serves_off_variant_and_skips_rules() {
        // D5: правило збіглося б (userKey IN user-1), але enabled=false його навіть не перевіряє
        var flag = new FlagDefinition("f", false, "on", "off", ON_OFF,
                List.of(new Rule("r1",
                        List.of(new Clause("userKey", Operator.IN, List.of("user-1"), false)),
                        "on", null)),
                null);

        var result = Evaluator.evaluate(envWith(flag), "f", user("user-1"));

        assertThat(result).isEqualTo(new EvaluationResult(
                "f", Reason.FLAG_DISABLED, "off", "false", null));
    }

    // --- крок 3: правила ---

    @Test
    void matching_rule_with_fixed_variant_yields_rule_match_with_rule_id() {
        var flag = new FlagDefinition("f", true, "off", "off", ON_OFF,
                List.of(new Rule("r-ua", List.of(countryIs("UA")), "on", null)),
                null);

        var result = Evaluator.evaluate(envWith(flag), "f", user("user-1", "country", "UA"));

        assertThat(result).isEqualTo(new EvaluationResult(
                "f", Reason.RULE_MATCH, "on", "true", "r-ua"));
    }

    @Test
    void first_matching_rule_wins() {
        // D1: обидва правила збігаються — виграє перше за порядком
        var flag = new FlagDefinition("f", true, "off", "off", ON_OFF,
                List.of(
                        new Rule("r-first", List.of(countryIs("UA")), "on", null),
                        new Rule("r-second", List.of(countryIs("UA")), "off", null)),
                null);

        var result = Evaluator.evaluate(envWith(flag), "f", user("user-1", "country", "UA"));

        assertThat(result.ruleId()).isEqualTo("r-first");
    }

    @Test
    void non_matching_rule_is_skipped() {
        var flag = new FlagDefinition("f", true, "off", "off", ON_OFF,
                List.of(
                        new Rule("r-pl", List.of(countryIs("PL")), "off", null),
                        new Rule("r-ua", List.of(countryIs("UA")), "on", null)),
                null);

        var result = Evaluator.evaluate(envWith(flag), "f", user("user-1", "country", "UA"));

        assertThat(result.ruleId()).isEqualTo("r-ua");
    }

    @Test
    void rule_without_clauses_matches_everyone() {
        // D2
        var flag = new FlagDefinition("f", true, "off", "off", ON_OFF,
                List.of(new Rule("r-all", List.of(), "on", null)),
                null);

        var result = Evaluator.evaluate(envWith(flag), "f", user("anyone"));

        assertThat(result.reason()).isEqualTo(Reason.RULE_MATCH);
        assertThat(result.ruleId()).isEqualTo("r-all");
    }

    @Test
    void rule_rollout_yields_rollout_reason_with_rule_id_and_bucketer_consistent_variant() {
        var flag = new FlagDefinition("f", true, "off", "off", ON_OFF,
                List.of(new Rule("r-roll", List.of(), null, AB_ROLLOUT)),
                null);

        var result = Evaluator.evaluate(envWith(flag), "f", user("user-42"));

        // бакетування має йти через Bucketer із flagKey (не rule.id)
        String expected = Bucketer.variantKeyFor(AB_ROLLOUT, "f", "user-42");
        assertThat(result.reason()).isEqualTo(Reason.ROLLOUT);
        assertThat(result.ruleId()).isEqualTo("r-roll");
        assertThat(result.variantKey()).isEqualTo(expected);
    }

    @Test
    void rule_with_in_segment_clause_uses_config_segments() {
        var segments = Map.of("beta", new Segment("beta", List.of(countryIs("UA"))));
        var flag = new FlagDefinition("f", true, "off", "off", ON_OFF,
                List.of(new Rule("r-beta",
                        List.of(new Clause("segment", Operator.IN_SEGMENT, List.of("beta"), false)),
                        "on", null)),
                null);
        var config = new EnvironmentConfig(Map.of("f", flag), segments);

        var inSegment = Evaluator.evaluate(config, "f", user("u1", "country", "UA"));
        var outOfSegment = Evaluator.evaluate(config, "f", user("u2", "country", "PL"));

        assertThat(inSegment.reason()).isEqualTo(Reason.RULE_MATCH);
        assertThat(outOfSegment.reason()).isEqualTo(Reason.DEFAULT);
    }

    // --- крок 4: rollout конфігурації ---

    @Test
    void config_rollout_applies_when_no_rule_matches() {
        var flag = new FlagDefinition("f", true, "off", "off", ON_OFF,
                List.of(new Rule("r-pl", List.of(countryIs("PL")), "on", null)),
                AB_ROLLOUT);

        var result = Evaluator.evaluate(envWith(flag), "f", user("user-42", "country", "UA"));

        String expected = Bucketer.variantKeyFor(AB_ROLLOUT, "f", "user-42");
        assertThat(result.reason()).isEqualTo(Reason.ROLLOUT);
        assertThat(result.ruleId()).isNull();
        assertThat(result.variantKey()).isEqualTo(expected);
    }

    // --- крок 5: DEFAULT ---

    @Test
    void default_variant_when_no_rules_and_no_rollout() {
        var flag = new FlagDefinition("f", true, "on", "off", ON_OFF, List.of(), null);

        var result = Evaluator.evaluate(envWith(flag), "f", user("user-1"));

        assertThat(result).isEqualTo(new EvaluationResult(
                "f", Reason.DEFAULT, "on", "true", null));
    }

    // --- D6: розсинхрон variants ---

    @Test
    void missing_variant_definition_yields_null_json_value_without_throwing() {
        var flag = new FlagDefinition("f", true, "ghost", "off", ON_OFF, List.of(), null);

        var result = Evaluator.evaluate(envWith(flag), "f", user("user-1"));

        assertThat(result.variantKey()).isEqualTo("ghost");
        assertThat(result.jsonValue()).isNull();
    }

    // --- evaluateAll ---

    @Test
    void evaluate_all_returns_result_per_flag_sorted_by_key() {
        // D8: порядок за ключем, не порядок Map
        var config = envWith(
                new FlagDefinition("zulu", true, "on", "off", ON_OFF, List.of(), null),
                new FlagDefinition("alpha", true, "on", "off", ON_OFF, List.of(), null),
                new FlagDefinition("mike", false, "on", "off", ON_OFF, List.of(), null));

        var results = Evaluator.evaluateAll(config, user("user-1"));

        assertThat(results).extracting(EvaluationResult::flagKey)
                .containsExactly("alpha", "mike", "zulu");
        assertThat(results).extracting(EvaluationResult::reason)
                .containsExactly(Reason.DEFAULT, Reason.FLAG_DISABLED, Reason.DEFAULT);
    }

    @Test
    void evaluate_all_on_empty_config_returns_empty_list() {
        assertThat(Evaluator.evaluateAll(envWith(), user("user-1"))).isEmpty();
    }

    // --- null-контракт ---

    @Test
    void null_arguments_yield_npe() {
        var config = envWith();
        var context = user("user-1");

        assertThatThrownBy(() -> Evaluator.evaluate(null, "f", context))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Evaluator.evaluate(config, null, context))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Evaluator.evaluate(config, "f", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Evaluator.evaluateAll(null, context))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Evaluator.evaluateAll(config, null))
                .isInstanceOf(NullPointerException.class);
    }
}
