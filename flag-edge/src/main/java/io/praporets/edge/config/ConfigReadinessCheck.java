package io.praporets.edge.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * E-01, друга половина: {@code /q/health/ready} каже UP лише коли конфігурація
 * завантажена. До того Kubernetes (04b) не шле трафік на цей pod — інстанс без
 * конфігурації відповідав би на кожен Evaluate помилкою.
 *
 * <p>Це саме <b>readiness</b>, не liveness: інстанс, що чекає на CP, — живий
 * (не рестартувати!), просто не готовий. Плутанина між ними — класичний
 * спосіб влаштувати рестарт-шторм при падінні залежності.
 *
 * <p><b>Залежності для інжекту:</b> {@link ConfigStore}.
 *
 * <p><b>Реалізація (твоя робота):</b> {@code store.isLoaded()} → UP (додай
 * {@code withData("revision", ...)} — безкоштовна діагностика в JSON health);
 * інакше DOWN.
 */
@Readiness
@ApplicationScoped
public class ConfigReadinessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        throw new UnsupportedOperationException("02c: твоя реалізація");
    }
}
