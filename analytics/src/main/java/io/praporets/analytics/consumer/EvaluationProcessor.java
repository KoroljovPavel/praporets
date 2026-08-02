package io.praporets.analytics.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    private final MeterRegistry meterRegistry;
    private final EvaluationAggRepository evaluationAggRepository;

    public EvaluationProcessor(EvaluationAggRepository evaluationAggRepository, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.evaluationAggRepository = evaluationAggRepository;
    }

    @Transactional
    public void process(EvaluationEventPayload payload) {
        if (!evaluationAggRepository.markProcessed(payload.evaluationId()) || payload.variantKey() == null)
            return;

        Instant window = payload.occurredAt().truncatedTo(ChronoUnit.MINUTES);
        long uniqueDelta = evaluationAggRepository.markUserSeen(payload.environment(), payload.flagKey(), payload.variantKey(), window, payload.userKeyHash()) ? 1 : 0;
        evaluationAggRepository.incrementAggregate(payload.environment(), payload.flagKey(), payload.variantKey(), window, uniqueDelta);

        meterRegistry.counter("praporets_evaluations_total",
                Tags.of(
                    Tag.of("environment", payload.environment()),
                    Tag.of("flag", payload.flagKey()),
                    Tag.of("variant", payload.variantKey()),
                    Tag.of("reason", payload.reason())

                ))
            .increment();
    }
}
