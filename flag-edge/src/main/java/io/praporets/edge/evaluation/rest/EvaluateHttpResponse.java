package io.praporets.edge.evaluation.rest;

/**
 * Відповідь {@code POST /api/v1/evaluate} — дзеркало gRPC
 * {@code EvaluateResponse} (V5). ГОТОВИЙ контракт.
 *
 * <p>{@code jsonValue} — сирий JSON-рядок варіанта, як зберігається;
 * парсити його — справа клієнта. {@code variantKey}/{@code jsonValue}/
 * {@code ruleId} можуть бути {@code null} (напр. {@code FLAG_NOT_FOUND}) —
 * на відміну від proto3 тут null легальний і чесніший за порожній рядок.
 *
 * @param reason ім'я core-{@code Reason} як рядок (RULE_MATCH, ROLLOUT, ...)
 */
public record EvaluateHttpResponse(
    String flagKey,
    String variantKey,
    String jsonValue,
    String reason,
    String ruleId,
    long revision) {
}
