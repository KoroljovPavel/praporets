package io.praporets.controlplane.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.praporets.controlplane.TestKafka;
import io.praporets.controlplane.common.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт доставки — relay забирає пачку через
 * FOR UPDATE SKIP LOCKED, публікує з ack-ом і маркує. Планувальник у цьому
 * контексті вимкнений — кожен тік викликається явно.
 */
class OutboxRelayTest extends AbstractOutboxTest {

    @Autowired
    OutboxRelay relay;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    DataSource dataSource;

    @Test
    void relay_publishes_pending_row_and_marks_it_published() throws Exception {
        String env = seedEnvironmentWithConfiguredFlag("relay-a");

        int published = relay.relayBatch();

        assertThat(published).isGreaterThanOrEqualTo(1);
        assertThat(unpublishedCount(env)).isZero();

        List<ConsumerRecord<String, String>> records = consumeForEnvironment(env, 1);
        assertThat(records).hasSize(1);
        ConsumerRecord<String, String> record = records.getFirst();
        assertThat(record.key()).isEqualTo(env);
        assertThat(record.value()).contains("\"revision\"");
        Header schemaVersion = record.headers().lastHeader("schema-version");
        assertThat(schemaVersion).as("заголовок schema-version").isNotNull();
        assertThat(new String(schemaVersion.value(), StandardCharsets.UTF_8)).isEqualTo("1");
    }

    @Test
    void revisions_of_one_environment_arrive_in_order() throws Exception {
        String env = seedEnvironmentWithConfiguredFlag("relay-b");
        toggle(env, false);
        toggle(env, true);

        relay.relayBatch();

        List<ConsumerRecord<String, String>> records = consumeForEnvironment(env, 3);
        // один ключ → одна партиція → порядок гарантований Kafka
        assertThat(records)
            .extracting(r -> r.value().replaceAll(".*\"revision\"\\s*:\\s*(\\d+).*", "$1"))
            .containsExactly("1", "2", "3");
    }

    @Test
    void locked_row_is_skipped_without_blocking_and_picked_up_later() throws Exception {
        String env = seedEnvironmentWithConfiguredFlag("relay-c");

        try (Connection lockHolder = dataSource.getConnection()) {
            lockHolder.setAutoCommit(false);
            try (Statement statement = lockHolder.createStatement()) {
                // «інша репліка» тримає лок на нашому рядку
                statement.execute(
                    "SELECT id FROM outbox WHERE partition_key = '" + env + "' FOR UPDATE");
            }

            relay.relayBatch(); // не повисне: SKIP LOCKED мовчки пропустить рядок

            assertThat(unpublishedCount(env))
                .as("залокований рядок пропущений, не опублікований")
                .isEqualTo(1);
            lockHolder.rollback();
        }

        relay.relayBatch(); // лок знято — рядок доїжджає наступним тіком
        assertThat(unpublishedCount(env)).isZero();
    }

    @Test
    void second_tick_has_nothing_to_resend() throws Exception {
        String env = seedEnvironmentWithConfiguredFlag("relay-d");
        drain();

        assertThat(relay.relayBatch()).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class))
            .isZero();
        // навмисно чекаємо ДВОХ повідомлень: якби дубль існував, ми б його
        // зловили; deadline спливе — лишиться рівно одне
        assertThat(consumeForEnvironment(env, 2))
            .as("повторний тік не дублює повідомлень")
            .hasSize(1);
    }

    @Test
    void lag_gauge_tracks_oldest_unpublished_and_drops_after_relay() throws Exception {
        drain();
        jdbc.update("""
                INSERT INTO outbox (id, aggregate_id, topic, partition_key, payload, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, now() - interval '30 seconds')
                """, UUID.randomUUID(), UUID.randomUUID(), KafkaTopics.FLAG_CHANGES,
            "out-relay-lag", "{\"environmentKey\": \"out-relay-lag\", \"revision\": 1}");

        double lag = meterRegistry.get("praporets.outbox.lag.seconds").gauge().value();
        // верхня межа — щоб зловити «повернув epoch-секунди замість віку»
        assertThat(lag).as("вік найстарішого неопублікованого").isBetween(25.0, 300.0);

        drain();

        assertThat(meterRegistry.get("praporets.outbox.lag.seconds").gauge().value())
            .as("порожній outbox → лаг 0")
            .isZero();
    }

    // ------------------------------------------------ хелпери

    private void drain() {
        while (relay.relayBatch() > 0) {
            // публікуємо все, що накопичилось (і чуже з сусідніх тестів)
        }
    }

    /**
     * Читає топік з початку свіжою групою і фільтрує повідомлення свого env.
     */
    private List<ConsumerRecord<String, String>> consumeForEnvironment(String env, int expected) {
        Map<String, Object> config = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestKafka.INSTANCE.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(KafkaTopics.FLAG_CHANGES));
            long deadline = System.currentTimeMillis() + 10_000;
            while (found.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(200))
                    .forEach(r -> {
                        if (env.equals(r.key())) found.add(r);
                    });
            }
        }
        return found;
    }
}
