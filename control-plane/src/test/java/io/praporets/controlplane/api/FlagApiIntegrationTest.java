package io.praporets.controlplane.api;

import io.praporets.controlplane.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Повний стек REST → сервіс → Postgres: життєвий цикл флага з If-Match (G4),
 * archive замість delete, конфігурація з ревізіями через API, аудит із X-Actor.
 */
class FlagApiIntegrationTest extends AbstractIntegrationTest {

    private static final String FLAG_JSON = """
            {
              "key": "checkout.new-flow",
              "name": "New checkout",
              "valueType": "BOOLEAN",
              "variants": [{"key": "on", "value": true}, {"key": "off", "value": false}]
            }
            """;

    private static final String CONFIG_JSON = """
            {
              "enabled": true,
              "defaultVariant": "on",
              "offVariant": "off",
              "rules": [],
              "rollout": null
            }
            """;

    private static final String CONFIG_WITH_RULES_JSON = """
            {
              "enabled": true,
              "defaultVariant": "on",
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

    private void createEnvironment() throws Exception {
        mvc.perform(post("/api/v1/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "dev", "name": "Development"}
                                """))
                .andExpect(status().isCreated());
    }

    private void createFlag() throws Exception {
        mvc.perform(post("/api/v1/flags").contentType(MediaType.APPLICATION_JSON).content(FLAG_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void flag_lifecycle_with_if_match_and_archive() throws Exception {
        createFlag();

        mvc.perform(get("/api/v1/flags/checkout.new-flow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.variants", hasSize(2)));

        mvc.perform(patch("/api/v1/flags/checkout.new-flow")
                        .header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New checkout v2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New checkout v2"))
                .andExpect(jsonPath("$.version").value(1));

        // той самий If-Match удруге — версія вже 1, редагування застаріле
        mvc.perform(patch("/api/v1/flags/checkout.new-flow")
                        .header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "lost update"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(delete("/api/v1/flags/checkout.new-flow"))
                .andExpect(status().isNoContent());

        // archive, не hard delete: флаг живий, історія збережена
        mvc.perform(get("/api/v1/flags/checkout.new-flow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void config_upsert_toggle_and_revision_journal() throws Exception {
        createEnvironment();
        createFlag();

        // створення — без If-Match → 201
        mvc.perform(put("/api/v1/environments/dev/flags/checkout.new-flow/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0));

        // оновлення без If-Match → 400
        mvc.perform(put("/api/v1/environments/dev/flags/checkout.new-flow/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_WITH_RULES_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // оновлення з коректним If-Match → 200, правила збережені
        mvc.perform(put("/api/v1/environments/dev/flags/checkout.new-flow/config")
                        .header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_WITH_RULES_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].variantKey").value("on"));

        // stale If-Match → 409
        mvc.perform(put("/api/v1/environments/dev/flags/checkout.new-flow/config")
                        .header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isConflict());

        // kill switch — без If-Match свідомо (G5)
        mvc.perform(post("/api/v1/environments/dev/flags/checkout.new-flow/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // журнал: 2 успішні PUT + toggle = ревізії 3,2,1 (новіші перші)
        mvc.perform(get("/api/v1/environments/dev/revisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].revision").value(3))
                .andExpect(jsonPath("$[0].changeType").value("FLAG_TOGGLED"))
                .andExpect(jsonPath("$[1].changeType").value("FLAG_CONFIG_UPDATED"))
                .andExpect(jsonPath("$[2].revision").value(1))
                .andExpect(jsonPath("$[2].payload.defaultVariant").value("on"));
    }

    @Test
    void rollout_with_broken_weights_is_rejected_by_core_invariants() throws Exception {
        createEnvironment();
        createFlag();

        // 30k + 30k ≠ 100k: канонічний конструктор Rollout кидає IAE ще при
        // десеріалізації — невалідна конфігурація не досягає навіть сервісу (P1/G2)
        mvc.perform(put("/api/v1/environments/dev/flags/checkout.new-flow/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "defaultVariant": "on",
                                  "offVariant": "off",
                                  "rules": [],
                                  "rollout": {
                                    "salt": "v1",
                                    "buckets": [
                                      {"variantKey": "on", "weight": 30000},
                                      {"variantKey": "off", "weight": 30000}
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void segment_upsert_writes_audit_with_actor() throws Exception {
        createEnvironment();

        MvcResult created = mvc.perform(put("/api/v1/environments/dev/segments/beta-testers")
                        .header("X-Actor", "qa-robot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conditions": [{"attribute": "plan", "operator": "IN", "values": ["pro"], "negate": false}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        mvc.perform(put("/api/v1/environments/dev/segments/beta-testers")
                        .header("X-Actor", "qa-robot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conditions": [{"attribute": "plan", "operator": "IN", "values": ["pro", "trial"], "negate": false}]}
                                """))
                .andExpect(status().isOk());

        String segmentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/v1/audit").param("entityId", segmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].actor").value("qa-robot"))
                .andExpect(jsonPath("$[1].action").value("CREATE"))
                .andExpect(jsonPath("$[1].before").isEmpty());
    }
}
