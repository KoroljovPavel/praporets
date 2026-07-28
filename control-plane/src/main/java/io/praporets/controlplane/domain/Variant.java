package io.praporets.controlplane.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Варіант значення флага — таблиця {@code variant}.
 *
 * <p><b>Мапінг (твоя робота):</b> {@code @Entity}, id — {@code @UuidGenerator};
 * {@code flag} — {@code @ManyToOne(fetch = LAZY)} (P2!); {@code value} — колонка
 * JSONB: рядок із довільним JSON ({@code "true"}, {@code "\"blue\""}), мапиться
 * як {@code String} + {@code @JdbcTypeCode(SqlTypes.JSON)} — тест
 * {@code jsonb_columns_are_real_jsonb_not_text} перевірить, що в БД справді jsonb.
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
