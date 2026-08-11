package io.praporets.analytics.stats;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Gatherers;

/**
 * Збірка часового ряду: рядки {@link StatsRepository#findWindows} (уже
 * впорядковані за часом) → серії по варіантах із біжучою сумою.
 * Групування по {@code variantKey} — через {@code LinkedHashMap}, щоб
 * зберегти порядок точок усередині серії. Кумулятива — через
 * {@code Gatherers.scan}: на відміну від {@code fold} він віддає ПРОМІЖНІ
 * значення згортки, що й є біжучою сумою; ініціальний «нульовий
 * акумулятор» у вихід не потрапляє. Флаг без рядків у діапазоні →
 * порожній {@code series}, не помилка.
 */
@Service
public class StatsService {

    private final StatsRepository statsRepository;

    public StatsService(StatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    public StatsResponse stats(String environment, String flagKey, Instant from, Instant to) {
        Map<String, List<StatsResponse.Point>> points = statsRepository.findWindows(environment, flagKey, from, to)
            .stream()
            .collect(Collectors.groupingBy(
                StatsRepository.AggWindow::variantKey,
                LinkedHashMap::new,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> list.stream()
                        .gather(Gatherers.scan(
                            () -> new StatsResponse.Point(null, 0, 0, 0),
                            (acc, w) -> new StatsResponse.Point(
                                w.windowStart(),
                                w.evalCount(),
                                w.uniqueUsers(),
                                acc.cumulativeEvalCount() + w.evalCount()
                            )
                        ))
                        .toList()
                )
            ));

        List<StatsResponse.VariantSeries> variantSeries = points.entrySet()
            .stream().map(p -> new StatsResponse.VariantSeries(p.getKey(), p.getValue())).toList();

        return new StatsResponse(environment, flagKey, variantSeries);
    }
}
