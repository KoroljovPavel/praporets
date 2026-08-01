package io.praporets.analytics.consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакційна обробка однієї події: ідемпотентний маркер + інкремент
 * агрегату В ОДНІЙ транзакції (G4). Виділено з листенера навмисно (G3):
 * листенер ack-ає ПІСЛЯ повернення звідси, тобто після коміту — ack
 * всередині транзакційного методу комітив би офсет ДО даних.
 *
 * <p><b>Залежності для інжекту:</b> {@link EvaluationAggRepository}.
 *
 * <p><b>{@code process} (твоя робота):</b>
 * <ol>
 *   <li>{@code markProcessed(evaluationId)} → {@code false} — дублікат:
 *       тихий return (камінь #3: дублікат — це УСПІХ, не виняток);</li>
 *   <li>{@code variantKey == null} (FLAG_NOT_FOUND) — return: подія
 *       оброблена, але агрегату для неї немає;</li>
 *   <li>вікно: {@code occurredAt.truncatedTo(ChronoUnit.MINUTES)} —
 *       EVENT time (G6, камінь #4);</li>
 *   <li>{@code incrementAggregate(...)}.</li>
 * </ol>
 * Винятки (БД впала тощо) НЕ ловити — транзакція відкотиться цілком,
 * листенер не ack-не, error handler зробить ретраї → DLT (G5).
 */
@Service
public class EvaluationProcessor {

    @Transactional
    public void process(EvaluationEventPayload payload) {
        throw new UnsupportedOperationException("03d-1: твоя реалізація");
    }
}
