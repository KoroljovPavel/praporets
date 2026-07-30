package io.praporets.edge.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 02d: контракт backoff-а (D5). jitterSource детермінований — тести без сну
 * і без справжнього Random.
 */
class BackoffTest {

    @Test
    void ceiling_doubles_from_base_on_each_call() {
        // jitter 0.5 → завжди половина стелі: послідовність видає стелю напряму
        Backoff backoff = new Backoff(100, 30_000, () -> 0.5);

        assertThat(backoff.nextDelayMillis()).isEqualTo(50);    // стеля 100
        assertThat(backoff.nextDelayMillis()).isEqualTo(100);   // стеля 200
        assertThat(backoff.nextDelayMillis()).isEqualTo(200);   // стеля 400
        assertThat(backoff.nextDelayMillis()).isEqualTo(400);   // стеля 800
    }

    @Test
    void ceiling_is_capped_at_max() {
        Backoff backoff = new Backoff(100, 30_000, () -> 0.5);

        for (int i = 0; i < 20; i++) {
            backoff.nextDelayMillis();
        }

        // стеля давно вперлася в кап 30_000 → 0.5 * 30_000
        assertThat(backoff.nextDelayMillis()).isEqualTo(15_000);
        assertThat(backoff.nextDelayMillis()).isEqualTo(15_000);
    }

    @Test
    void reset_returns_ceiling_to_base() {
        Backoff backoff = new Backoff(100, 30_000, () -> 0.5);
        backoff.nextDelayMillis();
        backoff.nextDelayMillis();
        backoff.nextDelayMillis();

        backoff.reset();

        assertThat(backoff.nextDelayMillis()).isEqualTo(50);
    }

    @Test
    void zero_jitter_yields_zero_delay_and_is_legal() {
        // full jitter: нижня межа діапазону — 0мс, і це нормально
        Backoff backoff = new Backoff(100, 30_000, () -> 0.0);

        assertThat(backoff.nextDelayMillis()).isZero();
        assertThat(backoff.nextDelayMillis()).isZero();
    }
}
