package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.AuditEntryResponse;
import io.praporets.controlplane.domain.AuditLogRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Читання audit_log для {@code GET /api/v1/audit} (запис робить
 * {@link RevisionRecorder#audit} у транзакціях сервісів).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @Service},
 * {@code @Transactional(readOnly = true)}.
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    public AuditQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Історія однієї сутності за її UUID, новіші перші. */
    public List<AuditEntryResponse> byEntityId(UUID entityId, int limit) {
        return auditLogRepository.findByEntityIdOrderByIdDesc(entityId, Limit.of(limit)).stream().map(l -> new AuditEntryResponse(
            l.getId(), l.getActor(), l.getAction(), l.getEntityType(), l.getEntityId(), l.getBefore(), l.getAfter(), l.getCreatedAt()
        )).toList();
    }
}
