package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.CreateFlagRequest;
import io.praporets.controlplane.api.dto.FlagResponse;
import io.praporets.controlplane.api.dto.UpdateFlagRequest;
import io.praporets.controlplane.service.FlagService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/v1/flags}: CRUD глобального довідника флагів. Оновлення
 * захищене обов'язковим {@code If-Match} (відсутність заголовка Spring сам
 * перетворює на 400 через {@code MissingRequestHeaderException}).
 */
@RestController
@RequestMapping("/api/v1/flags")
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    /** {@code GET ?archived=&page=&size=} → 200 PagedModel. {@code archived} опційний. */
    @GetMapping
    public PagedModel<FlagResponse> list(@RequestParam(name = "archived", required = false) Boolean archived, Pageable pageable) {
        return new PagedModel<>(flagService.list(archived, pageable));
    }

    /** {@code GET /{key}} → 200 | 404. */
    @GetMapping("/{key}")
    public FlagResponse get(@PathVariable String key) {
        return flagService.get(key);
    }

    /** {@code POST} → 201 + {@code Location: /api/v1/flags/{key}}. */
    @PostMapping
    public ResponseEntity<FlagResponse> create(@RequestBody @Valid CreateFlagRequest request,
                                               @RequestHeader(name = "X-Actor", defaultValue = "anonymous") String actor) {
        FlagResponse response = flagService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", "/api/v1/flags/" + response.key()).body(response);
    }

    /** {@code PATCH /{key}} + {@code If-Match} → 200 | 409 (stale) | 400 (без заголовка). */
    @PatchMapping("/{key}")
    public FlagResponse update(@PathVariable String key,
                               @RequestHeader("If-Match") long ifMatch,
                               @RequestBody @Valid UpdateFlagRequest request,
                               @RequestHeader(name = "X-Actor", defaultValue = "anonymous") String actor) {
        return flagService.update(key, ifMatch, request, actor);
    }

    /** {@code DELETE /{key}} → 204; archive, не hard delete. */
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> archive(@PathVariable String key, @RequestHeader(name = "X-Actor", defaultValue = "anonymous") String actor) {
        flagService.archive(key, actor);
        return ResponseEntity.noContent().build();
    }
}
