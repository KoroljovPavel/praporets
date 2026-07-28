package io.praporets.controlplane.api.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Запис аудиту в {@code GET /api/v1/audit}. */
public record AuditEntryResponse(
        long id,
        String actor,
        String action,
        String entityType,
        UUID entityId,
        JsonNode before,
        JsonNode after,
        Instant createdAt
) {
}
