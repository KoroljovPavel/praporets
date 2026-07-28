package io.praporets.controlplane.api.dto;

import tools.jackson.databind.JsonNode;
import io.praporets.controlplane.domain.ChangeType;

import java.time.Instant;

/** Запис журналу ревізій у {@code GET .../revisions}. */
public record RevisionResponse(
        long revision,
        ChangeType changeType,
        JsonNode payload,
        Instant createdAt
) {
}
