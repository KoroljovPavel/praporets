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
 * Рядок таблиці {@code outbox} — подія, що чекає на доставку в Kafka.
 * Id генерується в застосунку (у конструкторі), payload — готовий
 * JSON-рядок у JSONB-колонці; {@code publishedAt == null} означає
 * «ще не в Kafka», relay ставить час після ack брокера.
 *
 * <p>Без {@code @Version}: за рядок конкурують лише relay-і різних реплік,
 * і їх розводить {@code FOR UPDATE SKIP LOCKED}, а не оптимістичний лок.
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
