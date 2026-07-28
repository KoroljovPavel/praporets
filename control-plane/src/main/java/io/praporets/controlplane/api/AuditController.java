package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.AuditEntryResponse;
import io.praporets.controlplane.service.AuditQueryService;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/audit?entityId=&limit=50} (CP-05, читання).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @RestController} + анотації.
 */
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    /** {@code GET ?entityId=&limit=} → 200, новіші перші. */
    public List<AuditEntryResponse> byEntity(UUID entityId, int limit) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
