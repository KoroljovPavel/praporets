package io.praporets.controlplane.service;

/**
 * Подія «в середовищі відбулася зміна конфігурації з новою ревізією».
 *
 * <p>Публікується {@link RevisionRecorder#recordChange} <b>усередині</b>
 * транзакції через {@code ApplicationEventPublisher}; слухач
 * ({@code grpc/ConfigChangePublisher}) підписаний з фазою
 * {@code AFTER_COMMIT} — тому подія про відкочену транзакцію ніколи не
 * долетить до edge-стрімів.
 *
 * <p>Одна транзакція може дати кілька подій (rollback робить серію upsert-ів) —
 * після коміту вони обробляються по черзі, кожна зі своєю ревізією.
 *
 * <p>У 03a/03b цей же шов обростає outbox → Kafka (CP-09/CP-10): подія
 * лишиться, але пуш у стріми піде через consumer топіка
 * {@code praporets.flag.changes.v1} у кожній репліці.
 *
 * @param environmentKey ключ середовища
 * @param revision       щойно присвоєна ревізія (див. {@code recordChange})
 */
public record ConfigChangedEvent(String environmentKey, long revision) {
}
