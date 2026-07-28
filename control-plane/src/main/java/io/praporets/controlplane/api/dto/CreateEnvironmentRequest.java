package io.praporets.controlplane.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Тіло {@code POST /api/v1/environments}. */
public record CreateEnvironmentRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z0-9-]+$") String key,
        @NotBlank @Size(max = 128) String name
) {
}
