package io.praporets.controlplane.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Середовище ({@code dev}/{@code staging}/{@code prod}) — таблиця {@code environment}.
 *
 * <p><b>Мапінг (твоя робота):</b> {@code @Entity}; id — UUID, генерується Hibernate
 * ({@code @UuidGenerator}); {@code key} — unique за схемою; {@code revision} —
 * монотонний лічильник, який інкрементуватиме application-шар (01g), тут просто
 * колонка; {@code created_at} — {@code @CreationTimestamp}.
 *
 * <p>{@code @Version} тут НЕ потрібен: environment не редагується конкурентно
 * (revision — не version, це доменний лічильник змін конфігурації).
 */
public class Environment {

    private UUID id;
    private String key;
    private String name;
    private long revision;
    private Instant createdAt;

    protected Environment() {
    }

    public Environment(String key, String name) {
        this.key = key;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public long getRevision() {
        return revision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
