package io.praporets.controlplane.domain;

import io.praporets.core.model.Clause;
import java.util.List;
import java.util.UUID;

/**
 * Сегмент користувачів у межах середовища — таблиця {@code segment}.
 *
 * <p><b>Мапінг (твоя робота):</b> {@code @Entity}; {@code environment} —
 * {@code @ManyToOne(fetch = LAZY)}; {@code conditions} — JSONB напряму в
 * {@code List<Clause>} із ядра (P1): {@code @JdbcTypeCode(SqlTypes.JSON)}.
 * Jackson серіалізує records за іменами компонентів — це і є канонічна
 * JSON-форма умов. Десеріалізація йде через канонічний конструктор, тож
 * валідації ядра спрацюють і при читанні з БД. {@code version} — {@code @Version} (P3).
 */
public class Segment {

    private UUID id;
    private Environment environment;
    private String key;
    private List<Clause> conditions;
    private long version;

    protected Segment() {
    }

    public Segment(Environment environment, String key, List<Clause> conditions) {
        this.environment = environment;
        this.key = key;
        this.conditions = conditions;
    }

    public UUID getId() {
        return id;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public String getKey() {
        return key;
    }

    public List<Clause> getConditions() {
        return conditions;
    }

    public void setConditions(List<Clause> conditions) {
        this.conditions = conditions;
    }

    public long getVersion() {
        return version;
    }
}
