package io.praporets.controlplane.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    /** Історія змін однієї сутності, новіші перші. */
    List<AuditLogEntry> findByEntityIdOrderByIdDesc(UUID entityId, Limit limit);
}
