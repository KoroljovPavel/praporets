package io.praporets.edge.config;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Дві метрики синхронізації (D6, частина E-06/E-08), видимі на `/q/metrics`:
 * <ul>
 *   <li>{@code flag_edge_config_staleness_seconds} — скільки секунд тому
 *       edge востаннє чув від CP підтвердження актуальності (дельта АБО
 *       heartbeat). Росте під час розриву — головний сигнал алерту «edge
 *       живе на застарілій конфігурації» (компроміс AP, спека §7.4);</li>
 *   <li>{@code flag_edge_config_revision} — поточна ревізія. На дашборді 04c
 *       поруч із ревізією CP покаже синхронний стрибок при кожній зміні.</li>
 * </ul>
 *
 * <p>Імена з префіксом {@code flag_edge_} — дослівно з E-06 (CP-метрики мають
 * префікс {@code praporets_}; розбіжність свідома, зафіксована в D6).
 *
 * <p><b>Залежності для інжекту:</b> {@code MeterRegistry} (гейджі реєструються
 * в конструкторі, як у {@code ConfigStreamRegistry} з 02b).
 *
 * <p><b>Реалізація (твоя робота):</b> тримай два {@code AtomicLong} —
 * epoch-millis останньої синхронізації (ініціалізуй часом створення біна, щоб
 * staleness був осмисленим і до першого снапшота) і ревізію.
 * staleness-gauge обчислюється на льоту: {@code (now - lastSync) / 1000.0}.
 */
@ApplicationScoped
public class SyncMetrics {

    /**
     * Фіксує успішну синхронізацію: оновлює і час, і ревізію.
     */
    public void markSynced(long revision) {
        throw new UnsupportedOperationException("02d: твоя реалізація");
    }
}
