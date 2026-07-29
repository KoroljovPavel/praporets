package io.praporets.controlplane.common;

import io.praporets.controlplane.api.dto.FlagConfigResponse;
import io.praporets.controlplane.api.dto.SegmentResponse;
import io.praporets.controlplane.domain.RevisionLogEntry;
import tools.jackson.databind.json.JsonMapper;

public final class GroupingHelper {

    private GroupingHelper() {
    }

    public static GroupingKey extractGroupingKey(RevisionLogEntry revision, JsonMapper jsonMapper) {
        return switch (revision.getChangeType()) {
            case FLAG_CONFIG_UPDATED, FLAG_TOGGLED ->
                new GroupingKey(EntityType.FLAG, jsonMapper.treeToValue(revision.getPayload(), FlagConfigResponse.class).flagKey());
            case SEGMENT_UPDATED ->
                new GroupingKey(EntityType.SEGMENT, jsonMapper.treeToValue(revision.getPayload(), SegmentResponse.class).key());
        };
    }

    public record GroupingKey(EntityType kind, String key) {
    }

    public enum EntityType {
        SEGMENT,
        FLAG,
    }
}
