package io.praporets.analytics.consumer;

import io.praporets.analytics.TestKafka;
import io.praporets.analytics.TestPostgres;
import io.praporets.analytics.common.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 03d-1: контракт конвеєра analytics — подія з топіка стає похвилинним
 * агрегатом рівно один раз (A-01/A-03/A-04), отрута їде в DLT (A-05/G5/G7).
 *
 * <p>Ізоляція — даними: кожен тест живе у своєму environment
 * ({@code an-<uuid>}), спільна БД/топік іншим тестам не заважають.
 * Group {@code analytics} з earliest — гонки «продюс до assignment» немає
 * (на відміну від 03b): консюмер прочитає й те, що було до підписки.
 *
 * <p>Той самий flagKey усередині тесту = той самий key = одна партиція =
 * гарантований порядок — на цьому стоять сценарії «отрута, потім валідна».
 */
@SpringBootTest
class AnalyticsEvaluationsConsumerTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @ServiceConnection
    static final KafkaContainer KAFKA = TestKafka.INSTANCE;

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JdbcTemplate jdbc;

    // ------------------------------------------------------------------ tests

    @Test
    void event_lands_in_minute_aggregate_and_is_marked_processed() throws Exception {
        String env = uniqueEnv();
        UUID evaluationId = UUID.randomUUID();

        produce(flagOf(env), payload(evaluationId, "2026-08-01T10:15:30.123Z", env, flagOf(env), "on"), "1");

        awaitTrue("агрегат з'явився", () -> countFor(env) == 1);
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT flag_key, variant_key, eval_count, unique_users, window_start "
                + "FROM evaluation_agg WHERE environment = ?", env);
        assertThat(row.get("flag_key")).isEqualTo(flagOf(env));
        assertThat(row.get("variant_key")).isEqualTo("on");
        assertThat(((Number) row.get("eval_count")).longValue()).isEqualTo(1);
        // вікно — початок хвилини EVENT time (G6), секунди обрізані
        assertThat(row.get("window_start").toString()).contains("10:15:00");

        Long processed = jdbc.queryForObject(
            "SELECT count(*) FROM processed_event WHERE evaluation_id = ?", Long.class, evaluationId);
        assertThat(processed).isEqualTo(1);
    }

    @Test
    void duplicate_evaluation_id_does_not_change_aggregate() throws Exception {
        String env = uniqueEnv();
        UUID evaluationId = UUID.randomUUID();
        String duplicated = payload(evaluationId, "2026-08-01T10:15:10Z", env, flagOf(env), "on");

        produce(flagOf(env), duplicated, "1");
        produce(flagOf(env), duplicated, "1");
        // бар'єр: інший variant тієї ж партиції — коли він оброблений,
        // обидва дублікати вже точно пройшли крізь консюмер (порядок!)
        produce(flagOf(env), payload(UUID.randomUUID(), "2026-08-01T10:15:20Z", env, flagOf(env), "barrier"), "1");

        awaitTrue("бар'єр оброблено", () -> evalCount(env, "barrier") == 1);
        assertThat(evalCount(env, "on"))
            .as("повторний evaluationId не інкрементить агрегат (A-04)")
            .isEqualTo(1);
    }

    @Test
    void events_in_same_window_accumulate() throws Exception {
        String env = uniqueEnv();

        produce(flagOf(env), payload(UUID.randomUUID(), "2026-08-01T10:15:01Z", env, flagOf(env), "on"), "1");
        produce(flagOf(env), payload(UUID.randomUUID(), "2026-08-01T10:15:59Z", env, flagOf(env), "on"), "1");

        awaitTrue("обидві події в одному вікні", () -> evalCount(env, "on") == 2);
        Long rows = jdbc.queryForObject(
            "SELECT count(*) FROM evaluation_agg WHERE environment = ?", Long.class, env);
        assertThat(rows).as("вікно одне — рядок один").isEqualTo(1);
    }

    @Test
    void events_in_different_windows_get_separate_rows() throws Exception {
        String env = uniqueEnv();

        produce(flagOf(env), payload(UUID.randomUUID(), "2026-08-01T10:15:59Z", env, flagOf(env), "on"), "1");
        produce(flagOf(env), payload(UUID.randomUUID(), "2026-08-01T10:16:00Z", env, flagOf(env), "on"), "1");

        awaitTrue("два вікна — два рядки", () -> countFor(env) == 2);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT eval_count FROM evaluation_agg WHERE environment = ?", env);
        assertThat(rows).allSatisfy(r ->
            assertThat(((Number) r.get("eval_count")).longValue()).isEqualTo(1));
    }

    @Test
    void flag_not_found_event_is_processed_but_not_aggregated() throws Exception {
        String env = uniqueEnv();
        UUID evaluationId = UUID.randomUUID();

        // variantKey: null — edge шле і такі (E8 з 03c)
        produce(flagOf(env), payloadWithNullVariant(evaluationId, env, flagOf(env)), "1");

        awaitTrue("подію оброблено", () -> processedCount(evaluationId) == 1);
        assertThat(countFor(env)).as("агрегату без варіанта немає").isZero();
    }

    @Test
    void malformed_payload_goes_to_dlt_and_partition_moves_on() throws Exception {
        String env = uniqueEnv();
        String garbage = "це не json {{{ " + env;

        produce(flagOf(env), garbage, "1");
        produce(flagOf(env), payload(UUID.randomUUID(), "2026-08-01T10:15:30Z", env, flagOf(env), "on"), "1");

        // партиція не заблокована: валідна подія ПІСЛЯ отрути доїхала
        awaitTrue("валідна подія після отрути оброблена", () -> evalCount(env, "on") == 1);
        // а отрута — в DLT, як є (оригінальний value)
        ConsumerRecord<String, String> dead = awaitDlt(value -> value.equals(garbage));
        assertThat(dead.key()).isEqualTo(flagOf(env));
    }

    @Test
    void unknown_schema_version_goes_to_dlt() throws Exception {
        String env = uniqueEnv();
        UUID evaluationId = UUID.randomUUID();
        String futureEvent = payload(evaluationId, "2026-08-01T10:15:30Z", env, flagOf(env), "on");

        produce(flagOf(env), futureEvent, "99");
        produce(flagOf(env), payload(UUID.randomUUID(), "2026-08-01T10:16:30Z", env, flagOf(env), "on"), "1");

        awaitTrue("валідна подія оброблена", () -> evalCount(env, "on") == 1);
        // G7: на відміну від fan-out (03b) невідома версія НЕ скіпається —
        // це дані, вони чекають у DLT на переграванння після апгрейду
        awaitDlt(value -> value.equals(futureEvent));
        assertThat(processedCount(evaluationId)).as("v99 не оброблялась").isZero();
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEnv() {
        return "an-" + UUID.randomUUID();
    }

    private static String flagOf(String env) {
        return "flag." + env;
    }

    /**
     * K-payload дослівно у форматі edge (03c, спека §6.4).
     */
    private static String payload(UUID evaluationId, String occurredAt, String env, String flagKey, String variant) {
        return """
            {"evaluationId": "%s", "occurredAt": "%s", "environment": "%s",
             "flagKey": "%s", "variantKey": "%s", "reason": "RULE_MATCH", "ruleId": "r1",
             "revision": 7, "userKeyHash": "abc123", "edgeInstanceId": "flag-edge-test"}
            """.formatted(evaluationId, occurredAt, env, flagKey, variant);
    }

    private static String payloadWithNullVariant(UUID evaluationId, String env, String flagKey) {
        return """
            {"evaluationId": "%s", "occurredAt": "2026-08-01T10:15:30Z", "environment": "%s",
             "flagKey": "%s", "variantKey": null, "reason": "FLAG_NOT_FOUND", "ruleId": null,
             "revision": 7, "userKeyHash": "abc123", "edgeInstanceId": "flag-edge-test"}
            """.formatted(evaluationId, env, flagKey);
    }

    private void produce(String key, String value, String schemaVersion) throws Exception {
        ProducerRecord<String, String> record =
            new ProducerRecord<>(KafkaTopics.FLAG_EVALUATIONS, key, value);
        record.headers().add("schema-version", schemaVersion.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    }

    private long countFor(String env) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM evaluation_agg WHERE environment = ?", Long.class, env);
        return n == null ? 0 : n;
    }

    private long evalCount(String env, String variant) {
        Long n = jdbc.queryForObject(
            "SELECT coalesce(sum(eval_count), 0) FROM evaluation_agg "
                + "WHERE environment = ? AND variant_key = ?", Long.class, env, variant);
        return n == null ? 0 : n;
    }

    private long processedCount(UUID evaluationId) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM processed_event WHERE evaluation_id = ?", Long.class, evaluationId);
        return n == null ? 0 : n;
    }

    private static void awaitTrue(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    /**
     * Читає DLT з початку власним консюмером до першого збігу (15с).
     */
    private ConsumerRecord<String, String> awaitDlt(Predicate<String> valueFilter) {
        Properties props = new Properties();
        props.putAll(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaTopics.FLAG_EVALUATIONS_DLT));
            long deadline = System.currentTimeMillis() + WAIT.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (valueFilter.test(record.value())) {
                        return record;
                    }
                }
            }
        }
        return fail("повідомлення не з'явилось у DLT за 15с");
    }
}
