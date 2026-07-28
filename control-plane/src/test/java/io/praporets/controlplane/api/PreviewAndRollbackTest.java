package io.praporets.controlplane.api;

import io.praporets.controlplane.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 01h: dry-run обчислення через praporets-core (CP-11) і rollback ревізії
 * (CP-06). Тільки HTTP — внутрішню структуру крок не пінить, дизайн твій.
 */
class PreviewAndRollbackTest extends AbstractIntegrationTest {

    private static final String FLAG_JSON = """
            {
              "key": "checkout.new-flow",
              "name": "New checkout",
              "valueType": "BOOLEAN",
              "variants": [{"key": "on", "value": true}, {"key": "off", "value": false}]
            }
            """;

    private static final String CONFIG_WITH_RULE_JSON = """
            {
              "enabled": true,
              "defaultVariant": "off",
              "offVariant": "off",
              "rules": [{
                "id": "r1",
                "clauses": [{"attribute": "country", "operator": "IN", "values": ["UA"], "negate": false}],
                "variantKey": "on",
                "rollout": null
              }],
              "rollout": null
            }
            """;

    private void createEnvironmentAndFlag() throws Exception {
        mvc.perform(post("/api/v1/environments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "dev", "name": "Development"}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/flags").contentType(MediaType.APPLICATION_JSON).content(FLAG_JSON))
                .andExpect(status().isCreated());
    }

