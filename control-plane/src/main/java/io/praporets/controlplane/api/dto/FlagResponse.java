package io.praporets.controlplane.api.dto;

import io.praporets.controlplane.domain.ValueType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Флаг у відповідях API. {@code version} клієнт кладе в {@code If-Match} при PATCH. */
public record FlagResponse(
        UUID id,
        String key,
        String name,
        String description,
        ValueType valueType,
        boolean archived,
        long version,
        Instant createdAt,
        List<VariantDto> variants
) {
}
