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
 * Детекція відхилення rollout: порівняння фактичних часток rollout-обчислень
 * з очікуваними вагами на останньому закритому хвилинному вікні. Результат —
 * метрика {@code praporets_rollout_deviation} (по рядку на варіант) плюс
 * WARN-лог, коли відхилення перевищує поріг.
 *
 * <p>Гейдж — {@code MultiGauge}, бо рядків змінна кількість з динамічними
 * тегами (environment/flag/variant); кожен прогін реєструє повний набір
 * рядків з overwrite — застарілі рядки не виживають між вікнами.
 *
 * <p>Налаштування — {@code praporets.analytics.deviation.*}:
 * {@code enabled}, {@code threshold}, {@code min-sample},
 * {@code check-interval}.
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

    /**
     * Плановий запуск: перевіряє останнє ЗАКРИТЕ вікно (попередню хвилину).
     * Property-гейт {@code enabled} дозволяє тестам вимкнути фон і смикати
     * {@link #check} руками без перегонів із планувальником.
     */
    @Scheduled(fixedDelayString = "${praporets.analytics.deviation.check-interval}")
    void tick() {
        if (deviationEnabled) {
            check(Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.MINUTES));
        }
    }

    /**
     * Перевірка одного вікна; викликається тіком і тестами.
     * <ol>
     *   <li>rollout-лічильники вікна групуються по (environment, flagKey);</li>
     *   <li>{@code total < minSample} → флаг скіпається: на малій вибірці
     *       біноміальний шум дає хибні спрацювання, гейджа для флага не
     *       буде взагалі;</li>
     *   <li>флаг без очікувань (rollout уже зняли) — скіп;</li>
     *   <li>для КОЖНОГО варіанта з очікувань (включно з відсутніми у
     *       вікні — їхній факт 0, тобто найгірше відхилення, а не «немає
     *       даних»): {@code deviation = факт/total − вага/100000};</li>
     *   <li>усі рядки реєструються одним {@code register(rows, true)}
     *       (overwrite);</li>
     *   <li>{@code |deviation| > threshold} хоч в одного варіанта —
     *       ОДИН WARN на флаг із розбивкою по варіантах.</li>
     * </ol>
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
