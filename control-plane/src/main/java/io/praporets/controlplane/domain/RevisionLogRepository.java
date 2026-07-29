package io.praporets.controlplane.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RevisionLogRepository extends JpaRepository<RevisionLogEntry, Long> {

    /** Останні ревізії середовища, новіші перші. {@link Limit} — рідний спосіб Spring Data обмежити вибірку. */
    List<RevisionLogEntry> findByEnvironmentKeyOrderByRevisionDesc(String environmentKey, Limit limit);

    /** Для rollback (H4): усі записи до {@code toRevision} включно, новіші перші. */
    List<RevisionLogEntry> findByEnvironmentKeyAndRevisionLessThanEqualOrderByRevisionDesc(String environmentKey, long revision);

    /** Для склеєної дельти 02b (спека §7.4): записи СУВОРО ПІСЛЯ {@code revision}, старіші перші. */
    List<RevisionLogEntry> findByEnvironmentKeyAndRevisionGreaterThanOrderByRevisionAsc(String environmentKey, long revision);
}
