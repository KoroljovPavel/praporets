package io.praporets.controlplane.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Один запис журналу ревізій середовища — таблиця {@code revision_log}.
 * Append-only: записи ніколи не оновлюються і не видаляються — з них
 * збираються дельти для edge і стан для rollback. {@code payload} — довільне
 * JSON-дерево ({@link JsonNode}), бо його форма залежить від changeType.
 *
 * <p>Унікальність {@code (environment_id, revision)} тримає схема БД —
 * окремої анотації не треба ({@code ddl-auto: validate}).
 */
@Entity
@Table(name = "revision_log")
public class RevisionLogEntry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Environment environment;
    private long revision;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, name = "change_type")
    private ChangeType changeType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private JsonNode payload;
    @CreationTimestamp
    private Instant createdAt;

    protected RevisionLogEntry() {
    }

    public RevisionLogEntry(Environment environment, long revision, ChangeType changeType, JsonNode payload) {
        this.environment = environment;
        this.revision = revision;
        this.changeType = changeType;
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public long getRevision() {
        return revision;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
