package io.praporets.analytics.consumer;

import java.time.Instant;
import java.util.UUID;

/**
 * JSON-контракт evaluation-події (спека §6.4, пише edge у 03c). Дзеркало
 * {@code io.praporets.edge.events.EvaluationEvent} — свій клас, бо модулі
 * незалежні, а контракт і так пінований тестами обох сторін.
 *
 * <p>Jackson 3 десеріалізує record-и і {@code Instant} (ISO-8601) з коробки;
 * невідомі поля ігноруються (дефолт Jackson 3) — v1-сумісні додавання полів
 * не ламають консюмер.
 *
 * @param evaluationId UUIDv7 — ключ ідемпотентності (A-04)
 * @param occurredAt   момент обчислення — ВІКНО рахується від нього (G6)
 * @param variantKey   {@code null} для FLAG_NOT_FOUND — така подія
 *                     маркується обробленою, але в агрегат не йде
 * @param ruleId       може бути {@code null}; агрегату байдуже
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
