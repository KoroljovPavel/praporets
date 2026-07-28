package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.SegmentResponse;
import io.praporets.controlplane.api.dto.UpsertSegmentRequest;
import io.praporets.controlplane.service.SegmentService;
import io.praporets.controlplane.service.Upserted;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {@code /api/v1/environments/{env}/segments} (CP-04).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @RestController} + анотації.
 * PUT відповідає 201 при створенні і 200 при заміні —
 * {@code Upserted.created()} каже, що сталося.
 */
@RestController
@RequestMapping("/api/v1/environments/{env}/segments")
public class SegmentController {

    private final SegmentService segments;

    public SegmentController(SegmentService segments) {
        this.segments = segments;
    }

    /** {@code GET} → 200. */
    @GetMapping
    public List<SegmentResponse> list(@PathVariable(name = "env") String environmentKey) {
        return segments.list(environmentKey);
    }

    /** {@code PUT /{key}} → 201 (створено) | 200 (замінено). */
    @PutMapping("/{key}")
    public ResponseEntity<SegmentResponse> upsert(@PathVariable(name = "env") String environmentKey,
                                                  @PathVariable(name = "key") String segmentKey,
                                                  @RequestBody @Valid UpsertSegmentRequest request,
                                                  @RequestHeader(name = "X-Actor", defaultValue = "anonymous") String actor) {

        Upserted<SegmentResponse> response = segments.upsert(environmentKey, segmentKey, request, actor);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK).body(response.body());
    }
}
