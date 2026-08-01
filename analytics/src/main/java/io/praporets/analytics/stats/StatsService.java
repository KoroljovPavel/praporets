package io.praporets.analytics.stats;

import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Збірка часового ряду: рядки {@code findWindows} → серії по варіантах з
 * кумулятивою (H4).
 *
 * <p><b>Залежності для інжекту:</b> {@link StatsRepository}.
 *
 * <p><b>{@code stats} (твоя робота):</b>
 * <ol>
 *   <li>{@code findWindows(...)} — уже впорядковані за часом;</li>
 *   <li>групування по {@code variantKey} зі ЗБЕРЕЖЕННЯМ порядку точок
 *       (камінь #6: {@code groupingBy(..., LinkedHashMap::new, toList())}
 *       або ручний прохід);</li>
 *   <li>кумулятива — <b>{@code Gatherers.scan}</b> (JDK 24, java.util.stream):
 *       <pre>
 * List&lt;Point&gt; points = windows.stream()
 *     .gather(Gatherers.scan(
 *         () -&gt; new Point(null, 0, 0, 0),
 *         (acc, w) -&gt; new Point(w.windowStart(), w.evalCount(), w.uniqueUsers(),
 *                               acc.cumulativeEvalCount() + w.evalCount())))
 *     .toList();
 *       </pre>
 *       {@code scan} віддає ПРОМІЖНІ значення згортки — це й є біжуча
 *       сума; {@code fold} віддав би лише фінал (камінь #5). Ініціальний
 *       елемент — «нульовий акумулятор», у вихід не потрапляє;</li>
 *   <li>зібрати {@link StatsResponse}; флаг без рядків → порожній
 *       {@code series}, не помилка.</li>
 * </ol>
 */
@Service
public class StatsService {

    public StatsResponse stats(String environment, String flagKey, Instant from, Instant to) {
        throw new UnsupportedOperationException("03d-2: твоя реалізація");
    }
}
