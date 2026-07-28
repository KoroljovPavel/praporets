package io.praporets.controlplane.api.dto;

import io.praporets.controlplane.domain.ValueType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Тіло {@code POST /api/v1/flags}: флаг створюється разом із варіантами
 * (флаг без варіантів не має сенсу — його нічим обчислювати).
 *
 * <p>Регексп ключа — з CP-02: сегменти з малих літер/цифр, розділені
 * {@code .} або {@code -}, без underscore.
 */
public record CreateFlagRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[a-z0-9]+([.-][a-z0-9]+)*$") String key,
        @NotBlank @Size(max = 256) String name,
        String description,
        @NotNull ValueType valueType,
        @NotEmpty List<@Valid VariantDto> variants
) {
}
