package io.praporets.edge.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.praporets.edge.config.ConfigStore;
import io.praporets.edge.config.FakeControlPlane;
import io.praporets.grpc.evaluation.v1.EvaluateAllRequest;
import io.praporets.grpc.evaluation.v1.EvaluateAllResponse;
import io.praporets.grpc.evaluation.v1.EvaluationContext;
import io.praporets.grpc.evaluation.v1.EvaluationServiceGrpc;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Специфікує наскрізний контракт публікації — обчислення через реальний
 * REST/gRPC edge → подія в {@code praporets.flag.evaluations.v1}: key =
 * flagKey, header {@code schema-version: 1}, JSON-поля події, хешований
 * userKey. Окремий Quarkus-контекст: FakeControlPlane (конфігурація) +
 * EventsKafka (брокер, events увімкнені).
 *
 * <p>Топік спільний на JVM-прогін, тому кожен тест фільтрує повідомлення за
 * хешем СВОГО унікального userKey — чужі події невидимі.
 */
@QuarkusTest
@QuarkusTestResource(value = FakeControlPlane.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = EventsKafka.class, restrictToAnnotatedClass = true)
class EvaluationEventPublishingTest {

    @Inject
    ConfigStore store;

    private static ManagedChannel channel;

    // edge живе на Jackson 2 (Quarkus), НЕ tools.jackson як control-plane
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @BeforeEach
    void awaitLoaded() throws InterruptedException {
        await("снапшот завантажено", () -> store.isLoaded());
        if (channel == null) {
            channel = ManagedChannelBuilder.forAddress("localhost", 8081).usePlaintext().build();
        }
    }

    @AfterAll
    static void closeChannel() {
        if (channel != null) {
            channel.shutdownNow();
            channel = null;
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    void rest_evaluation_lands_in_evaluations_topic() throws Exception {
        String userKey = "user-" + UUID.randomUUID();
        String hash = EvaluationEventsTest.sha256Hex(userKey);

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "environmentKey": "%s",
                  "flagKey": "%s",
                  "context": {"userKey": "%s", "attributes": {"country": "UA"}}
                }
                """.formatted(FakeControlPlane.ENVIRONMENT, FakeControlPlane.FLAG_KEY, userKey))
            .when()
            .post("/api/v1/evaluate")
            .then()
            .statusCode(200);

        ConsumerRecord<String, String> record =
            consumeMatching(value -> value.contains(hash), 1).getFirst();

        assertThat(record.key()).as("key повідомлення = flagKey").isEqualTo(FakeControlPlane.FLAG_KEY);
        assertThat(new String(record.headers().lastHeader("schema-version").value(), StandardCharsets.UTF_8))
            .isEqualTo("1");

        JsonNode event = jsonMapper.readTree(record.value());
        assertThat(UUID.fromString(event.get("evaluationId").asText()).version())
            .as("evaluationId — UUIDv7").isEqualTo(7);
        assertThat(Instant.parse(event.get("occurredAt").asText()))
            .as("occurredAt — ISO-8601").isBeforeOrEqualTo(Instant.now());
        assertThat(event.get("environment").asText()).isEqualTo(FakeControlPlane.ENVIRONMENT);
        assertThat(event.get("flagKey").asText()).isEqualTo(FakeControlPlane.FLAG_KEY);
        assertThat(event.get("variantKey").asText()).isEqualTo("on");
        assertThat(event.get("reason").asText()).isEqualTo("RULE_MATCH");
        assertThat(event.get("ruleId").asText()).isEqualTo("r1");
        assertThat(event.get("revision").asLong()).isEqualTo(FakeControlPlane.SNAPSHOT_REVISION);
        assertThat(event.get("userKeyHash").asText()).isEqualTo(hash);
        assertThat(event.get("edgeInstanceId").asText()).startsWith("flag-edge-");

        // PII-гейт: сирого userKey немає НІДЕ в повідомленні
        assertThat(record.value()).doesNotContain(userKey);
    }

    @Test
    void evaluate_all_emits_one_event_per_flag() {
        String userKey = "user-all-" + UUID.randomUUID();
        String hash = EvaluationEventsTest.sha256Hex(userKey);

        EvaluateAllResponse response = EvaluationServiceGrpc.newBlockingStub(channel)
            .evaluateAll(EvaluateAllRequest.newBuilder()
                .setEnvironmentKey(FakeControlPlane.ENVIRONMENT)
                .setContext(EvaluationContext.newBuilder().setUserKey(userKey))
                .build());
        assertThat(response.getEvaluationsCount()).isPositive();

        List<ConsumerRecord<String, String>> records =
            consumeMatching(value -> value.contains(hash), response.getEvaluationsCount());

        assertThat(records).hasSize(response.getEvaluationsCount());
        assertThat(records.stream()
            .map(r -> parse(r.value()).get("flagKey").asText())
            .collect(Collectors.toSet()))
            .as("по одній події на кожен флаг відповіді")
            .isEqualTo(response.getEvaluationsList().stream()
                .map(e -> e.getFlagKey())
                .collect(Collectors.toSet()));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Jackson 2 кидає checked-виняток — обгортка для лямбд.
     */
    private JsonNode parse(String value) {
        try {
            return jsonMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException("невалідний JSON події: " + value, e);
        }
    }

    /**
     * Читає топік з початку СВОЇМ консюмером (унікальна група) і збирає
     * повідомлення за фільтром, доки не набере {@code minCount} (до 15с).
     */
    private List<ConsumerRecord<String, String>> consumeMatching(
        Predicate<String> valueFilter, int minCount) {
        Properties props = new Properties();
        props.putAll(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, EventsKafka.KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "events-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));

        List<ConsumerRecord<String, String>> matched = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(EvaluationEventPublisher.TOPIC));
            long deadline = System.currentTimeMillis() + 15_000;
            while (matched.size() < minCount && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (valueFilter.test(record.value())) {
                        matched.add(record);
                    }
                }
            }
        }
        if (matched.size() < minCount) {
            fail("очікували ≥ %d подій, отримали %d за 15с".formatted(minCount, matched.size()));
        }
        return matched;
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }
}
