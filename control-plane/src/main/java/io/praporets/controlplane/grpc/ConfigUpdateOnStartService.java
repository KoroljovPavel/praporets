package io.praporets.controlplane.grpc;

import io.praporets.controlplane.domain.EnvironmentRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigUpdateOnStartService {

    private final ConfigRevisionMetrics configRevisionMetrics;
    private final EnvironmentRepository environmentRepository;

    public ConfigUpdateOnStartService(ConfigRevisionMetrics configRevisionMetrics, EnvironmentRepository environmentRepository) {
        this.configRevisionMetrics = configRevisionMetrics;
        this.environmentRepository = environmentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStart() {
        environmentRepository.findAll().forEach(environment -> {
            configRevisionMetrics.update(environment.getKey(), environment.getRevision());
        });
    }
}
