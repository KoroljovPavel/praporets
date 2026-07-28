package io.praporets.controlplane.service;

import tools.jackson.databind.JsonNode;
import io.praporets.controlplane.domain.ChangeType;

import java.util.UUID;

/**
 * Спільні кроки 2–4 транзакції публікації зміни (спека 7.3):
 * інкремент ревізії середовища → запис у {@code revision_log} → запис у
 * {@code audit_log}. Крок 5 (outbox) приїде сюди в 03a — це і є та сама
 * транзакція, тому клас окремий, а не приватний метод сервісу.
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @Component}; НЕ відкриває власну
 * транзакцію — методи викликаються зсередини {@code @Transactional}-методів
 * сервісів і мають джойнитись до їхньої (за замовчуванням так і буде;
 * {@code REQUIRES_NEW} тут був би багом — ревізія без зміни при відкаті).
 *
 * <p><b>Конкуренція (підводний камінь #2 спеки кроку):</b> «прочитати
 * Environment → setRevision(+1) → save» дає lost update між двома
 * паралельними транзакціями, бо в Environment немає {@code @Version}
 * (свідомо). Рішення — песимістичне блокування рядка:
 * {@code @Lock(LockModeType.PESSIMISTIC_WRITE)} на новому методі
 * {@code EnvironmentRepository.findWithLockByKey(...)} (згенерує
 * {@code SELECT ... FOR UPDATE}); друга транзакція чекатиме на row lock.
 * Знадобиться і мутатор ревізії на Environment — краще не голий setter,
 * а {@code long incrementRevision()} (повертає нове значення).
 */
public class RevisionRecorder {

    /**
     * Інкрементує ревізію середовища і додає запис у журнал ревізій.
     *
     * @param environmentKey ключ середовища (має існувати)
     * @param changeType     що сталося
     * @param payload        дельта в канонічній JSON-формі (G8) — те, що в 02x
     *                       поїде на edge
     * @return нова (щойно присвоєна) ревізія середовища
     * @throws NotFoundException якщо середовища немає
     */
    public long recordChange(String environmentKey, ChangeType changeType, JsonNode payload) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /**
     * Додає запис аудиту (CP-05). {@code before} — {@code null} при створенні,
     * {@code after} — стан після зміни.
     */
    public void audit(String actor, String action, String entityType, UUID entityId,
                      JsonNode before, JsonNode after) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
