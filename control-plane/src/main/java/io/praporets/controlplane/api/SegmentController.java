package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.SegmentResponse;
import io.praporets.controlplane.api.dto.UpsertSegmentRequest;
import io.praporets.controlplane.service.SegmentService;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * {@code /api/v1/environments/{env}/segments} (CP-04).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @RestController} + анотації.
 * PUT відповідає 201 при створенні і 200 при заміні —
 * {@code Upserted.created()} каже, що сталося.
 */
public class SegmentController {

    private final SegmentService segments;

    public SegmentController(SegmentService segments) {
        this.segments = segments;
    }

    /** {@code GET} → 200. */
    public List<SegmentResponse> list(String environmentKey) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** {@code PUT /{key}} → 201 (створено) | 200 (замінено). */
    public ResponseEntity<SegmentResponse> upsert(String environmentKey, String segmentKey,
                                                  UpsertSegmentRequest request, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
