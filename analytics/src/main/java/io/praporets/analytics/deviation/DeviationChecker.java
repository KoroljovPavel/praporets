package io.praporets.analytics.deviation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private final MeterRegistry meterRegistry;
    private final DeviationRepository deviationRepository;
    private final RolloutExpectations rolloutExpectations;

    @Value("${praporets.analytics.deviation.enabled:true}")
    boolean deviationEnabled;

    @Value("${praporets.analytics.deviation.threshold:0.05}")
    double deviationThreshold;

    @Value("${praporets.analytics.deviation.min-sample:100}")
    int minSample;

    private final MultiGauge deviation;
    private final Logger log = LoggerFactory.getLogger(DeviationChecker.class);

    public DeviationChecker(MeterRegistry meterRegistry, DeviationRepository deviationRepository, RolloutExpectations rolloutExpectations) {
        this.meterRegistry = meterRegistry;
        this.deviationRepository = deviationRepository;
        this.rolloutExpectations = rolloutExpectations;

        deviation = MultiGauge.builder("praporets_rollout_deviation").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${praporets.analytics.deviation.check-interval}")
    void tick() {
        if (deviationEnabled) {
            check(Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.MINUTES));
        }
    }

    /**
     * Перевірка одного вікна; викликається тіком і тестами.
     */
    public void check(Instant windowStart) {
        Map<GroupingKey, List<DeviationRepository.RolloutCount>> groupingRolloutCountByEnvAndFlagKey =
            deviationRepository.rolloutCounts(windowStart).stream().collect(
                Collectors.groupingBy(rolloutCount -> new GroupingKey(rolloutCount.environment(), rolloutCount.flagKey()))
            );

        List<MultiGauge.Row<Number>> gaugeList = new ArrayList<>();

        for (Map.Entry<GroupingKey, List<DeviationRepository.RolloutCount>> entry : groupingRolloutCountByEnvAndFlagKey.entrySet()) {
            long total = entry.getValue().stream().mapToLong(DeviationRepository.RolloutCount::rolloutCount).sum();
            if (total < minSample) continue;

            Map<String, Long> actualByVariant = entry.getValue().stream().collect(
                Collectors.toMap(
                    DeviationRepository.RolloutCount::variantKey,
                    DeviationRepository.RolloutCount::rolloutCount
                )
            );

            rolloutExpectations.weightsFor(entry.getKey().environment(), entry.getKey().flagKey()).ifPresent(
                weights -> {
                    boolean writeLog = false;
                    for (Map.Entry<String, Integer> expected : weights.entrySet()) {
                        double actual = actualByVariant.getOrDefault(expected.getKey(), 0L) / (double) total;
                        double deviation = actual - expected.getValue() / 100_000.0;
                        gaugeList.add(
                            MultiGauge.Row.of(Tags.of(
                                "environment", entry.getKey().environment(),
                                "flag", entry.getKey().flagKey(),
                                "variant", expected.getKey()
                            ), deviation)
                        );

                        if (Math.abs(deviation) > deviationThreshold) {
                            writeLog = true;
                        }
                    }

                    if (writeLog) {
                        String breakDown = weights.keySet().stream()
                            .map(v -> {
                                double actual = actualByVariant.getOrDefault(v, 0L) / (double) total;
                                double expected = weights.get(v) / 100_000.0;
                                return "%s: %.1f%% vs expected %.1f%% (%+.1f pp)"
                                    .formatted(v, actual * 100, expected * 100, (actual - expected) * 100);
                            })
                            .collect(Collectors.joining(","));
                        log.warn("Rollout deviation for {}/{} in window {} (n={}): {}",
                            entry.getKey().environment(), entry.getKey().flagKey(), windowStart, total, breakDown);
                    }
                });
        }

        deviation.register(gaugeList, true);
    }

    private record GroupingKey(String environment, String flagKey) {
    }
}
