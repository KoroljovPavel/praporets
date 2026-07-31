package io.praporets.controlplane.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.praporets.controlplane.TestKafka;
import io.praporets.controlplane.TestPostgres;
import io.praporets.controlplane.outbox.OutboxRelay;
import io.praporets.grpc.config.v1.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 02b: наскрізний контракт gRPC ConfigService — снапшот, catch-up дельта,
 * live-пуш після коміту, SnapshotRequired за межами вікна, heartbeat,
 * прибирання стрімів. Сервер — справжній, але in-process (без порту);
 * Postgres — той самий singleton, що й у решти інтеграційних.
 *
 * <p><b>Свідомо НЕ успадковує {@code AbstractIntegrationTest}</b> і не має
 * {@code @Transactional}: live-пуш живе на {@code AFTER_COMMIT}, який у
 * відкочуваній тест-транзакції не настає ніколи. Дані комітяться по-справжньому,
 * тому кожен тест працює з унікальними ключами (env-*, flag-*) — прибирання
 * не потрібне, зіткнення з іншими тестами неможливе.
 *
 * <p>Heartbeat прискорено до 250мс, вікно ревізій звужено до 5 — інакше
 * SnapshotRequired довелося б «заробляти» 500 комітами.
 *
 * <p><b>03b:</b> live-пуш більше не локальний — тест live-дельти став
 * наскрізним: REST → outbox → relay (тік руками) → Kafka → консюмер fan-out →
 * gRPC-стрім. Relay-ПЛАНУВАЛЬНИК вимкнений свідомо: Spring кешує цей контекст
 * живим до кінця JVM, і фоновий relay із доступним Kafka публікував би рядки
 * спільного Postgres, поки ганяються outbox-тести, ламаючи їхні асерти на
 * {@code published_at IS NULL}.
 */
