package io.praporets.analytics.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.praporets.analytics.TestKafka;
import io.praporets.analytics.TestPostgres;
import io.praporets.analytics.common.KafkaTopics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * unique_users рахується точно і ідемпотентно (на вікно, не глобально),
 * лічильник {@code praporets_evaluations_total} росте тільки для нових
 * подій. Той самий контекст та ізоляція даними, що в
 * {@link AnalyticsEvaluationsConsumerTest}.
 */
@SpringBootTest
class UniqueUsersAggregationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @ServiceConnection
    static final KafkaContainer KAFKA = TestKafka.INSTANCE;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry meterRegistry;

    // ------------------------------------------------------------------ tests

    @Test
    void same_user_twice_in_window_counts_once() throws Exception {
        String env = uniqueEnv();

        produce(env, event(env, "2026-08-01T10:15:10Z", "on", "user-a"));
        produce(env, event(env, "2026-08-01T10:15:40Z", "on", "user-a"));

        awaitTrue("обидві події в агрегаті", () -> evalCount(env, "on") == 2);
        assertThat(uniqueUsers(env, "on"))
            .as("той самий користувач у вікні — один унікальний").isEqualTo(1);
    }

    @Test
    void distinct_users_in_window_are_both_counted() throws Exception {
        String env = uniqueEnv();

        produce(env, event(env, "2026-08-01T10:15:10Z", "on", "user-a"));
        produce(env, event(env, "2026-08-01T10:15:40Z", "on", "user-b"));

        awaitTrue("обидві події в агрегаті", () -> evalCount(env, "on") == 2);
        assertThat(uniqueUsers(env, "on")).isEqualTo(2);
    }

    @Test
    void same_user_in_two_windows_counts_in_each() throws Exception {
        String env = uniqueEnv();

        produce(env, event(env, "2026-08-01T10:15:59Z", "on", "user-a"));
        produce(env, event(env, "2026-08-01T10:16:00Z", "on", "user-a"));

        awaitTrue("два вікна", () -> windowCount(env) == 2);
        Long perWindowMax = jdbc.queryForObject(
            "SELECT max(unique_users) FROM evaluation_agg WHERE environment = ?", Long.class, env);
        Long perWindowMin = jdbc.queryForObject(
            "SELECT min(unique_users) FROM evaluation_agg WHERE environment = ?", Long.class, env);
        assertThat(perWindowMin).as("unique рахується НА вікно").isEqualTo(1);
        assertThat(perWindowMax).isEqualTo(1);
    }

    @Test
    void evaluations_total_counter_grows_only_for_new_events() throws Exception {
        String env = uniqueEnv();
        UUID evaluationId = UUID.randomUUID();
        String duplicated = payload(evaluationId, env, "2026-08-01T10:15:10Z", "on", "user-a");

        produce(env, duplicated);
        produce(env, duplicated);  // дублікат — лічильник мовчить
        produce(env, event(env, "2026-08-01T10:15:20Z", "barrier", "user-b"));

        awaitTrue("бар'єр оброблено", () -> evalCount(env, "barrier") == 1);
        assertThat(counterValue(env, "on"))
            .as("praporets_evaluations_total: дублікат не рахується").isEqualTo(1.0);
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEnv() {
        return "uu-" + UUID.randomUUID();
    }

    private static String flagOf(String env) {
        return "flag." + env;
    }

    private static String event(String env, String occurredAt, String variant, String userKeyHash) {
        return payload(UUID.randomUUID(), env, occurredAt, variant, userKeyHash);
    }

    private static String payload(UUID evaluationId, String env, String occurredAt, String variant, String userKeyHash) {
        return """
            {"evaluationId": "%s", "occurredAt": "%s", "environment": "%s",
             "flagKey": "%s", "variantKey": "%s", "reason": "RULE_MATCH", "ruleId": "r1",
             "revision": 7, "userKeyHash": "%s", "edgeInstanceId": "flag-edge-test"}
            """.formatted(evaluationId, occurredAt, env, flagOf(env), variant, userKeyHash);
    }

    private void produce(String env, String value) throws Exception {
        ProducerRecord<String, String> record =
            new ProducerRecord<>(KafkaTopics.FLAG_EVALUATIONS, flagOf(env), value);
        record.headers().add("schema-version", "1".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    }

    private long evalCount(String env, String variant) {
        Long n = jdbc.queryForObject(
            "SELECT coalesce(sum(eval_count), 0) FROM evaluation_agg "
                + "WHERE environment = ? AND variant_key = ?", Long.class, env, variant);
        return n == null ? 0 : n;
    }

    private long uniqueUsers(String env, String variant) {
        Long n = jdbc.queryForObject(
            "SELECT coalesce(sum(unique_users), 0) FROM evaluation_agg "
                + "WHERE environment = ? AND variant_key = ?", Long.class, env, variant);
        return n == null ? 0 : n;
    }

    private long windowCount(String env) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM evaluation_agg WHERE environment = ?", Long.class, env);
        return n == null ? 0 : n;
    }

    private double counterValue(String env, String variant) {
        Counter counter = meterRegistry.find("praporets_evaluations_total")
            .tags("environment", env, "flag", flagOf(env), "variant", variant, "reason", "RULE_MATCH")
            .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static void awaitTrue(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }
}
