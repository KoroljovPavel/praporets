package io.praporets.controlplane.api.dto;

import io.praporets.core.evaluation.Reason;
import tools.jackson.databind.JsonNode;

/**
 * Відповідь preview (H2). {@link Reason} серіалізується ім'ям константи
 * ({@code "RULE_MATCH"}) — рівно те, що пінять тести.
 *
 * @param variantKey {@code null} лише при {@code FLAG_NOT_FOUND}
 * @param value      розпарсене JSON-значення варіанта
 *                   ({@code jsonMapper.readTree(result.jsonValue())});
 *                   {@code null} при {@code FLAG_NOT_FOUND} і D6
 * @param ruleId     заданий лише коли спрацювало правило (див. матрицю
 *                   {@code EvaluationResult})
 * @param revision   поточна ревізія середовища — щоб оператор бачив,
 *                   ПРОТИ ЯКОГО стану рахувався preview
 */
public record EvaluatePreviewResponse(
        String flagKey,
        String variantKey,
        JsonNode value,
        Reason reason,
        String ruleId,
        long revision
) {
}
