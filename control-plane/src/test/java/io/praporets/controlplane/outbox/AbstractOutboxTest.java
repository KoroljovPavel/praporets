package io.praporets.controlplane.outbox;

import io.praporets.controlplane.TestKafka;
import io.praporets.controlplane.TestPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * База outbox-тестів. НЕ {@link io.praporets.controlplane.AbstractIntegrationTest}
 * і НЕ {@code @Transactional} — свідомо:
 * <ul>
 *   <li>відкат тест-транзакції означає, що BEFORE_COMMIT-слухач ніколи не
 *       спрацює — писати в outbox було б нікому;</li>
 *   <li>relay і Kafka живуть поза тест-транзакцією.</li>
 * </ul>
 * Замість відкату — ізоляція даними: кожен тест сідить середовище з
 * УНІКАЛЬНИМ суфіксом і асертить тільки свої рядки/повідомлення.
 *
 * <p>Relay-планувальник вимкнений properties — тіки смикаються руками
 * ({@code relay.relayBatch()}), інакше фонові тіки зробили б асерти
 * расовими.
 */
// fanout теж вимкнений: relay-тести читають топік ВЛАСНИМИ консюмерами,
// а фоновий слухач контексту тільки додавав би недетермінізму
@SpringBootTest(properties = {
    "praporets.outbox.relay.enabled=false",
    "praporets.fanout.enabled=false"
})
@AutoConfigureMockMvc
public abstract class AbstractOutboxTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @ServiceConnection
    static final KafkaContainer KAFKA = TestKafka.INSTANCE;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Ціна реальних комітів: наші дані ПЕРЕЖИВАЮТЬ тест у спільному
     * singleton-Postgres, а сусідні тести (напр. {@code JsonbMappingTest})
     * припускають порожні таблиці. Прибираємо за собою після кожного тесту;
     * Kafka-топік не чистимо — читачі фільтрують за унікальним env-ключем.
     */
    @org.junit.jupiter.api.AfterEach
    void cleanUpCommittedSeeds() {
        try {
            jdbc.update("DELETE FROM outbox");
        } catch (org.springframework.dao.DataAccessException e) {
            // таблиці outbox ще немає — тихо пропускаємо
        }
        jdbc.update("DELETE FROM flag_config WHERE environment_id IN (SELECT id FROM environment WHERE key LIKE 'out-%')");
        jdbc.update("DELETE FROM revision_log WHERE environment_id IN (SELECT id FROM environment WHERE key LIKE 'out-%')");
        jdbc.update("DELETE FROM segment WHERE environment_id IN (SELECT id FROM environment WHERE key LIKE 'out-%')");
        jdbc.update("DELETE FROM flag WHERE key LIKE 'flag.out-%'"); // variant — каскадом
        jdbc.update("DELETE FROM environment WHERE key LIKE 'out-%'");
    }

    /**
     * Сідить env {@code out-<suffix>} із флагом {@code flag.out-<suffix>}
     * (variants on/off) і його конфігурацією (enabled, без правил) — upsert
     * конфігурації і є ПЕРШОЮ зміною (ревізія 1, один запис в outbox).
     */
    protected String seedEnvironmentWithConfiguredFlag(String suffix) throws Exception {
        String env = "out-" + suffix;
        String flag = flagKeyFor(env);
        mvc.perform(post("/api/v1/environments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\": \"%s\", \"name\": \"%s\"}".formatted(env, env)))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"key": "%s", "name": "outbox test flag", "valueType": "BOOLEAN",
                     "variants": [{"key": "on", "value": true}, {"key": "off", "value": false}]}
                    """.formatted(flag)))
            .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/environments/%s/flags/%s/config".formatted(env, flag))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"enabled": true, "defaultVariant": "on", "offVariant": "off",
                     "rules": null, "rollout": null}
                    """))
            .andExpect(status().isCreated());
        return env;
    }

    protected String flagKeyFor(String envKey) {
        return "flag." + envKey;
    }

    /**
     * Наступна зміна конфігурації середовища (ревізія +1, ще один запис в outbox).
     */
    protected void toggle(String env, boolean enabled) throws Exception {
        mvc.perform(post("/api/v1/environments/%s/flags/%s/toggle".formatted(env, flagKeyFor(env)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": " + enabled + "}"))
            .andExpect(status().isOk());
    }

    protected long outboxCount(String env) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM outbox WHERE partition_key = ?", Long.class, env);
        return n == null ? 0 : n;
    }

    protected long unpublishedCount(String env) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM outbox WHERE partition_key = ? AND published_at IS NULL",
            Long.class, env);
        return n == null ? 0 : n;
    }
}
