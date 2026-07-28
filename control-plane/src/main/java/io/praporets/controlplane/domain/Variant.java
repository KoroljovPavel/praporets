package io.praporets.controlplane.domain;

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
public class Variant {

    private UUID id;
    private Flag flag;
    private String key;
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
