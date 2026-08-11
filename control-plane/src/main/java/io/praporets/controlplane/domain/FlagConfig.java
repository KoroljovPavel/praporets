package io.praporets.controlplane.domain;

import io.praporets.core.model.Rollout;
import io.praporets.core.model.Rule;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Конфігурація флага в конкретному середовищі — таблиця {@code flag_config}.
 * {@code rules} і {@code rollout} — JSONB-колонки, що мапляться напряму в
 * core-records ({@code List<Rule>}, {@code Rollout}); {@code rollout}
 * nullable — флаг без відсоткового розподілу є нормою. {@code version} —
 * оптимістичний лок для If-Match у API.
 */
@Entity
@Table(name = "flag_config")
public class FlagConfig {

    @Id @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Flag flag;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Environment environment;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, length = 64)
    private String defaultVariant;
    @Column(nullable = false, length = 64)
    private String offVariant;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '[]'")
    private List<Rule> rules = List.of();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private Rollout rollout;
    @Version
    private long version;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FlagConfig() {
    }

    public FlagConfig(Flag flag, Environment environment, String defaultVariant, String offVariant) {
        this.flag = flag;
        this.environment = environment;
        this.defaultVariant = defaultVariant;
        this.offVariant = offVariant;
    }

    public UUID getId() {
        return id;
    }

    public Flag getFlag() {
        return flag;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultVariant() {
        return defaultVariant;
    }

    public void setDefaultVariant(String defaultVariant) {
        this.defaultVariant = defaultVariant;
    }

    public String getOffVariant() {
        return offVariant;
    }

    public void setOffVariant(String offVariant) {
        this.offVariant = offVariant;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public Rollout getRollout() {
        return rollout;
    }

    public void setRollout(Rollout rollout) {
        this.rollout = rollout;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
