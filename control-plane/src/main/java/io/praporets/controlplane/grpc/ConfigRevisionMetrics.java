package io.praporets.controlplane.grpc;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ConfigRevisionMetrics {

    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, AtomicLong> metrics = new ConcurrentHashMap<>();

    public ConfigRevisionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void update(String environmentKey, long revision) {
        AtomicLong atomicRevision = metrics.computeIfAbsent(environmentKey, key -> {
            AtomicLong newRevision = new AtomicLong(revision);

            Gauge.builder("praporets_config_revision", newRevision, AtomicLong::get)
                .tag("environment", environmentKey)
                .register(meterRegistry);

            return newRevision;
        });

        atomicRevision.accumulateAndGet(revision, Math::max);
    }
}
