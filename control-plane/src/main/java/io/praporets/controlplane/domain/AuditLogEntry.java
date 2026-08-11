package io.praporets.controlplane.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Запис аудиту «хто, коли, що змінив» — таблиця {@code audit_log}.
 * Append-only, без зв'язків на інші сутності: {@code entityId} — просто UUID,
 * бо аудит має переживати навіть видалення сутності, на яку вказує.
 *
 * <p>{@code before}/{@code after} — nullable JSONB-знімки стану:
 * {@code before} порожній при CREATE; при ARCHIVE зберігається стан після
 * архівації.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 128)
    private String actor;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(nullable = false, length = 64)
    private String entityType;
    @Column(nullable = false)
    private UUID entityId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "JSONB")
    private JsonNode before;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "JSONB")
    private JsonNode after;
    @CreationTimestamp
    private Instant createdAt;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(String actor, String action, String entityType, UUID entityId,
                         JsonNode before, JsonNode after) {
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.before = before;
        this.after = after;
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public JsonNode getBefore() {
        return before;
    }

    public JsonNode getAfter() {
        return after;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
