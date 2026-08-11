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
 * агрегату в ОДНІЙ транзакції. Виділено з листенера навмисно:
 * листенер ack-ає ПІСЛЯ повернення звідси, тобто після коміту — ack
 * всередині транзакційного методу комітив би офсет ДО даних.
 */
@Service
public class EvaluationProcessor {

    private final MeterRegistry meterRegistry;
    private final EvaluationAggRepository evaluationAggRepository;

    public EvaluationProcessor(EvaluationAggRepository evaluationAggRepository, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.evaluationAggRepository = evaluationAggRepository;
    }

    /**
     * Обробляє одну evaluation-подію:
     * <ol>
     *   <li>{@code markProcessed(evaluationId)} → {@code false} — дублікат:
     *       тихий return (дублікат — це успіх, не виняток);</li>
     *   <li>{@code variantKey == null} (FLAG_NOT_FOUND) — return: подія
     *       оброблена, але агрегату для неї немає;</li>
     *   <li>вікно — {@code occurredAt}, обрізаний до хвилини (event time,
     *       не час обробки);</li>
     *   <li>інкремент агрегату: unique-дельта — чи користувач у вікні
     *       вперше, rollout-дельта — лише flag-level ROLLOUT (reason
     *       {@code ROLLOUT} без {@code ruleId});</li>
     *   <li>інкремент лічильника {@code praporets_evaluations_total}
     *       (тільки для нових подій — до цього рядка дублікат не доходить).</li>
     * </ol>
     * Винятки (БД впала тощо) навмисно не ловляться — транзакція
     * відкочується цілком, листенер не ack-ає, error handler робить
     * ретраї → DLT.
     */
    @Transactional
    public void process(EvaluationEventPayload payload) {
        if (!evaluationAggRepository.markProcessed(payload.evaluationId()) || payload.variantKey() == null)
            return;

        Instant window = payload.occurredAt().truncatedTo(ChronoUnit.MINUTES);
        long rolloutDelta = payload.reason().equals("ROLLOUT") && payload.ruleId() == null ? 1 : 0;
        long uniqueDelta = evaluationAggRepository.markUserSeen(payload.environment(), payload.flagKey(), payload.variantKey(), window, payload.userKeyHash()) ? 1 : 0;
        evaluationAggRepository.incrementAggregate(payload.environment(), payload.flagKey(), payload.variantKey(), window, uniqueDelta, rolloutDelta);

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
