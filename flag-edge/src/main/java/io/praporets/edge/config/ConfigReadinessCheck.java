package io.praporets.edge.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness-перевірка {@code /q/health/ready}: UP лише коли конфігурація
 * завантажена в {@link ConfigStore}. До того Kubernetes не шле трафік на цей
 * pod — інстанс без конфігурації відповідав би на кожен Evaluate помилкою.
 * В UP-відповіді додається поточна ревізія ({@code withData("revision", ...)})
 * — безкоштовна діагностика в JSON health.
 *
 * <p>Це саме <b>readiness</b>, не liveness: інстанс, що чекає на CP, — живий
 * (не рестартувати!), просто не готовий. Плутанина між ними — класичний
 * спосіб влаштувати рестарт-шторм при падінні залежності.
 */
@Readiness
@ApplicationScoped
public class ConfigReadinessCheck implements HealthCheck {

    @Inject
    ConfigStore configStore;

    @Override
    public HealthCheckResponse call() {
        if (configStore.isLoaded()) {
            long revision = configStore.current().map(ConfigStore.StoredConfig::revision).orElse(-1L);
            return HealthCheckResponse.builder().up().name("edge-config").withData("revision", revision).build();
        } else {
            return HealthCheckResponse.down("edge-config");
        }
    }
}
