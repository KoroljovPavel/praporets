package io.praporets.controlplane.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.praporets.grpc.config.v1.ConfigUpdate;
import io.praporets.grpc.config.v1.Heartbeat;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Контракт реєстру стрімів — ізоляція середовищ, прибирання мертвих
 * підписників, gauge. Чистий юніт: без Spring, без мережі.
 */
class ConfigStreamRegistryTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ConfigStreamRegistry registry = new ConfigStreamRegistry(meterRegistry);

    /** Мінімальний фейк: збирає onNext-и; за бажанням «вмирає» на першому ж пуші. */
    private static final class RecordingObserver implements StreamObserver<ConfigUpdate> {
        final List<ConfigUpdate> received = new ArrayList<>();
        final boolean broken;

        RecordingObserver() { this(false); }

        RecordingObserver(boolean broken) { this.broken = broken; }

        @Override
        public void onNext(ConfigUpdate value) {
            if (broken) {
                // так виглядає пуш у стрім, чий клієнт уже відвалився
                throw Status.CANCELLED.asRuntimeException();
            }
            received.add(value);
        }

        @Override
        public void onError(Throwable t) { }

        @Override
        public void onCompleted() { }
    }

    private static ConfigUpdate heartbeat(long revision) {
        return ConfigUpdate.newBuilder()
            .setRevision(revision)
            .setHeartbeat(Heartbeat.newBuilder().setServerTimeMillis(1L))
            .build();
    }

    @Test
    void publish_reaches_only_subscribers_of_that_environment() {
        RecordingObserver dev = new RecordingObserver();
        RecordingObserver prod = new RecordingObserver();
        registry.register("dev", dev);
        registry.register("prod", prod);

        registry.publish("dev", heartbeat(7));

        assertThat(dev.received).hasSize(1);
        assertThat(dev.received.getFirst().getRevision()).isEqualTo(7);
        assertThat(prod.received).isEmpty();
    }

    @Test
    void publish_to_environment_without_subscribers_is_a_silent_noop() {
        assertThatCode(() -> registry.publish("ghost", heartbeat(1))).doesNotThrowAnyException();
    }

    @Test
    void broken_observer_is_deregistered_and_the_rest_keep_receiving() {
        RecordingObserver healthy = new RecordingObserver();
        RecordingObserver broken = new RecordingObserver(true);
        registry.register("dev", healthy);
        registry.register("dev", broken);

        registry.publish("dev", heartbeat(1));  // broken кидає CANCELLED → вилітає з реєстру
        registry.publish("dev", heartbeat(2));

        assertThat(healthy.received).hasSize(2);
        assertThat(registry.activeStreams()).isEqualTo(1);
    }

    @Test
    void deregister_is_idempotent_and_clears_empty_environment() {
        RecordingObserver observer = new RecordingObserver();
        registry.register("dev", observer);
        assertThat(registry.activeEnvironments()).containsExactly("dev");

        registry.deregister("dev", observer);
        registry.deregister("dev", observer);  // повторно — no-op, не виняток

        // порожнє середовище зникає повністю: heartbeat-обхід не має ходити
        // по мертвих env і смикати БД заради нікого
        assertThat(registry.activeEnvironments()).isEmpty();
        assertThat(registry.activeStreams()).isZero();
    }

    @Test
    void gauge_praporets_config_streams_active_follows_registry_state() {
        assertThat(meterRegistry.get("praporets_config_streams_active").gauge().value()).isZero();

        registry.register("dev", new RecordingObserver());
        registry.register("prod", new RecordingObserver());

        assertThat(meterRegistry.get("praporets_config_streams_active").gauge().value()).isEqualTo(2);
    }
}
