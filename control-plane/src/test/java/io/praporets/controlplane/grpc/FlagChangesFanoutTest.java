package io.praporets.controlplane.grpc;

import com.google.protobuf.util.JsonFormat;
import io.grpc.stub.StreamObserver;
import io.praporets.controlplane.TestKafka;
import io.praporets.controlplane.TestPostgres;
import io.praporets.controlplane.common.KafkaTopics;
import io.praporets.grpc.config.v1.ConfigDelta;
import io.praporets.grpc.config.v1.ConfigUpdate;
import io.praporets.grpc.config.v1.FlagDefinition;
import io.praporets.grpc.config.v1.ValueType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Контракт fan-out — те, що з'явилось у топіку, репліка пушить
 * у СВОЇ відкриті стріми. Роль «репліки A» грає сам тест: продюсить готові
 * події в топік, а «реплікою B» виступає контекст під тестом — його
 * консюмер має доставити дельту зареєстрованому підписнику.
 *
 * <p>Підписник — фейковий observer прямо в {@link ConfigStreamRegistry}
 * (справжній gRPC-шлях покриває {@code ConfigGrpcStreamingTest}). Relay
 * вимкнений — джерело подій тут тільки тест. Кожен тест чекає assignment
 * консюмера перед першим продюсом ({@code latest} + продюс до
 * assignment-у = загублене повідомлення).
 */
@SpringBootTest(properties = "praporets.outbox.relay.enabled=false")
class FlagChangesFanoutTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @ServiceConnection
    static final KafkaContainer KAFKA = TestKafka.INSTANCE;

    @Autowired
    ConfigStreamRegistry registry;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    KafkaListenerEndpointRegistry listenerRegistry;

    /**
     * Зареєстровані підписники — прибираються після тесту (контекст кешується).
     */
    private final List<Registration> registrations = new ArrayList<>();

    @BeforeEach
    void awaitFanoutAssigned() throws Exception {
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

    @AfterEach
    void deregisterObservers() {
        registrations.forEach(r -> registry.deregister(r.env(), r.observer()));
        registrations.clear();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void event_published_to_topic_reaches_local_subscribed_stream() throws Exception {
        String env = uniqueEnv();
        CollectingObserver observer = subscribe(env);

        produce(env, payload(env, 42, "flag.fanout-a"), "1");

        ConfigUpdate update = observer.awaitUpdate();
        assertThat(update.getRevision()).isEqualTo(42);
        assertThat(update.getPayloadCase()).isEqualTo(ConfigUpdate.PayloadCase.DELTA);
        assertThat(update.getDelta().getUpsertedFlagsCount()).isEqualTo(1);
        assertThat(update.getDelta().getUpsertedFlags(0).getKey()).isEqualTo("flag.fanout-a");
    }

    @Test
    void message_with_unknown_schema_version_is_skipped() throws Exception {
        String env = uniqueEnv();
        CollectingObserver observer = subscribe(env);

        // невідома версія → скіп; той самий env = та сама партиція = порядок,
        // тож якби «99» пушнулась, вона прийшла б ПЕРШОЮ і зламала асерти
        produce(env, payload(env, 41, "flag.fanout-old"), "99");
        produce(env, payload(env, 43, "flag.fanout-b"), "1");

        ConfigUpdate update = observer.awaitUpdate();
        assertThat(update.getRevision()).isEqualTo(43);
        assertThat(observer.updates).hasSize(1);
    }

    @Test
    void malformed_payload_does_not_block_the_partition() throws Exception {
        String env = uniqueEnv();
        CollectingObserver observer = subscribe(env);

        produce(env, "це не json {{{", "1");
        produce(env, payload(env, 44, "flag.fanout-c"), "1");

        ConfigUpdate update = observer.awaitUpdate();
        assertThat(update.getRevision()).isEqualTo(44);
        assertThat(observer.updates).hasSize(1);
    }

    @Test
    void environment_without_local_subscribers_is_a_quiet_no_op() throws Exception {
        String silent = uniqueEnv();   // ніхто не підписаний
        String env = uniqueEnv();
        CollectingObserver observer = subscribe(env);

        produce(silent, payload(silent, 7, "flag.fanout-ghost"), "1");
        produce(env, payload(env, 45, "flag.fanout-d"), "1");

        ConfigUpdate update = observer.awaitUpdate();
        assertThat(update.getRevision()).isEqualTo(45);
        assertThat(observer.updates).hasSize(1);
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEnv() {
        return "fan-" + UUID.randomUUID();
    }

    private CollectingObserver subscribe(String env) {
        CollectingObserver observer = new CollectingObserver();
        registry.register(env, observer);
        registrations.add(new Registration(env, observer));
        return observer;
    }

    /**
     * Payload рівно в тому форматі, що пише OutboxWriter.
     */
    private static String payload(String env, long revision, String flagKey) throws Exception {
        ConfigDelta delta = ConfigDelta.newBuilder()
            .addUpsertedFlags(FlagDefinition.newBuilder()
                .setKey(flagKey)
                .setValueType(ValueType.BOOLEAN)
                .setEnabled(true)
                .setDefaultVariant("on"))
            .build();
        return "{\"environmentKey\": \"%s\", \"revision\": %d, \"delta\": %s}"
            .formatted(env, revision, JsonFormat.printer().print(delta));
    }

    private void produce(String env, String value, String schemaVersion) throws Exception {
        ProducerRecord<String, String> record =
            new ProducerRecord<>(KafkaTopics.FLAG_CHANGES, env, value);
        record.headers().add("schema-version", schemaVersion.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    }

    private record Registration(String env, StreamObserver<ConfigUpdate> observer) {
    }

    private static final class CollectingObserver implements StreamObserver<ConfigUpdate> {
        final List<ConfigUpdate> updates = new CopyOnWriteArrayList<>();

        @Override
        public void onNext(ConfigUpdate update) {
            updates.add(update);
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
        }

        /**
         * Чекає перший update до 10с; тиша = fail.
         */
        ConfigUpdate awaitUpdate() throws InterruptedException {
            long deadline = System.currentTimeMillis() + 10_000;
            while (updates.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            if (updates.isEmpty()) {
                return fail("підписник не отримав ConfigUpdate за 10с");
            }
            return updates.getFirst();
        }
    }
}