@SpringBootTest(properties = {
    "praporets.grpc.heartbeat-interval=250ms",
    "praporets.grpc.revision-window=5",
    "praporets.outbox.relay.enabled=false"
})
@AutoConfigureMockMvc
@AutoConfigureTestGrpcTransport
class ConfigGrpcStreamingTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @ServiceConnection
    static final KafkaContainer KAFKA = TestKafka.INSTANCE;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private GrpcChannelFactory channels;

    @Autowired
    private MeterRegistry meterRegistry;

    // ---------------------------------------------------------------- helpers

    private ConfigServiceGrpc.ConfigServiceBlockingStub stub() {
        return ConfigServiceGrpc.newBlockingStub(channels.createChannel("inProcess"))
            .withDeadlineAfter(15, TimeUnit.SECONDS);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private void createEnvironment(String key) throws Exception {
        mvc.perform(post("/api/v1/environments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\": \"%s\", \"name\": \"gRPC test env\"}".formatted(key)))
            .andExpect(status().isCreated());
    }

    private void createFlag(String flagKey) throws Exception {
        mvc.perform(post("/api/v1/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "key": "%s",
                      "name": "gRPC test flag",
                      "valueType": "BOOLEAN",
                      "variants": [{"key": "on", "value": true}, {"key": "off", "value": false}]
                    }
                    """.formatted(flagKey)))
            .andExpect(status().isCreated());
    }

    /** Перший PUT конфігурації (ревізія +1): правило country IN UA + rollout. */
    private void putInitialConfig(String env, String flagKey) throws Exception {
        mvc.perform(put("/api/v1/environments/%s/flags/%s/config".formatted(env, flagKey))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
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
                      "rollout": {"salt": "v1", "buckets": [{"variantKey": "on", "weight": 100000}]}
                    }
                    """))
            .andExpect(status().isCreated());
    }

    /** Kill switch (ревізія +1) — найпростіший спосіб накрутити ревізії. */
    private void toggle(String env, String flagKey, boolean enabled) throws Exception {
        mvc.perform(post("/api/v1/environments/%s/flags/%s/toggle".formatted(env, flagKey))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": %s}".formatted(enabled)))
            .andExpect(status().isOk());
    }

    /** Upsert сегмента (ревізія +1). */
    private void putSegment(String env, String segmentKey) throws Exception {
        mvc.perform(put("/api/v1/environments/%s/segments/%s".formatted(env, segmentKey))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"conditions": [{"attribute": "plan", "operator": "IN", "values": ["pro"], "negate": false}]}
                    """))
            .andExpect(status().is2xxSuccessful());
    }

    /** Ковтає heartbeat-и (вони летять кожні 250мс), повертає перше «змістовне» повідомлення. */
    private static ConfigUpdate nextNonHeartbeat(Iterator<ConfigUpdate> updates) {
        while (true) {
            ConfigUpdate update = updates.next();
            if (update.getPayloadCase() != ConfigUpdate.PayloadCase.HEARTBEAT) {
                return update;
            }
        }
    }

    /** Чекає перший heartbeat — доказ, що сервер зареєстрував стрім у реєстрі. */
    private static ConfigUpdate awaitHeartbeat(Iterator<ConfigUpdate> updates) {
        while (true) {
            ConfigUpdate update = updates.next();
            if (update.getPayloadCase() == ConfigUpdate.PayloadCase.HEARTBEAT) {
                return update;
            }
        }
    }

    private double activeStreamsGauge() {
        return meterRegistry.get("praporets_config_streams_active").gauge().value();
    }

    /**
     * 03b, камінь #1: консюмер із {@code latest} не побачить повідомлення,
     * продюснуте ДО того, як йому роздали партиції — чекаємо assignment.
     */
    private void awaitFanoutAssigned() throws InterruptedException {
        MessageListenerContainer container =
            listenerRegistry.getListenerContainer("flag-changes-fanout");
        assertThat(container).as("слухач flag-changes-fanout існує").isNotNull();
        long deadline = System.currentTimeMillis() + 15_000;
        while ((container.getAssignedPartitions() == null
            || container.getAssignedPartitions().isEmpty())
            && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(container.getAssignedPartitions())
            .as("консюмеру роздали партиції").isNotEmpty();
    }

    // ------------------------------------------------------------ GetSnapshot

    @Test
    void get_snapshot_returns_full_environment_config() throws Exception {
        String env = unique("env");
        String flagKey = unique("flag");
        createEnvironment(env);
        createFlag(flagKey);
        putInitialConfig(env, flagKey);   // ревізія 1
        putSegment(env, "beta-testers");  // ревізія 2

        ConfigSnapshot snapshot = stub().getSnapshot(
            SnapshotRequest.newBuilder().setEnvironmentKey(env).build());

        assertThat(snapshot.getEnvironmentKey()).isEqualTo(env);
        assertThat(snapshot.getRevision()).isEqualTo(2);

        assertThat(snapshot.getFlagsCount()).isEqualTo(1);
        FlagDefinition flag = snapshot.getFlags(0);
        assertThat(flag.getKey()).isEqualTo(flagKey);
        assertThat(flag.getValueType()).isEqualTo(ValueType.BOOLEAN);
        assertThat(flag.getEnabled()).isTrue();
        assertThat(flag.getDefaultVariant()).isEqualTo("on");
        assertThat(flag.getVariantsList())
            .anySatisfy(v -> {
                assertThat(v.getKey()).isEqualTo("on");
                assertThat(v.getJsonValue()).isEqualTo("true");
            })
            .hasSize(2);
        assertThat(flag.getRulesCount()).isEqualTo(1);
        assertThat(flag.getRules(0).getId()).isEqualTo("r1");
        assertThat(flag.getRules(0).getClauses(0).getOperator()).isEqualTo(Operator.IN);
        assertThat(flag.hasRollout()).isTrue();
        assertThat(flag.getRollout().getSalt()).isEqualTo("v1");

        assertThat(snapshot.getSegmentsCount()).isEqualTo(1);
        assertThat(snapshot.getSegments(0).getKey()).isEqualTo("beta-testers");
        assertThat(snapshot.getSegments(0).getClauses(0).getAttribute()).isEqualTo("plan");
    }

    @Test
    void get_snapshot_for_unknown_environment_is_not_found() {
        assertThatThrownBy(() -> stub().getSnapshot(
                SnapshotRequest.newBuilder().setEnvironmentKey("no-such-env").build()))
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    // ----------------------------------------------------------- StreamConfig

    @Test
    void stream_for_unknown_environment_is_not_found() {
        Iterator<ConfigUpdate> updates = stub().streamConfig(
            StreamRequest.newBuilder().setEnvironmentKey("no-such-env").build());

        assertThatThrownBy(updates::next)
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void stream_receives_live_delta_after_committed_change() throws Exception {
        String env = unique("env");
        String flagKey = unique("flag");
        createEnvironment(env);
        createFlag(flagKey);
        putInitialConfig(env, flagKey);  // ревізія 1

        // підключаємось АКТУАЛЬНИМИ (fromRevision = поточна) → catch-up нема
        Iterator<ConfigUpdate> updates = stub().streamConfig(StreamRequest.newBuilder()
            .setEnvironmentKey(env).setFromRevision(1).setEdgeInstanceId("test-edge").build());

        // перший heartbeat = стрім зареєстровано; лише тепер зміна гарантовано
        // не провалиться в щілину «між підключенням і реєстрацією»
        awaitHeartbeat(updates);
        awaitFanoutAssigned();

        toggle(env, flagKey, false);  // ревізія 2, комітиться по-справжньому
        // 03b: пуш їде через Kafka; планувальник вимкнений — тік руками.
        // Drain, а не один batch: наш рядок НАЙНОВІШИЙ, а relay бере
        // найстаріші — попереду може стояти хвіст сусідніх тестів
        while (relay.relayBatch() > 0) {
            // публікуємо все до порожнього outbox
        }

        ConfigUpdate update = nextNonHeartbeat(updates);
        assertThat(update.getPayloadCase()).isEqualTo(ConfigUpdate.PayloadCase.DELTA);
        assertThat(update.getRevision()).isEqualTo(2);
        assertThat(update.getDelta().getUpsertedFlagsCount()).isEqualTo(1);
        assertThat(update.getDelta().getUpsertedFlags(0).getKey()).isEqualTo(flagKey);
        assertThat(update.getDelta().getUpsertedFlags(0).getEnabled()).isFalse();
    }

    @Test
    void catch_up_delta_squashes_missed_revisions_into_latest_state() throws Exception {
        String env = unique("env");
        String flagKey = unique("flag");
        createEnvironment(env);
        createFlag(flagKey);
        putInitialConfig(env, flagKey);  // ревізія 1
        toggle(env, flagKey, false);     // ревізія 2
        putSegment(env, "beta-testers"); // ревізія 3

        // edge «бачив» лише ревізію 1 → пропустив 2 і 3
        Iterator<ConfigUpdate> updates = stub().streamConfig(StreamRequest.newBuilder()
            .setEnvironmentKey(env).setFromRevision(1).build());

        ConfigUpdate catchUp = nextNonHeartbeat(updates);
        assertThat(catchUp.getPayloadCase()).isEqualTo(ConfigUpdate.PayloadCase.DELTA);
        assertThat(catchUp.getRevision()).isEqualTo(3);
        // флаг мінявся у 2 ревізіях, але в дельті він ОДИН і в поточному стані
        assertThat(catchUp.getDelta().getUpsertedFlagsCount()).isEqualTo(1);
        assertThat(catchUp.getDelta().getUpsertedFlags(0).getEnabled()).isFalse();
        assertThat(catchUp.getDelta().getUpsertedSegmentsCount()).isEqualTo(1);
        assertThat(catchUp.getDelta().getUpsertedSegments(0).getKey()).isEqualTo("beta-testers");
        // DELETE-ендпоінтів немає — removed_* порожні завжди
        assertThat(catchUp.getDelta().getRemovedFlagKeysCount()).isZero();
    }

    @Test
    void stream_far_behind_window_gets_snapshot_required_and_completes() throws Exception {
        String env = unique("env");
        String flagKey = unique("flag");
        createEnvironment(env);
        createFlag(flagKey);
        putInitialConfig(env, flagKey);          // ревізія 1
        for (int i = 0; i < 6; i++) {
            toggle(env, flagKey, i % 2 == 0);    // ревізії 2..7
        }

        // розрив 7-1=6 > вікно 5 → жодних дельт, тільки «йди за снапшотом»
        Iterator<ConfigUpdate> updates = stub().streamConfig(StreamRequest.newBuilder()
            .setEnvironmentKey(env).setFromRevision(1).build());

        ConfigUpdate first = updates.next();  // без реєстрації heartbeat-ів бути не може
        assertThat(first.getPayloadCase()).isEqualTo(ConfigUpdate.PayloadCase.SNAPSHOT_REQUIRED);
        assertThat(updates.hasNext()).isFalse();  // сервер закрив стрім (onCompleted)
    }

    // -------------------------------------------------- heartbeat + cleanup

    @Test
    void heartbeat_carries_server_time_and_current_revision() throws Exception {
        String env = unique("env");
        createEnvironment(env);
        putSegment(env, "beta-testers");  // ревізія 1

        Iterator<ConfigUpdate> updates = stub().streamConfig(StreamRequest.newBuilder()
            .setEnvironmentKey(env).setFromRevision(1).build());

        ConfigUpdate heartbeat = awaitHeartbeat(updates);
        assertThat(heartbeat.getHeartbeat().getServerTimeMillis()).isPositive();
        // ревізія в heartbeat = детектор відставання для edge (02d)
        assertThat(heartbeat.getRevision()).isEqualTo(1);
    }

    @Test
    void cancelled_stream_is_removed_from_registry() throws Exception {
        String env = unique("env");
        createEnvironment(env);

        double baseline = activeStreamsGauge();

        // короткий дедлайн: клієнт відвалиться сам, серверу лишиться прибрати
        Iterator<ConfigUpdate> updates = ConfigServiceGrpc
            .newBlockingStub(channels.createChannel("inProcess"))
            .withDeadlineAfter(700, TimeUnit.MILLISECONDS)
            .streamConfig(StreamRequest.newBuilder().setEnvironmentKey(env).build());

        awaitHeartbeat(updates);  // стрім точно зареєстровано
        assertThat(activeStreamsGauge()).isEqualTo(baseline + 1);

        assertThatThrownBy(() -> { while (true) { updates.next(); } })
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));

        // відміна доїжджає до сервера асинхронно — трохи почекаємо
        long deadline = System.currentTimeMillis() + 5_000;
        while (activeStreamsGauge() > baseline && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(activeStreamsGauge()).isEqualTo(baseline);
    }
}
