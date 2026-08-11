package io.praporets.analytics.stats;

import io.praporets.analytics.TestKafka;
import io.praporets.analytics.TestPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Read path — {@code GET /api/v1/stats}. Агрегати сідяться SQL-ом
 * повз конвеєр: ендпоінт і в проді читає те, що в таблиці, тож Kafka
 * тут ні до чого. Ізоляція — унікальним environment на тест.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StatsEndpointTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @ServiceConnection
    static final KafkaContainer KAFKA = TestKafka.INSTANCE;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    // ------------------------------------------------------------------ tests

    @Test
    void stats_returns_series_per_variant_with_running_total() throws Exception {
        String env = uniqueEnv();
        seedAgg(env, "on", "2026-08-01T10:15:00Z", 5, 3);
        seedAgg(env, "on", "2026-08-01T10:17:00Z", 2, 1);
        seedAgg(env, "off", "2026-08-01T10:15:00Z", 4, 2);

        StatsResponse response = getStats(env, flagOf(env),
            "2026-08-01T10:00:00Z", "2026-08-01T11:00:00Z");

        assertThat(response.environment()).isEqualTo(env);
        assertThat(response.flagKey()).isEqualTo(flagOf(env));
        assertThat(response.series()).hasSize(2);

        StatsResponse.VariantSeries on = seriesOf(response, "on");
        assertThat(on.points()).extracting(StatsResponse.Point::windowStart)
            .as("точки впорядковані за часом")
            .containsExactly(Instant.parse("2026-08-01T10:15:00Z"), Instant.parse("2026-08-01T10:17:00Z"));
        assertThat(on.points()).extracting(StatsResponse.Point::evalCount).containsExactly(5L, 2L);
        assertThat(on.points()).extracting(StatsResponse.Point::uniqueUsers).containsExactly(3L, 1L);
        assertThat(on.points()).extracting(StatsResponse.Point::cumulativeEvalCount)
            .as("кумулятива — біжуча сума")
            .containsExactly(5L, 7L);

        assertThat(seriesOf(response, "off").points())
            .extracting(StatsResponse.Point::cumulativeEvalCount)
            .containsExactly(4L);
    }

    @Test
    void stats_respects_half_open_time_range() throws Exception {
        String env = uniqueEnv();
        seedAgg(env, "on", "2026-08-01T10:14:00Z", 1, 1);  // до from — відрізане
        seedAgg(env, "on", "2026-08-01T10:15:00Z", 2, 1);  // == from — включене
        seedAgg(env, "on", "2026-08-01T10:16:00Z", 3, 1);  // == to — ЕКСКЛЮЗИВНО

        StatsResponse response = getStats(env, flagOf(env),
            "2026-08-01T10:15:00Z", "2026-08-01T10:16:00Z");

        assertThat(seriesOf(response, "on").points())
            .extracting(StatsResponse.Point::windowStart)
            .containsExactly(Instant.parse("2026-08-01T10:15:00Z"));
    }

    @Test
    void stats_for_unknown_flag_is_empty_series() throws Exception {
        StatsResponse response = getStats(uniqueEnv(), "flag.no-such",
            "2026-08-01T10:00:00Z", "2026-08-01T11:00:00Z");

        assertThat(response.series()).isEmpty();
    }

    @Test
    void missing_required_parameter_is_problem_detail() throws Exception {
        mvc.perform(get("/api/v1/stats")
                .param("environment", "dev")
                .param("flag", "checkout.new-flow")
                .param("from", "2026-08-01T10:00:00Z"))  // без to
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEnv() {
        return "st-" + UUID.randomUUID();
    }

    private static String flagOf(String env) {
        return "flag." + env;
    }

    private void seedAgg(String env, String variant, String windowIso, long evalCount, long uniqueUsers) {
        jdbc.update("""
                INSERT INTO evaluation_agg
                    (environment, flag_key, variant_key, window_start, eval_count, unique_users)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            env, flagOf(env), variant,
            Instant.parse(windowIso).atOffset(ZoneOffset.UTC), evalCount, uniqueUsers);
    }

    private StatsResponse getStats(String env, String flag, String from, String to) throws Exception {
        String body = mvc.perform(get("/api/v1/stats")
                .param("environment", env)
                .param("flag", flag)
                .param("from", from)
                .param("to", to))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return jsonMapper.readValue(body, StatsResponse.class);
    }

    private static StatsResponse.VariantSeries seriesOf(StatsResponse response, String variant) {
        List<StatsResponse.VariantSeries> matching = response.series().stream()
            .filter(s -> s.variantKey().equals(variant))
            .toList();
        assertThat(matching).as("серія варіанта %s", variant).hasSize(1);
        return matching.getFirst();
    }
}
