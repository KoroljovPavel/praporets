package io.praporets.analytics.deviation;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.praporets.analytics.TestKafka;
import io.praporets.analytics.TestPostgres;
import io.praporets.analytics.common.KafkaTopics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Детекція відхилення rollout. Планувальник вимкнено —
 * {@code check(window)} смикається руками, без перегонів із фоновим тіком;
 * min-sample знижений до 50, щоб вибірки тестів були керовані.
 *
 * <p>Ізоляція: унікальні env і РІЗНІ вікна на тест — {@code check}
 * читає все вікно цілком, спільне вікно тягло б чужі рядки.
 */
@SpringBootTest(properties = {
    "praporets.analytics.deviation.enabled=false",
    "praporets.analytics.deviation.min-sample=50"
})
@ExtendWith(OutputCaptureExtension.class)
class RolloutDeviationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @ServiceConnection
    static final KafkaContainer KAFKA = TestKafka.INSTANCE;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RolloutExpectations expectations;

    @Autowired
    DeviationChecker checker;

    @Autowired
    MeterRegistry meterRegistry;

    // ------------------------------------------------------------------ tests

    @Test
    void rollout_event_without_rule_increments_rollout_count() throws Exception {
        String env = uniqueEnv();

        produceEvaluation(env, evaluation(env, "ROLLOUT", null));    // ← рахується
        produceEvaluation(env, evaluation(env, "ROLLOUT", "r1"));    // rule-level — ні
        produceEvaluation(env, evaluation(env, "RULE_MATCH", "r1")); // не rollout — ні

        awaitTrue("усі три події оброблені", () -> evalCount(env) == 3);
        Long rollout = jdbc.queryForObject(
            "SELECT coalesce(sum(rollout_count), 0) FROM evaluation_agg WHERE environment = ?",
            Long.class, env);
        assertThat(rollout).as("тільки flag-level ROLLOUT").isEqualTo(1);
    }

    @Test
    void expectations_follow_flag_changes_topic() throws Exception {
        String env = uniqueEnv();
        String flag = flagOf(env);

        produceFlagChange(env, changeWithRollout(env, flag, 30_000, 70_000));
        awaitTrue("очікування з'явились", () -> expectations.weightsFor(env, flag).isPresent());
        assertThat(expectations.weightsFor(env, flag))
            .contains(Map.of("on", 30_000, "off", 70_000));

        // upsert того ж флага БЕЗ rollout → очікування знімаються
        produceFlagChange(env, changeWithoutRollout(env, flag));
        awaitTrue("очікування зняті", () -> expectations.weightsFor(env, flag).isEmpty());
    }

    @Test
    void deviation_gauge_reflects_actual_minus_expected(CapturedOutput output) throws Exception {
        String env = uniqueEnv();
        String flag = flagOf(env);
        Instant window = Instant.parse("2026-08-02T09:15:00Z");

        produceFlagChange(env, changeWithRollout(env, flag, 30_000, 70_000));
        awaitTrue("очікування з'явились", () -> expectations.weightsFor(env, flag).isPresent());
        seedRollout(env, flag, "on", window, 80);   // факт 80% проти 30%
        seedRollout(env, flag, "off", window, 20);  // факт 20% проти 70%

        checker.check(window);

        assertThat(gaugeValue(env, flag, "on")).isCloseTo(0.5, within(1e-9));
        assertThat(gaugeValue(env, flag, "off")).isCloseTo(-0.5, within(1e-9));
        assertThat(output.getOut() + output.getErr())
                .as("WARN з ключем флага").contains(flag);
    }

    @Test
    void small_sample_is_ignored(CapturedOutput output) throws Exception {
        String env = uniqueEnv();
        String flag = flagOf(env);
        Instant window = Instant.parse("2026-08-02T09:17:00Z");

        produceFlagChange(env, changeWithRollout(env, flag, 50_000, 50_000));
        awaitTrue("очікування з'явились", () -> expectations.weightsFor(env, flag).isPresent());
        seedRollout(env, flag, "on", window, 10);  // 100% перекіс, але n=10 < 50

        checker.check(window);

        assertThat(meterRegistry.find("praporets_rollout_deviation")
            .tags("environment", env).gauges())
                .as("мала вибірка — ані гейджа").isEmpty();
        assertThat(output.getOut() + output.getErr()).doesNotContain(flag);
    }

    @Test
    void variant_with_zero_events_still_gets_deviation_row() throws Exception {
        String env = uniqueEnv();
        String flag = flagOf(env);
        Instant window = Instant.parse("2026-08-02T09:19:00Z");

        produceFlagChange(env, changeWithRollout(env, flag, 50_000, 50_000));
        awaitTrue("очікування з'явились", () -> expectations.weightsFor(env, flag).isPresent());
        seedRollout(env, flag, "on", window, 60);  // "off" зник повністю

        checker.check(window);

        assertThat(gaugeValue(env, flag, "on")).isCloseTo(0.5, within(1e-9));
        assertThat(gaugeValue(env, flag, "off"))
                .as("зниклий варіант — найгірше відхилення, не «немає даних»")
            .isCloseTo(-0.5, within(1e-9));
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEnv() {
        return "rd-" + UUID.randomUUID();
    }

    private static String flagOf(String env) {
        return "flag." + env;
    }

    /**
     * Evaluation-подія у форматі edge; вікно фіксоване 08:15.
     */
    private static String evaluation(String env, String reason, String ruleId) {
        return """
            {"evaluationId": "%s", "occurredAt": "2026-08-02T08:15:30Z", "environment": "%s",
             "flagKey": "%s", "variantKey": "on", "reason": "%s", "ruleId": %s,
             "revision": 7, "userKeyHash": "%s", "edgeInstanceId": "flag-edge-test"}
            """.formatted(UUID.randomUUID(), env, flagOf(env), reason,
            ruleId == null ? "null" : "\"" + ruleId + "\"", UUID.randomUUID());
    }

    /**
     * Повідомлення flag.changes: флаг із rollout-бакетами on/off.
     */
    private static String changeWithRollout(String env, String flag, int onWeight, int offWeight) {
        return """
            {"environmentKey": "%s", "revision": 5, "delta": {"upsertedFlags": [
              {"key": "%s", "valueType": "BOOLEAN", "enabled": true, "defaultVariant": "on",
               "rollout": {"salt": "s1", "buckets": [
                 {"variantKey": "on", "weight": %d}, {"variantKey": "off", "weight": %d}]}}]}}
            """.formatted(env, flag, onWeight, offWeight);
    }

    private static String changeWithoutRollout(String env, String flag) {
        return """
            {"environmentKey": "%s", "revision": 6, "delta": {"upsertedFlags": [
              {"key": "%s", "valueType": "BOOLEAN", "enabled": true, "defaultVariant": "on"}]}}
            """.formatted(env, flag);
    }

    private void produceEvaluation(String env, String value) throws Exception {
        send(KafkaTopics.FLAG_EVALUATIONS, flagOf(env), value);
    }

    private void produceFlagChange(String env, String value) throws Exception {
        send(KafkaTopics.FLAG_CHANGES, env, value);
    }

    private void send(String topic, String key, String value) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        record.headers().add("schema-version", "1".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    }

    private void seedRollout(String env, String flag, String variant, Instant window, long rolloutCount) {
        jdbc.update("""
                INSERT INTO evaluation_agg
                    (environment, flag_key, variant_key, window_start, eval_count, unique_users, rollout_count)
                VALUES (?, ?, ?, ?, ?, 0, ?)
                """,
            env, flag, variant, window.atOffset(ZoneOffset.UTC), rolloutCount, rolloutCount);
    }

    private long evalCount(String env) {
        Long n = jdbc.queryForObject(
            "SELECT coalesce(sum(eval_count), 0) FROM evaluation_agg WHERE environment = ?",
            Long.class, env);
        return n == null ? 0 : n;
    }

    private Double gaugeValue(String env, String flag, String variant) {
        Gauge gauge = meterRegistry.find("praporets_rollout_deviation")
            .tags("environment", env, "flag", flag, "variant", variant)
            .gauge();
        assertThat(gauge).as("рядок гейджа для %s/%s", flag, variant).isNotNull();
        return gauge.value();
    }

    private static void awaitTrue(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }
}
