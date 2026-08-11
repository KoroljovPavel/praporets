package io.praporets.controlplane.service;

import io.praporets.controlplane.domain.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Спільні кроки транзакції публікації зміни: інкремент ревізії середовища →
 * запис у {@code revision_log} → запис у {@code audit_log} → публікація
 * {@link ConfigChangedEvent} (його ловить outbox-writer перед комітом).
 * Клас окремий, а не приватний метод сервісу, бо ці кроки спільні для всіх
 * сервісів, що змінюють конфігурацію.
 *
 * <p>НЕ відкриває власну транзакцію — методи викликаються зсередини
 * {@code @Transactional}-методів сервісів і джойняться до їхньої;
 * {@code REQUIRES_NEW} тут був би багом — ревізія без зміни при відкаті.
 *
 * <p><b>Конкуренція:</b> «прочитати Environment → setRevision(+1) → save»
 * дав би lost update між двома паралельними транзакціями, бо в Environment
 * немає {@code @Version} (свідомо). Рішення — песимістичне блокування рядка:
 * {@code EnvironmentRepository.findWithLockByKey} робить
 * {@code SELECT ... FOR UPDATE}, тож друга транзакція чекає на row lock, і
 * ревізії видаються строго послідовно.
 */
@Component
public class RevisionRecorder {

    private final AuditLogRepository auditLogRepository;
    private final RevisionLogRepository revisionLogRepository;
    private final EnvironmentRepository environmentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public RevisionRecorder(AuditLogRepository auditLogRepository, RevisionLogRepository revisionLogRepository,
                            EnvironmentRepository environmentRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.auditLogRepository = auditLogRepository;
        this.revisionLogRepository = revisionLogRepository;
        this.environmentRepository = environmentRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Інкрементує ревізію середовища, додає запис у журнал ревізій і
     * публікує {@link ConfigChangedEvent} для outbox.
     *
     * @param environmentKey ключ середовища (має існувати)
     * @param changeType     що сталося
     * @param payload        повний стан зміненої сутності в канонічній
     *                       JSON-формі — з нього збираються дельти для edge
     *                       і відновлюється стан при rollback
     * @return нова (щойно присвоєна) ревізія середовища
     * @throws NotFoundException якщо середовища немає
     */
    public long recordChange(String environmentKey, ChangeType changeType, JsonNode payload) {
        Environment environment = environmentRepository.findWithLockByKey(environmentKey)
            .orElseThrow(() -> new NotFoundException("Entity with key [" + environmentKey + "] not found"));

        long revision = environment.incrementRevision();

        revisionLogRepository.save(new RevisionLogEntry(environment, revision, changeType, payload));

        applicationEventPublisher.publishEvent(new ConfigChangedEvent(environmentKey, revision));

        return revision;
    }

    /**
     * Додає запис аудиту. {@code before} — {@code null} при створенні,
     * {@code after} — стан після зміни.
     */
    public void audit(String actor, String action, String entityType, UUID entityId,
                      JsonNode before, JsonNode after) {
        auditLogRepository.save(new AuditLogEntry(actor, action, entityType, entityId, before, after));
    }
}
