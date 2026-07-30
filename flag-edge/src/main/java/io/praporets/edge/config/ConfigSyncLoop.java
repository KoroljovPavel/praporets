package io.praporets.edge.config;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.praporets.core.model.EnvironmentConfig;
import io.praporets.core.revision.Delta;
import io.praporets.core.revision.DeltaApplier;
import io.praporets.grpc.config.v1.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Серце edge (D1): один фоновий тред, що тримає конфігурацію актуальною весь
 * час життя інстанса. Поглинає {@code SnapshotLoader} з 02c — той клас
 * видали, його роль тут (стан {@code SNAPSHOT} нижче).
 *
 * <p><b>Залежності для інжекту:</b> {@code @GrpcClient("config") Channel},
 * {@code @ConfigProperty praporets.edge.environment},
 * {@code @ConfigProperty praporets.edge.stream.read-timeout} (Duration,
 * дефолт {@code 45s} — 3× heartbeat-інтервал CP),
 * {@link ConfigStore}, {@link ProtoToCoreMapper}, {@link SyncMetrics}.
 *
 * <p><b>Машина станів петлі (виконується в одному треді, як у 02c):</b>
 * <pre>
 * SNAPSHOT:  GetSnapshot(env) → mapper.toEnvironmentConfig → store.swap
 *            → metrics.markSynced(rev) → backoff.reset() → STREAM
 *            (помилка → sleep(backoff.nextDelayMillis()) → SNAPSHOT)
 *
 * STREAM:    async-стаб streamConfig(env, fromRevision = поточна ревізія
 *            store, edgeInstanceId) із ClientResponseObserver:
 *            - onNext/onError кладуть у BlockingQueue&lt;Object&gt; (кап 256,
 *              put — навмисний backpressure на gRPC-тред);
 *            - beforeStart зберігає ClientCallStreamObserver — єдина ручка
 *              для cancel із нашого боку (камінь #3)
 *
 * CONSUME:   u = queue.poll(readTimeout):
 *            - null (тиша довша за таймаут — стрім мертвий без RST)
 *              → cancel → sleep(backoff) → STREAM
 *            - DELTA і u.revision &gt; поточна → mapper.toDelta →
 *              DeltaApplier.apply → store.swap(StoredConfig(u.revision, ...))
 *              → markSynced; u.revision ≤ поточна → ігнор (дубль
 *              catch-up/live — D4). У будь-якому разі перше повідомлення
 *              після конекту → backoff.reset() (камінь #4)
 *            - HEARTBEAT: markSynced(поточна); якщо hb.revision &gt; поточна —
 *              пропущена дельта → cancel → STREAM (без снапшота: сервер
 *              докине склеєну дельту з fromRevision)
 *            - SNAPSHOT_REQUIRED → SNAPSHOT (без backoff-паузи: це
 *              узгоджене перезавантаження, не помилка)
 *            - маркер помилки (onError-сентинел, камінь #2)
 *              → sleep(backoff) → STREAM
 *
 * SHUTDOWN (onStop): cancel стріму + shutdownNow виконавця (перерве і
 *            sleep, і poll) — E-09 частково.
 * </pre>
 *
 * <p>Під час будь-якого розриву store НЕ чіпається — edge продовжує
 * відповідати останньою конфігурацією (вибір AP), staleness росте.
 */
@ApplicationScoped
public class ConfigSyncLoop {

    @GrpcClient("config")
    Channel channel;

    @ConfigProperty(name = "praporets.edge.environment")
    String environment;

    @ConfigProperty(name = "praporets.edge.stream.read-timeout", defaultValue = "45s")
    Duration readTimeout;

    @Inject
    ConfigStore configStore;

    @Inject
    SyncMetrics syncMetrics;

    @Inject
    ProtoToCoreMapper protoToCoreMapper;

    private final Backoff backoff;

    private volatile QueueingObserver observer;

    private static final Object STREAM_COMPLETED = new Object();

    public ConfigSyncLoop() {
        backoff = new Backoff(100, 30_000, () -> ThreadLocalRandom.current().nextDouble());
    }

    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("config-sync-loop");
        t.setDaemon(true);
        return t;
    });

    void onStart(@Observes StartupEvent event) {
        singleThreadExecutor.submit(this::runLoop);
    }

    private void runLoop() {
        try {
            State state = State.SNAPSHOT;
            while (!Thread.currentThread().isInterrupted()) {
                state = switch (state) {
                    case SNAPSHOT -> loadSnapshot();
                    case STREAM -> streamAndConsume();
                };
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.info("Config sync loop stopped", e);
        } catch (Throwable t) {
            Log.error("Config sync loop died", t);
        }
    }

    private State loadSnapshot() throws InterruptedException {
        State state = State.SNAPSHOT;
        try {
            ConfigSnapshot snapshot = ConfigServiceGrpc.newBlockingStub(channel)
                .getSnapshot(SnapshotRequest.newBuilder().setEnvironmentKey(environment).build());
            EnvironmentConfig environmentConfig = protoToCoreMapper.toEnvironmentConfig(snapshot);

            configStore.swap(new ConfigStore.StoredConfig(snapshot.getRevision(), environmentConfig));
            syncMetrics.markSynced(snapshot.getRevision());
            backoff.reset();
            state = State.STREAM;
        } catch (Exception e) {
            Log.warn("Failed to get snapshot", e);
            TimeUnit.MILLISECONDS.sleep(backoff.nextDelayMillis());
        }
        return state;
    }

    private State streamAndConsume() throws InterruptedException {
        ConfigStore.StoredConfig currentStoredConfig = configStore.current()
            .orElseThrow(() -> new IllegalStateException("Stream without loaded store, broken state machine"));
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>(256);

        observer = new QueueingObserver(queue);

        ConfigServiceGrpc.newStub(channel).streamConfig(StreamRequest.newBuilder()
            .setEnvironmentKey(environment)
            .setFromRevision(currentStoredConfig.revision())
            .setEdgeInstanceId("edge-config") //todo: підставити правильний айді. Наприклад hostname + випадковий суфікс (у K8s це стане pod name).
            .build(), observer);

        boolean isFirst = true;
        while (true) {
            Object u = queue.poll(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (u == null) {
                Log.warnf("No messages for %s, cancelling stream and reconnecting", readTimeout);
                observer.call.cancel("read timeout, reconnecting", null);
                TimeUnit.MILLISECONDS.sleep(backoff.nextDelayMillis());
                return State.STREAM;
            }

            if (u instanceof ConfigUpdate update) {
                if (isFirst) {
                    Log.infof("stream established from revision %s", currentStoredConfig.revision());
                    isFirst = false;
                }
                backoff.reset();
                currentStoredConfig = configStore.current()
                    .orElseThrow(() -> new IllegalStateException("Stream without loaded store, broken state machine"));
                switch (update.getPayloadCase()) {
                    case DELTA -> {
                        if (update.getRevision() > currentStoredConfig.revision()) {
                            Delta delta = protoToCoreMapper.toDelta(update.getDelta());
                            configStore.swap(new ConfigStore.StoredConfig(
                                update.getRevision(),
                                DeltaApplier.apply(currentStoredConfig.config(), delta)));
                            syncMetrics.markSynced(update.getRevision());
                        }
                    }
                    case HEARTBEAT -> {
                        syncMetrics.markSynced(currentStoredConfig.revision());
                        if (update.getRevision() > currentStoredConfig.revision()) {
                            observer.call.cancel("heartbeat, reconnecting", null);
                            return State.STREAM;
                        }
                    }
                    case SNAPSHOT_REQUIRED -> {
                        return State.SNAPSHOT;
                    }
                    case PAYLOAD_NOT_SET -> Log.warn("Payload not set");
                    default -> Log.warn("Unknown payload case " + update.getPayloadCase());
                }
            }

            if (u instanceof Throwable t) {
                long delay = backoff.nextDelayMillis();
                Log.warnf("Config stream failed (%s), reconnecting in %d ms",
                    Status.fromThrowable(t), delay);
                TimeUnit.MILLISECONDS.sleep(delay);
                return State.STREAM;
            }

            if (u == STREAM_COMPLETED) {
                Log.info("Closed by server");
                TimeUnit.MILLISECONDS.sleep(backoff.nextDelayMillis());
                return State.STREAM;
            }
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        QueueingObserver obs = observer;
        if (obs != null && obs.call != null)
            obs.call.cancel("shutdown", null);
        singleThreadExecutor.shutdownNow();
    }

    private static final class QueueingObserver implements ClientResponseObserver<StreamRequest, ConfigUpdate> {

        private final BlockingQueue<Object> queue;
        private volatile ClientCallStreamObserver<StreamRequest> call;

        public QueueingObserver(BlockingQueue<Object> queue) {
            this.queue = queue;
        }

        @Override
        public void beforeStart(ClientCallStreamObserver<StreamRequest> requestStream) {
            this.call = requestStream;
        }

        @Override
        public void onNext(ConfigUpdate value) {
            enqueue(value);
        }

        @Override
        public void onError(Throwable t) {
            enqueue(t);
        }

        @Override
        public void onCompleted() {
            enqueue(STREAM_COMPLETED);
        }

        private void enqueue(Object item) {
            try {
                queue.put(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private enum State {
        SNAPSHOT,
        STREAM
    }
}
