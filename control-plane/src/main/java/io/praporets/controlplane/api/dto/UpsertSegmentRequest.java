package io.praporets.controlplane.api.dto;

import io.praporets.core.model.Clause;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Тіло {@code PUT /api/v1/environments/{env}/segments/{key}} — повна заміна умов.
 * {@link Clause} — record із ядра (P1/G2): невалідна умова не пройде
 * десеріалізацію (канонічний конструктор кине IAE → 400).
 */
public record UpsertSegmentRequest(
        @NotEmpty List<Clause> conditions
) {
}
