package io.praporets.analytics.consumer;

import java.time.Instant;
import java.util.UUID;

/**
 * JSON-контракт evaluation-події, яку публікує flag-edge. Дзеркало
 * {@code io.praporets.edge.events.EvaluationEvent} — свій клас, бо модулі
 * незалежні, а контракт і так пінований тестами обох сторін.
 *
 * <p>Jackson 3 десеріалізує record-и і {@code Instant} (ISO-8601) з коробки;
 * невідомі поля ігноруються (дефолт Jackson 3) — сумісні в межах v1
 * додавання полів не ламають консюмер.
 *
 * @param evaluationId UUIDv7 — ключ ідемпотентності
 * @param occurredAt   момент обчислення (event time) — хвилинне вікно
 *                     агрегації рахується від нього, не від часу обробки
 * @param variantKey   {@code null} для FLAG_NOT_FOUND — така подія
 *                     маркується обробленою, але в агрегат не йде
 * @param ruleId       може бути {@code null}; агрегату байдуже
 * @param userKeyHash  хеш ключа користувача — PII в топік не потрапляє
 */
public record EvaluationEventPayload(
    UUID evaluationId,
    Instant occurredAt,
    String environment,
    String flagKey,
    String variantKey,
    String reason,
    String ruleId,
    long revision,
    String userKeyHash,
    String edgeInstanceId) {
}
