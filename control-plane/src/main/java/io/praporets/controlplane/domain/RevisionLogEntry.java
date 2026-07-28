package io.praporets.controlplane.domain;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Один запис журналу ревізій середовища — таблиця {@code revision_log}.
 * Append-only: записи ніколи не оновлюються і не видаляються (дельта-вікно
 * для edge у 02x читає саме звідси).
 *
 * <p><b>Мапінг (твоя робота):</b>
 * <ul>
 *   <li>{@code @Entity}; id — {@code BIGSERIAL}, тобто НЕ {@code @UuidGenerator},
 *       а {@code @GeneratedValue(strategy = GenerationType.IDENTITY)} — значення
 *       генерує Postgres при INSERT (нове для тебе: Hibernate дізнається id
 *       лише після флашу);</li>
 *   <li>{@code environment} — {@code @ManyToOne(fetch = LAZY)} (P2);</li>
 *   <li>{@code changeType} — {@code @Enumerated(STRING)} (P5);</li>
 *   <li>{@code payload} — JSONB → {@link JsonNode} (та сама техніка
 *       {@code @JdbcTypeCode(SqlTypes.JSON)}, що в 01f, але тип — довільне
 *       JSON-дерево, бо форма payload залежить від changeType);</li>
 *   <li>{@code createdAt} — {@code @CreationTimestamp}.</li>
 * </ul>
 *
 * <p>Унікальність {@code (environment_id, revision)} тримає схема — окремої
 * анотації не треба ({@code ddl-auto: validate}).
 */
public class RevisionLogEntry {

    private Long id;
    private Environment environment;
    private long revision;
    private ChangeType changeType;
    private JsonNode payload;
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
