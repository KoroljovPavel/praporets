package io.praporets.controlplane.domain;

/**
 * Тип зміни в {@code revision_log} — що саме сталося в цій ревізії середовища.
 * Ревізію створюють лише зміни, видимі edge (G6); CRUD глобальних сутностей
 * (флаг, середовище) потрапляє тільки в audit_log.
 *
 * <p>У БД — {@code VARCHAR(32)}, мапиться як {@code @Enumerated(STRING)}
 * (те саме правило P5, що й для {@link ValueType}).
 */
public enum ChangeType {
    /** PUT конфігурації флага (створення або оновлення rules/rollout/варіантів за замовчуванням). */
    FLAG_CONFIG_UPDATED,
    /** Перемикання enabled через toggle-ендпоінт. */
    FLAG_TOGGLED,
    /** Upsert сегмента. */
    SEGMENT_UPDATED
}
