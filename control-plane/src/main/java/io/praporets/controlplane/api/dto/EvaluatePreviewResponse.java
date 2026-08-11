package io.praporets.controlplane.api.dto;

import io.praporets.core.evaluation.Reason;
import tools.jackson.databind.JsonNode;

/**
 * Відповідь preview. {@link Reason} серіалізується ім'ям константи
 * ({@code "RULE_MATCH"}).
 *
 * @param variantKey {@code null} лише при {@code FLAG_NOT_FOUND}
 * @param value      розпарсене JSON-значення варіанта; {@code null}, коли
 *                   ядро не повернуло значення (зокрема при
 *                   {@code FLAG_NOT_FOUND})
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
