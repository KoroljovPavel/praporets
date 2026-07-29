package io.praporets.edge.config;

import io.praporets.core.model.EnvironmentConfig;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Єдине сховище конфігурації edge-інстанса: незмінний знімок
 * {@code (revision, EnvironmentConfig)} за {@code AtomicReference} (E-02:
 * atomic swap). Читачі (обчислення в 02e, readiness) бачать або старий знімок
 * цілком, або новий цілком — жодних локів і жодного напівзастосованого стану.
 *
 * <p>Стартовий стан — «не завантажено» (порожній Optional): у цьому стані
 * readiness = DOWN, обчислення неможливе.
 *
 * <p><b>Реалізація (твоя робота):</b> тримай
 * {@code AtomicReference<StoredConfig>} (початково {@code null}); swap —
 * {@code set} нового record-а (заміна завжди повна, CAS не потрібен — писар
 * один: завантажувач снапшота, у 02d — той самий тред стріму).
 */
@ApplicationScoped
public class ConfigStore {

    /**
     * Незмінний знімок: ревізія + готова core-конфігурація.
     *
     * @param revision ревізія середовища, з якої зібрано конфігурацію
     * @param config   конфігурація для {@code Evaluator}
     */
    public record StoredConfig(long revision, EnvironmentConfig config) {
    }

    /**
     * Атомарно підміняє поточний знімок новим.
     */
    public void swap(StoredConfig newConfig) {
        throw new UnsupportedOperationException("02c: твоя реалізація");
    }

    /**
     * Поточний знімок; порожній до першого успішного завантаження.
     */
    public Optional<StoredConfig> current() {
        throw new UnsupportedOperationException("02c: твоя реалізація");
    }

    /**
     * Чи завантажено конфігурацію хоч раз (для readiness).
     */
    public boolean isLoaded() {
        throw new UnsupportedOperationException("02c: твоя реалізація");
    }
}
