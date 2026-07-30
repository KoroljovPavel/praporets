package io.praporets.edge.config;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

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

    void onStart(@Observes StartupEvent event) {
        throw new UnsupportedOperationException("02d: твоя реалізація");
    }

    void onStop(@Observes ShutdownEvent event) {
        throw new UnsupportedOperationException("02d: твоя реалізація");
    }
}
