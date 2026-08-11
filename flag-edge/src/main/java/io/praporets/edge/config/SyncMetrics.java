package io.praporets.edge.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Дві метрики синхронізації конфігурації, видимі на {@code /q/metrics}:
 * <ul>
 *   <li>{@code flag_edge_config_staleness_seconds} — скільки секунд тому
 *       edge востаннє чув від CP підтвердження актуальності (дельта АБО
 *       heartbeat). Росте під час розриву — головний сигнал алерту «edge
 *       живе на застарілій конфігурації»: за AP-вибором edge при розриві
 *       продовжує відповідати старою конфігурацією, і ця метрика — ціна
 *       того компромісу, зроблена видимою;</li>
 *   <li>{@code flag_edge_config_revision} — поточна ревізія; на дашборді
 *       поруч із ревізією CP показує синхронний стрибок при кожній зміні.</li>
 * </ul>
 *
 * <p>Префікс {@code flag_edge_} свідомо відрізняється від CP-метрик
 * ({@code praporets_}) — сервіси легко розрізняти в одному Prometheus.
 *
 * <p>Стан — два {@code AtomicLong}: epoch-секунди останньої синхронізації
 * (ініціалізуються часом створення біна, щоб staleness був осмисленим і до
 * першого снапшота) і остання ревізія. Staleness-gauge обчислюється на льоту
 * як {@code now - lastSync}.
 */
@ApplicationScoped
public class SyncMetrics {

    AtomicLong lastSyncSeconds = new AtomicLong(Instant.now().getEpochSecond());
    AtomicLong lastRevision = new AtomicLong(0);

    public SyncMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("flag_edge_config_staleness_seconds", this, SyncMetrics::calculateSecondsAgo)
            .description("скільки секунд тому edge востаннє чув від CP підтвердження актуальності")
            .baseUnit("seconds")
            .register(meterRegistry);

        Gauge.builder("flag_edge_config_revision", lastRevision, AtomicLong::get)
            .description("поточна ревізія")
            .baseUnit("revision")
            .register(meterRegistry);
    }

    private double calculateSecondsAgo() {
        return Instant.now().getEpochSecond() - lastSyncSeconds.get();
    }

    /**
     * Фіксує успішну синхронізацію: оновлює і час, і ревізію.
     */
    public void markSynced(long revision) {
        lastSyncSeconds.set(Instant.now().getEpochSecond());
        lastRevision.set(revision);
    }
}
