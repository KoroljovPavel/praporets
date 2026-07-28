package io.praporets.controlplane.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Середовище у відповідях API. {@code id} потрібен клієнту для запитів до /audit. */
public record EnvironmentResponse(
        UUID id,
        String key,
        String name,
        long revision,
        Instant createdAt
) {
}
