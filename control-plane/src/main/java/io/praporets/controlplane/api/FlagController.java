package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.CreateFlagRequest;
import io.praporets.controlplane.api.dto.FlagResponse;
import io.praporets.controlplane.api.dto.UpdateFlagRequest;
import io.praporets.controlplane.service.FlagService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;

/**
 * {@code /api/v1/flags} (CP-02).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @RestController} + анотації.
 * If-Match — {@code @RequestHeader("If-Match") long} (обов'язковий: відсутність
 * заголовка Spring сам зробить 400 через {@code MissingRequestHeaderException}).
 * {@code Pageable} Boot збирає з {@code ?page=&size=} автоматично; у відповіді —
 * {@code new PagedModel<>(page)} (G9).
 */
public class FlagController {

    private final FlagService flags;

    public FlagController(FlagService flags) {
        this.flags = flags;
    }

    /** {@code GET ?archived=&page=&size=} → 200 PagedModel. {@code archived} опційний. */
    public PagedModel<FlagResponse> list(Boolean archived, Pageable pageable) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** {@code GET /{key}} → 200 | 404. */
    public FlagResponse get(String key) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** {@code POST} → 201 + {@code Location: /api/v1/flags/{key}}. */
    public ResponseEntity<FlagResponse> create(CreateFlagRequest request, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** {@code PATCH /{key}} + {@code If-Match} → 200 | 409 (stale) | 400 (без заголовка). */
    public FlagResponse update(String key, long ifMatch, UpdateFlagRequest request, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** {@code DELETE /{key}} → 204; archive, не hard delete. */
    public ResponseEntity<Void> archive(String key, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
