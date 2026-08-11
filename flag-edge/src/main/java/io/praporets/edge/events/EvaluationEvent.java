package io.praporets.edge.events;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

/**
 * Подія одного обчислення флага — контракт для сервісу analytics.
 * Серіалізується Jackson-ом у JSON value Kafka-повідомлення; key повідомлення —
 * {@link #flagKey}, header — {@code schema-version: 1}.
 *
 * <p>{@code @RegisterForReflection} обов'язковий: Jackson у native-image
 * читає record рефлексією, і без реєстрації GraalVM мовчки викидає member-и —
 * подія серіалізується в {@code {}}.
 *
 * @param evaluationId   UUIDv7 ({@link Uuid7}) — ключ ідемпотентності в analytics
 * @param occurredAt     момент обчислення; ISO-8601 у JSON (дає Quarkus-Jackson)
 * @param environment    середовище цього edge
 * @param flagKey        обчислений флаг (завжди заданий)
 * @param variantKey     обраний варіант; {@code null} для FLAG_NOT_FOUND
 * @param reason         {@code EvaluationResult.reason().name()}
 * @param ruleId         правило, що спрацювало; {@code null} поза RULE_MATCH/ROLLOUT
 * @param revision       ревізія конфігурації, на якій рахували
 * @param userKeyHash    SHA-256 hex від userKey — сирий ключ (PII) не покидає edge
 * @param edgeInstanceId ідентифікатор інстанса edge (властивість
 *                       {@code praporets.edge.instance-id})
 */
@RegisterForReflection
public record EvaluationEvent(
    String evaluationId,
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
