package io.praporets.core.model;

import java.util.Objects;

/**
 * Одне з можливих значень флага: для boolean — {@code on}/{@code off},
 * для мультиваріантного — {@code control}/{@code treatment-a}/...
 *
 * <p>Значення завжди зберігається як JSON-рядок ({@code "true"}, {@code "\"blue\""},
 * {@code "{\"limit\":10}"}) — так само, як у proto-контракті. Core <b>не</b> валідує
 * синтаксис JSON — це відповідальність control-plane при збереженні конфігурації;
 * ядро лишається тотальним і трактує значення як непрозорий рядок.
 *
 * <p><b>Інваріанти:</b> {@code key} не blank; {@code jsonValue} не {@code null}.
 *
 * @param key       ключ варіанта (напр. {@code "treatment-a"})
 * @param jsonValue значення як JSON-рядок
 */
public record Variant(String key, String jsonValue) {

    /**
     * @throws NullPointerException     якщо {@code key} або {@code jsonValue} {@code null}
     * @throws IllegalArgumentException якщо {@code key} blank
     */
    public Variant {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(jsonValue, "jsonValue");
        if (key.isBlank()) throw new IllegalArgumentException("key must be non-blank");
    }
}
