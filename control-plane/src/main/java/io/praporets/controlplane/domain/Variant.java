package io.praporets.controlplane.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Варіант значення флага — таблиця {@code variant}. {@code value} — JSONB-
 * колонка з довільним JSON ({@code "true"}, {@code "\"blue\""}), у Java
 * мапиться як {@code String}: парсити його тут нема кому, споживачі
 * (proto-мапер, core) працюють із канонічним JSON-рядком.
 */
@Entity
@Table(name = "variant")
public class Variant {

    @Id @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Flag flag;
    @Column(nullable = false, length = 64)
    private String key;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private String value;

    protected Variant() {
    }

    public Variant(String key, String value) {
        this.key = key;
        this.value = value;
    }

    void setFlag(Flag flag) {
        this.flag = flag;
    }

    public UUID getId() {
        return id;
    }

    public Flag getFlag() {
        return flag;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
