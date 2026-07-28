package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.CreateEnvironmentRequest;
import io.praporets.controlplane.api.dto.EnvironmentResponse;
import io.praporets.controlplane.api.dto.RevisionResponse;
import io.praporets.controlplane.api.dto.RollbackRequest;
import io.praporets.controlplane.api.dto.RollbackResponse;
import io.praporets.controlplane.service.EnvironmentService;
import io.praporets.controlplane.service.RollbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {@code /api/v1/environments} (CP-01) + журнал ревізій.
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @RestController},
 * {@code @RequestMapping("/api/v1/environments")}, method-анотації,
 * {@code @Valid} на тілах, {@code @RequestHeader(name = "X-Actor",
 * defaultValue = "anonymous")} для actor (G7).
 */
@RestController
@RequestMapping("/api/v1/environments")
public class EnvironmentController {

    private final EnvironmentService environments;
    private final RollbackService rollbackService;

    public EnvironmentController(EnvironmentService environments, RollbackService rollbackService) {
        this.environments = environments;
        this.rollbackService = rollbackService;
    }

    /** {@code GET} → 200. */
    @GetMapping
    public List<EnvironmentResponse> list() {
        return environments.list();
    }

    /** {@code POST} → 201 + {@code Location: /api/v1/environments/{key}}. */
    @PostMapping
    public ResponseEntity<EnvironmentResponse> create(@RequestBody @Valid CreateEnvironmentRequest request,
                                                      @RequestHeader(name = "X-Actor", defaultValue = "anonymous") String actor) {
        EnvironmentResponse response = environments.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", "/api/v1/environments/" + response.key()).body(response);
    }

    /** {@code GET /{env}/revisions?limit=50} → 200, новіші перші. */
    @GetMapping("/{environmentKey}/revisions")
    public List<RevisionResponse> revisions(@PathVariable String environmentKey, @RequestParam(defaultValue = "50") int limit) {
        return environments.revisions(environmentKey, limit);
    }

    /**
     * {@code POST /{env}/rollback} (CP-06) → 200 | 404 (немає середовища) |
     * 400 ({@code toRevision} поза діапазоном, H6). Мутація → actor з X-Actor
     * (G7), як у create.
     */
    @PostMapping("/{env}/rollback")
    public RollbackResponse rollback(@PathVariable(name = "env") String environmentKey,
                                     @RequestBody @Valid RollbackRequest request,
                                     @RequestHeader(name = "X-Actor", defaultValue = "anonymous") String actor) {
        return rollbackService.rollback(environmentKey, request.toRevision(), actor);
    }
}
