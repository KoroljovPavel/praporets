package io.praporets.controlplane.api.dto;

import io.praporets.core.model.Rollout;
import io.praporets.core.model.Rule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Конфігурація флага в середовищі. Ця сама форма (через
 * {@code ObjectMapper.valueToTree}) їде в {@code revision_log.payload} — G8.
 */
public record FlagConfigResponse(
        UUID id,
        String flagKey,
        String environmentKey,
        boolean enabled,
        String defaultVariant,
        String offVariant,
        List<Rule> rules,
        Rollout rollout,
        long version,
        Instant updatedAt
) {
}
