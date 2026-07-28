package io.praporets.controlplane.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Тіло {@code PATCH /api/v1/flags/{key}}. PATCH-семантика: {@code null} —
 * «не чіпати поле», тому без {@code @NotBlank}. Ключ і тип значення
 * незмінні після створення — їх тут немає.
 */
public record UpdateFlagRequest(
        @Size(max = 256) String name,
        String description
) {
}
