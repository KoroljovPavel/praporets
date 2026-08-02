package io.praporets.analytics.deviation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * A-07: порівняння фактичних часток rollout-обчислень з очікуваними вагами
 * на останньому закритому вікні (I3–I6).
 *
 * <p><b>Залежності для інжекту:</b> {@link DeviationRepository},
 * {@link RolloutExpectations}, {@code MeterRegistry},
 * {@code @ConfigProperty}-аналоги Boot ({@code @Value}):
 * {@code praporets.analytics.deviation.{enabled, threshold, min-sample}}.
 *
 * <p><b>Поле-гейдж:</b>
 * {@code MultiGauge deviation = MultiGauge.builder("praporets_rollout_deviation")
 * .description(...).register(meterRegistry);} — MultiGauge, бо рядків
 * змінна кількість з динамічними тегами (I5).
 *
 * <p><b>{@code check(windowStart)} (твоя робота):</b>
 * <ol>
 *   <li>{@code rolloutCounts(windowStart)} → згрупувати по (environment,
 *       flagKey);</li>
 *   <li>для кожного флага: {@code weightsFor(env, flag)} порожній → скіп
 *       (rollout уже зняли — вікно історичне); {@code total < minSample} →
 *       скіп (I4);</li>
 *   <li>для КОЖНОГО варіанта з очікувань (включно з відсутніми у вікні —
 *       їхній факт 0, I5): {@code deviation = count/total − weight/100_000.0}
 *       (камінь #4: цілочисельний поділ!);</li>
 *   <li>рядки {@code MultiGauge.Row.of(Tags.of("environment", env, "flag",
 *       flag, "variant", v), deviation)} — зібрати ВСІ і одним
 *       {@code deviation.register(rows, true)} (overwrite, камінь #6);</li>
 *   <li>{@code |deviation| > threshold} хоч в одного варіанта → ОДИН WARN
 *       на флаг зі значеннями по варіантах (I6).</li>
 * </ol>
 *
 * <p><b>{@code tick} (твоя робота):</b> {@code @Scheduled(fixedDelayString =
 * "${praporets.analytics.deviation.check-interval}")}; якщо
 * {@code enabled} → {@code check(Instant.now().truncatedTo(MINUTES)
 * .minus(1, MINUTES))}. Гейт property — щоб тести смикали {@code check}
 * руками без фонових перегонів (патерн relay 03a).
 */
@Component
public class DeviationChecker {

    @Scheduled(fixedDelayString = "${praporets.analytics.deviation.check-interval}")
    void tick() {
        // 03d-3: твоя реалізація (порожньо, НЕ UOE — планувальник смикає
        // це в КОЖНОМУ контексті, виняток смітив би в лог усіх тестів)
    }

    /**
     * Перевірка одного вікна; викликається тіком і тестами.
     */
    public void check(Instant windowStart) {
        throw new UnsupportedOperationException("03d-3: твоя реалізація");
    }
}
