package io.praporets.controlplane.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Тіло {@code POST /api/v1/environments/{env}/rollback}.
 * Діапазон {@code 1 ≤ toRevision ≤ поточна} перевіряє сервіс
 * ({@code DomainValidationException} → 400) — Bean Validation тут ловить
 * лише відсутність поля.
 */
public record RollbackRequest(
        @NotNull Long toRevision
) {
}
