package io.praporets.controlplane.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Флаг (глобальний, без прив'язки до середовища) — таблиця {@code flag}.
 * Агрегат: варіанти живуть і вмирають разом із флагом — {@code cascade = ALL,
 * orphanRemoval = true}, тож збереження флага каскадно зберігає варіанти.
 * {@code version} — оптимістичний лок для If-Match у API.
 */
@Entity
@Table(name = "flag")
public class Flag {

    @Id @UuidGenerator
    private UUID id;
    @Column(unique = true, nullable = false, length = 128)
    private String key;
    @Column(nullable = false, length = 256)
    private String name;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private ValueType valueType;
    @Column(nullable = false)
    private boolean archived;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    private long version;
    @OneToMany(mappedBy = "flag", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Variant> variants = new ArrayList<>();

    protected Flag() {
    }

    public Flag(String key, String name, String description, ValueType valueType) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.valueType = valueType;
    }

    /** Підтримує обидва кінці двонаправленого зв'язку узгодженими. */
    public void addVariant(Variant variant) {
        variants.add(variant);
        variant.setFlag(this);
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

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public List<Variant> getVariants() {
        return variants;
    }
}
