package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.CreateEnvironmentRequest;
import io.praporets.controlplane.api.dto.EnvironmentResponse;
import io.praporets.controlplane.api.dto.RevisionResponse;
import io.praporets.controlplane.service.EnvironmentService;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * {@code /api/v1/environments} (CP-01) + журнал ревізій.
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @RestController},
 * {@code @RequestMapping("/api/v1/environments")}, method-анотації,
 * {@code @Valid} на тілах, {@code @RequestHeader(name = "X-Actor",
 * defaultValue = "anonymous")} для actor (G7).
 */
public class EnvironmentController {

    private final EnvironmentService environments;

    public EnvironmentController(EnvironmentService environments) {
        this.environments = environments;
    }

    /** {@code GET} → 200. */
    public List<EnvironmentResponse> list() {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** {@code POST} → 201 + {@code Location: /api/v1/environments/{key}}. */
    public ResponseEntity<EnvironmentResponse> create(CreateEnvironmentRequest request, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** {@code GET /{env}/revisions?limit=50} → 200, новіші перші. */
    public List<RevisionResponse> revisions(String environmentKey, int limit) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
