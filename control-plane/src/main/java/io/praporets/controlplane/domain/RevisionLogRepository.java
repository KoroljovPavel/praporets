package io.praporets.controlplane.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RevisionLogRepository extends JpaRepository<RevisionLogEntry, Long> {

    /** Останні ревізії середовища, новіші перші. {@link Limit} — рідний спосіб Spring Data обмежити вибірку. */
    List<RevisionLogEntry> findByEnvironmentKeyOrderByRevisionDesc(String environmentKey, Limit limit);
}
