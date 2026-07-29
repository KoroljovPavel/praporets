package io.praporets.edge.config;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

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

    void onStart(@Observes StartupEvent event) {
        throw new UnsupportedOperationException("02c: твоя реалізація");
    }

    void onStop(@Observes ShutdownEvent event) {
        throw new UnsupportedOperationException("02c: твоя реалізація");
    }
}
