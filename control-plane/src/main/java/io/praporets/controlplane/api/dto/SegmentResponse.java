package io.praporets.controlplane.api.dto;

import io.praporets.core.model.Clause;

import java.util.List;
import java.util.UUID;

/** Сегмент у відповідях API. */
public record SegmentResponse(
        UUID id,
        String key,
        List<Clause> conditions,
        long version
) {
}
