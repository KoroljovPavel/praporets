package io.praporets.controlplane.api.dto;

import io.praporets.core.model.Rollout;
import io.praporets.core.model.Rule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Тіло {@code PUT /api/v1/environments/{env}/flags/{key}/config} — повна заміна
 * конфігурації. {@link Rule}/{@link Rollout} — records ядра: rollout із
 * вагами, що не дають {@code Rollout.TOTAL_WEIGHT}, не десеріалізується → 400.
 *
 * <p>{@code rules == null} трактується як порожній список;
 * {@code rollout == null} — флаг без відсоткового розподілу (норма).
 */
public record UpsertFlagConfigRequest(
        boolean enabled,
        @NotBlank @Size(max = 64) String defaultVariant,
        @NotBlank @Size(max = 64) String offVariant,
        List<Rule> rules,
        Rollout rollout
) {
}
