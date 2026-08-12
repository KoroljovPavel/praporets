package io.praporets.controlplane.grpc;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigRevisionMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ConfigRevisionMetrics registry = new ConfigRevisionMetrics(meterRegistry);

    @Test
    public void new_env_register_gauge() {
        String env = "env";

        assertThatThrownBy(() -> meterRegistry.get("praporets_config_revision").tag("environment", env).gauge());

        registry.update(env, 1);

        assertThat(meterRegistry.get("praporets_config_revision").tag("environment", env).gauge().value()).isEqualTo(1);
    }

    @Test
    public void update_metric_with_greater_revision_updates_metric() {
        String env = "env";
        registry.update(env, 1);
        registry.update(env, 2);
        assertThat(meterRegistry.get("praporets_config_revision").tag("environment", env).gauge().value()).isEqualTo(2);
    }

    @Test
    public void update_metric_with_lower_revision_NOT_updates_metric() {
        String env = "env";
        registry.update(env, 2);
        registry.update(env, 1);
        assertThat(meterRegistry.get("praporets_config_revision").tag("environment", env).gauge().value()).isEqualTo(2);
    }

    @Test
    public void for_different_envs_different_metrics() {
        String env1 = "env1";
        String env2 = "env2";

        registry.update(env1, 1);
        registry.update(env2, 2);

        assertThat(meterRegistry.get("praporets_config_revision").tag("environment", env1).gauge().value()).isEqualTo(1);
        assertThat(meterRegistry.get("praporets_config_revision").tag("environment", env2).gauge().value()).isEqualTo(2);
    }
}
