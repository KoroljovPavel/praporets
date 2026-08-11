package io.praporets.controlplane.outbox;

import io.praporets.controlplane.common.KafkaTopics;
import io.praporets.controlplane.service.ConfigChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт запису в outbox — рядок народжується в тій самій
 * транзакції, що й зміна конфігурації, і вмирає разом з нею при відкаті.
 */
class OutboxWriteTest extends AbstractOutboxTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Test
    void config_change_writes_outbox_row_in_same_transaction() throws Exception {
        String env = seedEnvironmentWithConfiguredFlag("write-a");

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT topic, aggregate_id, payload::text AS payload, published_at
            FROM outbox WHERE partition_key = ?
            """, env);

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("topic")).isEqualTo(KafkaTopics.FLAG_CHANGES);
        assertThat(row.get("aggregate_id")).as("aggregate_id = id середовища").isNotNull();
        assertThat(row.get("published_at")).as("публікує relay, не writer").isNull();
    }

    @Test
    void payload_carries_environment_revision_and_delta_of_changed_flag() throws Exception {
        String env = seedEnvironmentWithConfiguredFlag("write-b");

        String payload = jdbc.queryForObject(
            "SELECT payload::text FROM outbox WHERE partition_key = ?", String.class, env);
        JsonNode json = objectMapper.readTree(payload);

        assertThat(json.get("environmentKey").asString()).isEqualTo(env);
        assertThat(json.get("revision").asLong()).isEqualTo(1);
        // дельта — proto ConfigDelta через JsonFormat: lowerCamel-поля
        JsonNode upserted = json.get("delta").get("upsertedFlags");
        assertThat(upserted).isNotNull();
        assertThat(upserted.get(0).get("key").asString()).isEqualTo(flagKeyFor(env));
    }

    @Test
    void rolled_back_transaction_leaves_no_outbox_row() throws Exception {
        String env = seedEnvironmentWithConfiguredFlag("write-c");
        long before = outboxCount(env);

        // подія летить усередині транзакції, яку ми відкочуємо — BEFORE_COMMIT
        // слухач уже записав рядок, але відкат мусить забрати і його
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new ConfigChangedEvent(env, 1));
            status.setRollbackOnly();
        });

        assertThat(outboxCount(env))
            .as("відкат транзакції = відкат outbox-рядка")
            .isEqualTo(before);
    }

    @Test
    void every_change_appends_its_own_row_in_order() throws Exception {
        String env = seedEnvironmentWithConfiguredFlag("write-d");
        toggle(env, false);
        toggle(env, true);

        List<Long> revisions = jdbc.queryForList("""
            SELECT (payload ->> 'revision')::bigint FROM outbox
            WHERE partition_key = ? ORDER BY created_at
            """, Long.class, env);

        assertThat(revisions).containsExactly(1L, 2L, 3L);
    }
}
