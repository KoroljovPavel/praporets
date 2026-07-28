package io.praporets.controlplane.api.dto;

import io.praporets.core.model.EvaluationContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Тіло {@code POST /api/v1/evaluate-preview} (CP-11). Імена полів запинені
 * тестами: {@code environment} (НЕ environmentKey), {@code flagKey},
 * {@code context}.
 *
 * <p>{@code context} — прямо core-record {@link EvaluationContext} (той самий
 * прийом, що G2 з Rule/Rollout): JSON {@code {"userKey": "...",
 * "attributes": {...}}} десеріалізується за іменами компонентів, а канонічний
 * конструктор ядра кидає IAE на blank userKey → 400 безкоштовно.
 */
public record EvaluatePreviewRequest(
        @NotBlank String environment,
        @NotBlank String flagKey,
        @NotNull EvaluationContext context
) {
}
