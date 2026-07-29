package io.praporets.controlplane.grpc;

import io.praporets.controlplane.domain.EnvironmentRepository;
import io.praporets.grpc.config.v1.ConfigUpdate;
import io.praporets.grpc.config.v1.Heartbeat;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Періодичний heartbeat у всі відкриті стріми (проміжки без змін конфігурації
 * можуть тривати години). Навіщо:
 * <ul>
 *   <li>LB/NAT/ingress ріжуть «тихі» TCP-з'єднання за idle-таймаутом —
 *       heartbeat тримає їх живими;</li>
 *   <li>edge відрізняє «змін немає» від «стрім мертвий»: немає heartbeat
 *       довше N інтервалів → reconnect (02d);</li>
 *   <li>{@code ConfigUpdate.revision} у heartbeat = <b>поточна ревізія
 *       середовища</b> — дешевий детектор відставання: edge бачить у
 *       heartbeat ревізію більшу за свою → пропустив дельту → reconnect, не
 *       чекаючи наступної зміни.</li>
 * </ul>
 *
 * <p><b>Реалізація (твоя робота):</b> для кожного env із
 * {@code registry.activeEnvironments()}: ревізія з
 * {@code environmentRepository.findByKey} (середовища раптом нема — пропусти)
 * → {@code registry.publish(env, ConfigUpdate{revision,
 * heartbeat{server_time_millis = System.currentTimeMillis()}})}.
 *
 * <p>Потрібен {@code @EnableScheduling} на {@code ControlPlaneApplication} —
 * без нього анотація нижче мовчки не робить нічого. Інтервал —
 * {@code praporets.grpc.heartbeat-interval} (тести ставлять мілісекунди,
 * прод — 15s).
 */
@Component
public class HeartbeatScheduler {

    private final ConfigStreamRegistry configStreamRegistry;
    private final EnvironmentRepository environmentRepository;

    public HeartbeatScheduler(ConfigStreamRegistry configStreamRegistry, EnvironmentRepository environmentRepository) {
        this.configStreamRegistry = configStreamRegistry;
        this.environmentRepository = environmentRepository;
    }

    @Scheduled(fixedRateString = "${praporets.grpc.heartbeat-interval:15s}")
    public void beat() {
        for (String environmentKey : configStreamRegistry.activeEnvironments()) {
            environmentRepository.findByKey(environmentKey).ifPresent(environment -> {
                configStreamRegistry.publish(environmentKey, ConfigUpdate.newBuilder()
                    .setRevision(environment.getRevision())
                    .setHeartbeat(Heartbeat.newBuilder()
                        .setServerTimeMillis(System.currentTimeMillis())
                        .build())
                    .build());
            });
        }
    }
}
