package io.praporets.edge.config;

import java.util.function.DoubleSupplier;

/**
 * Експоненційний backoff із full jitter (E-06): затримка =
 * {@code uniform(0, стеля)}, стеля стартує з {@code baseMillis} і подвоюється
 * на кожен виклик до {@code capMillis}. Чому full jitter, а не «чистий»
 * експоненційний: сотні edge-ів, що втратили CP одночасно (деплой, падіння),
 * без джитера повернулися б до нього ідеально синхронними хвилями
 * (thundering herd) — рандомізація розмазує їх у часі.
 *
 * <p>Чистий клас без Quarkus — юніт-тести без сну і без рефлексії.
 * Не потокобезпечний — ним володіє єдиний тред {@link ConfigSyncLoop} (D1).
 *
 * <p>Конструктор — частина контракту (пінять тести): {@code jitterSource}
 * повертає число з [0, 1); прод-код передає
 * {@code ThreadLocalRandom.current()::nextDouble}, тести — детермінований
 * supplier.
 */
public class Backoff {

    private long ceilingMillis;
    private final long capMillis;
    private final long baseMillis;
    private final DoubleSupplier jitterSource;

    public Backoff(long baseMillis, long capMillis, DoubleSupplier jitterSource) {
        this.capMillis = capMillis;
        this.baseMillis = baseMillis;
        this.ceilingMillis = baseMillis;
        this.jitterSource = jitterSource;
    }

    /**
     * Затримка перед наступною спробою: {@code (long) (jitter * стеля)};
     * після обчислення стеля подвоюється (не вище капу).
     */
    public long nextDelayMillis() {
        throw new UnsupportedOperationException("02d: твоя реалізація");
    }

    /**
     * Повертає стелю до {@code baseMillis} — звати після успішного повідомлення.
     */
    public void reset() {
        throw new UnsupportedOperationException("02d: твоя реалізація");
    }
}