    private void putConfig(String json) throws Exception {
        mvc.perform(put("/api/v1/environments/dev/flags/checkout.new-flow/config")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private String preview(String flagKey, String userKey, String attributesJson) {
        return """
                {"environment": "dev", "flagKey": "%s",
                 "context": {"userKey": "%s", "attributes": %s}}
                """.formatted(flagKey, userKey, attributesJson);
    }

    // ---------- preview ----------

    @Test
    void preview_returns_rule_match_with_value_and_revision() throws Exception {
        createEnvironmentAndFlag();
        putConfig(CONFIG_WITH_RULE_JSON);

        mvc.perform(post("/api/v1/evaluate-preview").contentType(MediaType.APPLICATION_JSON)
                        .content(preview("checkout.new-flow", "u-1", "{\"country\": \"UA\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value("checkout.new-flow"))
                .andExpect(jsonPath("$.variantKey").value("on"))
                .andExpect(jsonPath("$.value").value(true))
                .andExpect(jsonPath("$.reason").value("RULE_MATCH"))
                .andExpect(jsonPath("$.ruleId").value("r1"))
                .andExpect(jsonPath("$.revision").value(1));

        // H3: dry-run — журнал не виріс
        mvc.perform(get("/api/v1/environments/dev/revisions"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void preview_no_rule_match_falls_back_to_default() throws Exception {
        createEnvironmentAndFlag();
        putConfig(CONFIG_WITH_RULE_JSON);

        mvc.perform(post("/api/v1/evaluate-preview").contentType(MediaType.APPLICATION_JSON)
                        .content(preview("checkout.new-flow", "u-1", "{\"country\": \"PL\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantKey").value("off"))
                .andExpect(jsonPath("$.value").value(false))
                .andExpect(jsonPath("$.reason").value("DEFAULT"));
    }

    @Test
    void preview_disabled_flag_returns_off_variant() throws Exception {
        createEnvironmentAndFlag();
        putConfig("""
                {"enabled": false, "defaultVariant": "on", "offVariant": "off", "rules": [], "rollout": null}
                """);

        mvc.perform(post("/api/v1/evaluate-preview").contentType(MediaType.APPLICATION_JSON)
                        .content(preview("checkout.new-flow", "u-1", "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantKey").value("off"))
                .andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
    }

    @Test
    void preview_rollout_with_single_full_bucket_is_deterministic() throws Exception {
        createEnvironmentAndFlag();
        putConfig("""
                {"enabled": true, "defaultVariant": "off", "offVariant": "off", "rules": [],
                 "rollout": {"salt": "v1", "buckets": [{"variantKey": "on", "weight": 100000}]}}
                """);

        mvc.perform(post("/api/v1/evaluate-preview").contentType(MediaType.APPLICATION_JSON)
                        .content(preview("checkout.new-flow", "any-user", "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantKey").value("on"))
                .andExpect(jsonPath("$.reason").value("ROLLOUT"));
    }

    @Test
    void preview_rule_can_target_segment() throws Exception {
        createEnvironmentAndFlag();
        mvc.perform(put("/api/v1/environments/dev/segments/beta-testers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conditions": [{"attribute": "plan", "operator": "IN", "values": ["pro"], "negate": false}]}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/environments/dev/flags/checkout.new-flow/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": true, "defaultVariant": "off", "offVariant": "off",
                                 "rules": [{"id": "r-seg",
                                            "clauses": [{"attribute": "user", "operator": "IN_SEGMENT",
                                                         "values": ["beta-testers"], "negate": false}],
                                            "variantKey": "on", "rollout": null}],
                                 "rollout": null}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/evaluate-preview").contentType(MediaType.APPLICATION_JSON)
                        .content(preview("checkout.new-flow", "u-1", "{\"plan\": \"pro\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantKey").value("on"))
                .andExpect(jsonPath("$.reason").value("RULE_MATCH"))
                .andExpect(jsonPath("$.ruleId").value("r-seg"));
    }

    @Test
    void preview_unknown_flag_is_200_flag_not_found() throws Exception {
        createEnvironmentAndFlag();

        mvc.perform(post("/api/v1/evaluate-preview").contentType(MediaType.APPLICATION_JSON)
                        .content(preview("ghost.flag", "u-1", "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"))
                .andExpect(jsonPath("$.variantKey").isEmpty())
                .andExpect(jsonPath("$.value").isEmpty());
    }

    @Test
    void preview_unknown_environment_is_404() throws Exception {
        mvc.perform(post("/api/v1/evaluate-preview").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"environment": "ghost", "flagKey": "x",
                                 "context": {"userKey": "u-1", "attributes": {}}}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- rollback ----------

    @Test
    void rollback_restores_old_config_state_with_new_revision() throws Exception {
        createEnvironmentAndFlag();
        putConfig("""
                {"enabled": true, "defaultVariant": "on", "offVariant": "off", "rules": [], "rollout": null}
                """);                                                       // rev 1
        mvc.perform(put("/api/v1/environments/dev/flags/checkout.new-flow/config")
                        .header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_WITH_RULE_JSON))
                .andExpect(status().isOk());                                 // rev 2
        mvc.perform(post("/api/v1/environments/dev/flags/checkout.new-flow/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isOk());                                 // rev 3

        mvc.perform(post("/api/v1/environments/dev/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toRevision": 1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environmentKey").value("dev"))
                .andExpect(jsonPath("$.rolledBackTo").value(1))
                .andExpect(jsonPath("$.revision").value(4));

        // стан — як на ревізії 1
        mvc.perform(get("/api/v1/environments/dev/flags/checkout.new-flow/config"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.defaultVariant").value("on"))
                .andExpect(jsonPath("$.rules", hasSize(0)));

        // історія НЕ переписана: 4 записи, нові зверху
        mvc.perform(get("/api/v1/environments/dev/revisions"))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].revision").value(4))
                .andExpect(jsonPath("$[1].revision").value(3))
                .andExpect(jsonPath("$[3].revision").value(1));
    }

    @Test
    void rollback_restores_segments_too() throws Exception {
        createEnvironmentAndFlag();
        putConfig(CONFIG_WITH_RULE_JSON);                                    // rev 1
        mvc.perform(put("/api/v1/environments/dev/segments/beta-testers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conditions": [{"attribute": "plan", "operator": "IN", "values": ["pro"], "negate": false}]}
                                """))
                .andExpect(status().isCreated());                            // rev 2
        mvc.perform(put("/api/v1/environments/dev/segments/beta-testers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conditions": [{"attribute": "plan", "operator": "IN", "values": ["enterprise"], "negate": false}]}
                                """))
                .andExpect(status().isOk());                                 // rev 3

        mvc.perform(post("/api/v1/environments/dev/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toRevision": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolledBackTo").value(2))
                .andExpect(jsonPath("$.revision").value(5));   // config + segment відновлені → 2 нові ревізії

        mvc.perform(get("/api/v1/environments/dev/segments"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].conditions[0].values[0]").value("pro"));
    }

    @Test
    void rollback_to_future_revision_is_400() throws Exception {
        createEnvironmentAndFlag();
        putConfig(CONFIG_WITH_RULE_JSON);                                    // rev 1

        mvc.perform(post("/api/v1/environments/dev/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toRevision": 99}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
