package io.praporets.controlplane.api.dto;

import jakarta.validation.constraints.NotNull;

/** Тіло {@code POST .../toggle}. Boxed {@code Boolean}, щоб відсутнє поле було 400, а не тихим false. */
public record ToggleRequest(
        @NotNull Boolean enabled
) {
}
