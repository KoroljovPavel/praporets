package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.*;
import io.praporets.controlplane.service.EnvironmentService;
import io.praporets.controlplane.service.RollbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {@code /api/v1/environments}: CRUD середовищ, журнал ревізій і rollback.
 * Actor мутацій береться з заголовка {@code X-Actor}
 * (за замовчуванням {@code anonymous}).
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
     * {@code POST /{env}/rollback} → 200 | 404 (немає середовища) |
     * 400 ({@code toRevision} поза діапазоном).
     */
    @PostMapping("/{env}/rollback")
    public RollbackResponse rollback(@PathVariable(name = "env") String environmentKey,
                                     @RequestBody @Valid RollbackRequest request,
                                     @RequestHeader(name = "X-Actor", defaultValue = "anonymous") String actor) {
        return rollbackService.rollback(environmentKey, request.toRevision(), actor);
    }
}
