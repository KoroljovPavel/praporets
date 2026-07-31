package io.praporets.controlplane.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Рядок таблиці {@code outbox} (V2, спека §5.1) — подія, що чекає на
 * доставку в Kafka.
 *
 * <p><b>Колонки → поля</b> (маппиш у своєму стилі, як в 01f):
 * <ul>
 *   <li>{@code id} UUID PK — генеруй в застосунку (UUIDv7/randomUUID у
 *       конструкторі), не в БД: ентіті з призначеним id;</li>
 *   <li>{@code aggregate_id} UUID — id середовища, до якого належить зміна;</li>
 *   <li>{@code topic} varchar(128) — {@code KafkaTopics.FLAG_CHANGES};</li>
 *   <li>{@code partition_key} varchar(128) — environmentKey (K3);</li>
 *   <li>{@code payload} JSONB — подія з K3 як JSON-рядок:
 *       {@code @JdbcTypeCode(SqlTypes.JSON)}, як у flag_config з 01f;</li>
 *   <li>{@code created_at} timestamptz NOT NULL — момент створення
 *       (застосунком, {@code Instant.now()});</li>
 *   <li>{@code published_at} timestamptz NULL — null = ще не в Kafka;
 *       relay ставить час після ack брокера.</li>
 * </ul>
 *
 * <p>Без {@code @Version}: за рядок конкурують лише relay-і, і їх розводить
 * {@code FOR UPDATE SKIP LOCKED}, а не оптимістичний лок.
 */
@Entity
@Table(name = "outbox")
public class OutboxEntry {

    @Id
    private UUID id;

    private UUID aggregateId;

    @Column(length = 128)
    private String topic;

    @Column(length = 128)
    private String partitionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    String payload;

    @CreationTimestamp
    Instant createdAt;

    Instant publishedAt;

    public OutboxEntry(UUID aggregateId, String topic, String partitionKey, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
    }

    public OutboxEntry() {
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public void markPublished(Instant at) {
        if (publishedAt != null) {
            throw new IllegalStateException("already published at " + publishedAt);
        }
        this.publishedAt = at;
    }
}
