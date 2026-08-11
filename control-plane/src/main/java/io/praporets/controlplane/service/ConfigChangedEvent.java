package io.praporets.controlplane.service;

/**
 * Подія «в середовищі відбулася зміна конфігурації з новою ревізією».
 *
 * <p>Публікується {@link RevisionRecorder#recordChange} <b>усередині</b>
 * транзакції через {@code ApplicationEventPublisher}; її ловить
 * {@code OutboxWriter} у фазі {@code BEFORE_COMMIT} і пише подію в outbox тією
 * ж транзакцією. Далі шлях до gRPC-стрімів іде через Kafka-топік
 * {@code praporets.flag.changes.v1} і fan-out-консюмер кожної репліки.
 *
 * <p>Одна транзакція може дати кілька подій (rollback робить серію
 * upsert-ів) — кожна зі своєю ревізією.
 *
 * @param environmentKey ключ середовища
 * @param revision       щойно присвоєна ревізія
 */
public record ConfigChangedEvent(String environmentKey, long revision) {
}
