package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.AuditEntryResponse;

import java.util.List;
import java.util.UUID;

/**
 * Читання audit_log для {@code GET /api/v1/audit} (запис робить
 * {@link RevisionRecorder#audit} у транзакціях сервісів).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @Service},
 * {@code @Transactional(readOnly = true)}.
 */
public class AuditQueryService {

    /** Історія однієї сутності за її UUID, новіші перші. */
    public List<AuditEntryResponse> byEntityId(UUID entityId, int limit) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
