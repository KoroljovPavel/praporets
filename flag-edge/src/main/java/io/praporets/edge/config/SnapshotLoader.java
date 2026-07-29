package io.praporets.edge.config;

import io.grpc.Channel;
import io.praporets.grpc.config.v1.ConfigServiceGrpc;
import io.praporets.grpc.config.v1.ConfigSnapshot;
import io.praporets.grpc.config.v1.SnapshotRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * E-01: на старті тягне повний снапшот із control-plane і кладе в
 * {@link ConfigStore}. Разом із {@link ConfigReadinessCheck} реалізує
 * контракт «readiness тільки після завантаження».
 *
 * <p><b>Залежності для інжекту:</b>
 * <ul>
 *   <li>{@code @GrpcClient("config") io.grpc.Channel} — «сирий» канал
 *       (рішення C3): стаб будуємо самі з готових класів contracts —
 *       {@code ConfigServiceGrpc.newBlockingStub(channel)}. Ім'я "config"
 *       з'єднує інжект із {@code quarkus.grpc.clients.config.*};</li>
 *   <li>{@code @ConfigProperty(name = "praporets.edge.environment") String};</li>
 *   <li>{@link ProtoToCoreMapper}, {@link ConfigStore}.</li>
 * </ul>
 *
 * <p><b>Реалізація (твоя робота), {@code onStart}:</b> НЕ блокувати старт
 * (рішення C5, вибір AP): запусти завантаження у фоновому треді
 * (однопотоковий executor — він же знадобиться 02d для стріму):
 * <ol>
 *   <li>{@code GetSnapshot(environment)} блокуючим стабом;</li>
 *   <li>успіх → {@code store.swap(new StoredConfig(snapshot.getRevision(),
 *       mapper.toEnvironmentConfig(snapshot)))}, лог із ревізією, цикл
 *       закінчено;</li>
 *   <li>помилка (CP лежить, NOT_FOUND, зіпсований снапшот) → лог WARN і
 *       повтор через 2с, доки не вийде або не зупинять (SIGTERM). Instance
 *       лишається живим, але NOT ready — Kubernetes не пошле трафік.
 *       Експоненційний backoff+jitter свідомо НЕ тут — це 02d, для стріму,
 *       звідки він і мігрує сюди.</li>
 * </ol>
 *
 * <p>{@code onStop}: зупини executor ({@code shutdownNow}) — інакше graceful
 * shutdown чекатиме на сплячий retry-цикл.
 */
@ApplicationScoped
public class SnapshotLoader {

    @GrpcClient("config")
    Channel channel;

    @ConfigProperty(name = "praporets.edge.environment")
    String environment;

    @Inject
    ConfigStore configStore;

    @Inject
    ProtoToCoreMapper protoToCoreMapper;

    private static final long RETRY_INTERVAL_MS = 2000L;

    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edge-background-worker");
        t.setDaemon(true);
        return t;
    });

    void onStart(@Observes StartupEvent event) {
        singleThreadExecutor.submit(this::loadDataInBackground);
    }

    private void loadDataInBackground() {
        while (!configStore.isLoaded() && !Thread.currentThread().isInterrupted()) {
            try {
                ConfigSnapshot snapshot = ConfigServiceGrpc.newBlockingStub(channel)
                    .getSnapshot(SnapshotRequest.newBuilder().setEnvironmentKey(environment).build());
                configStore.swap(new ConfigStore.StoredConfig(snapshot.getRevision(), protoToCoreMapper.toEnvironmentConfig(snapshot)));
                break;
            } catch (Exception e) {
                Log.warn("Failed to get snapshot", e);
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        singleThreadExecutor.shutdownNow();
    }
}
