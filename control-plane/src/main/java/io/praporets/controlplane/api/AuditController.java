package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.AuditEntryResponse;
import io.praporets.controlplane.service.AuditQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/audit?entityId=&limit=50} — читання журналу аудиту сутності.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    /** {@code GET ?entityId=&limit=} → 200, новіші перші. */
    @GetMapping
    public List<AuditEntryResponse> byEntity(@RequestParam UUID entityId, @RequestParam(defaultValue = "50") int limit) {
        return audit.byEntityId(entityId, limit);
    }
}
