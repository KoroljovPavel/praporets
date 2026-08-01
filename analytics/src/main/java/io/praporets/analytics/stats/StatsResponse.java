package io.praporets.analytics.stats;

import java.time.Instant;
import java.util.List;

/**
 * Відповідь {@code GET /api/v1/stats} (A-05) — часовий ряд по варіантах.
 * Форма-контракт (пінується тестами), як DTO подій у 03d-1.
 *
 * @param series по одній серії на варіант; порожній список — флаг без
 *               агрегатів у діапазоні (це 200, не 404)
 */
public record StatsResponse(String environment, String flagKey, List<VariantSeries> series) {

    /**
     * Серія одного варіанта; точки впорядковані за {@code windowStart}.
     */
    public record VariantSeries(String variantKey, List<Point> points) {
    }

    /**
     * Одна точка серії — хвилинне вікно.
     *
     * @param cumulativeEvalCount біжуча сума evalCount від початку діапазону
     *                            по це вікно включно (H4, Gatherers.scan)
     */
    public record Point(Instant windowStart, long evalCount, long uniqueUsers, long cumulativeEvalCount) {
    }
}
