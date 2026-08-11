package io.praporets.controlplane.domain;

/**
 * Тип значення флага (колонка {@code flag.value_type}, VARCHAR(16)).
 * Зберігається як рядок — {@code @Enumerated(STRING)} обов'язково:
 * ORDINAL прив'язав би дані до порядку констант.
 */
public enum ValueType {
    BOOLEAN, STRING, NUMBER, JSON
}
