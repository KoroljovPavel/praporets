package io.praporets.controlplane.api.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Варіант флага в запитах і відповідях. {@code value} — довільний JSON
 * ({@code true}, {@code "blue"}, {@code {"limit": 10}}); відповідність
 * {@code valueType} флага перевіряє сервіс (CP-02), не Bean Validation.
 */
public record VariantDto(
        @NotBlank @Size(max = 64) String key,
        @NotNull JsonNode value
) {
}
