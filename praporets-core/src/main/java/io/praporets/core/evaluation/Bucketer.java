package io.praporets.core.evaluation;

import io.praporets.core.hash.MurmurHash3;
import io.praporets.core.model.Bucket;
import io.praporets.core.model.Rollout;

/**
 * Детермінований розподіл користувачів по бакетах percentage rollout.
 *
 * <p>Чиста функція: жодного стану, жодної випадковості, жодної координації між
 * інстансами. Той самий вхід дає той самий варіант на будь-якому edge-інстансі —
 * це фундаментальна вимога системи (спека, розділ 7.2).
 *
 * <p><b>Алгоритм (контракт, не деталь реалізації):</b>
 * <pre>
 * input = flagKey + ":" + rollout.salt() + ":" + userKey      // UTF-8
 * h     = MurmurHash3.hash32(input, seed = 0)
 * point = (h &amp; 0x7FFF_FFFF) % 100_000                          // [0, 100_000)
 *
 * cumulative = 0
 * для кожного bucket у rollout.buckets() по порядку:
 *     cumulative += bucket.weight()
 *     якщо point &lt; cumulative: повернути bucket.variantKey()
 * повернути останній bucket.variantKey()                       // захист від округлень
 * </pre>
 *
 * <p>Чому саме так:
 * <ul>
 *   <li>{@code flagKey} у вході — той самий користувач на різних флагах потрапляє
 *       в різні бакети (незалежність експериментів);</li>
 *   <li>маска {@code 0x7FFF_FFFF} <b>до</b> модуля — інакше від'ємний хеш дає
 *       від'ємний point;</li>
 *   <li>кумулятивний перебір, а не модуль по вазі — збільшення ваги першого бакета
 *       лише розширює його діапазон точок, тож жоден користувач не «випадає» з
 *       варіанта при зростанні rollout (монотонність, ADR-011);</li>
 *   <li>фолбек на останній бакет недосяжний при валідному {@link Rollout}
 *       (сума ваг = 100_000), але лишається як захист.</li>
 * </ul>
 */
public final class Bucketer {

    /** Сума ваг валідного rollout: 100% у стотисячних. */
    public static final int TOTAL_WEIGHT = 100_000;

    private Bucketer() {
    }

    /**
     * Обчислює, у який варіант потрапляє користувач.
     *
     * @param rollout розподіл (валідний за інваріантами {@link Rollout}, не {@code null})
     * @param flagKey ключ флага (не {@code null})
     * @param userKey ключ користувача (не {@code null})
     * @return ключ варіанта одного з бакетів rollout
     */
    public static String variantKeyFor(Rollout rollout, String flagKey, String userKey) {
        String input = flagKey + ":" + rollout.salt() + ":" + userKey;
        long h = MurmurHash3.hash32(input, 0);
        int point = (int) (h & 0x7FFF_FFFF) % TOTAL_WEIGHT;
        int cumulative = 0;
        for (Bucket bucket : rollout.buckets()) {
            cumulative += bucket.weight();
            if (point < cumulative) {
                return bucket.variantKey();
            }
        }
        return rollout.buckets().getLast().variantKey();
    }
}
