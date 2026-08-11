package io.praporets.edge.config;

import io.praporets.core.model.EnvironmentConfig;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Єдине сховище конфігурації edge-інстанса: незмінний знімок
 * {@code (revision, EnvironmentConfig)} за volatile-посиланням — atomic swap.
 * Читачі (обчислення, readiness) бачать або старий знімок цілком, або новий
 * цілком — жодних локів і жодного напівзастосованого стану. Простий
 * volatile-set достатній: писар один — фоновий тред {@link ConfigSyncLoop},
 * і кожна заміна повна, тож CAS не потрібен.
 *
 * <p>Стартовий стан — «не завантажено» (порожній Optional): у цьому стані
 * readiness = DOWN, обчислення неможливе.
 */
@ApplicationScoped
public class ConfigStore {

    private volatile StoredConfig storedConfig;

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
        storedConfig = newConfig;
    }

    /**
     * Поточний знімок; порожній до першого успішного завантаження.
     */
    public Optional<StoredConfig> current() {
        return Optional.ofNullable(storedConfig);
    }

    /**
     * Чи завантажено конфігурацію хоч раз (для readiness).
     */
    public boolean isLoaded() {
        return storedConfig != null;
    }
}
