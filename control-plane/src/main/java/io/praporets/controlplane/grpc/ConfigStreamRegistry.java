package io.praporets.controlplane.grpc;

import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.praporets.grpc.config.v1.ConfigUpdate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Реєстр відкритих gRPC-стрімів цієї репліки (спека §7.3): хто на який
 * environment підписаний. Ключ — environmentKey, значення — множина активних
 * {@code StreamObserver}-ів. Пам'ять процесу, БД не торкається — впав процес,
 * впали і стріми, edge перепідключиться (02d).
 *
 * <p><b>Реалізація (твоя робота).</b> Вимоги:
 * <ul>
 *   <li><b>Потокобезпека мапи:</b> register/deregister/publish конкурують
 *       (gRPC-тред, event-listener після коміту, heartbeat-планувальник).
 *       {@code ConcurrentHashMap} + {@code ConcurrentHashMap.newKeySet()} для
 *       множин; порожню множину після останнього deregister прибрати з мапи
 *       (інакше {@link #activeEnvironments} вічно віддаватиме мертві env
 *       heartbeat-планувальнику);</li>
 *   <li><b>Потокобезпека observer-а:</b> сам {@code StreamObserver} НЕ
 *       thread-safe, а onNext на один стрім можуть звати кілька тредів
 *       одночасно (live-push + heartbeat). Серіалізуй виклики:
 *       {@code synchronized (observer) { observer.onNext(update); }};</li>
 *   <li><b>Мертві стріми:</b> {@code onNext} кинув
 *       {@code StatusRuntimeException}/{@code IllegalStateException} (клієнт
 *       зник, стрім уже закритий) → deregister цього observer-а і йти далі —
 *       один мертвий підписник не має ламати розсилку решті;</li>
 *   <li>публікація йде по <b>знімку</b> множини (ітерація конкурентної
 *       множини під час deregister — ок для {@code newKeySet()}, але знімок
 *       робить поведінку очевидною).</li>
 * </ul>
 */
@Component
public class ConfigStreamRegistry {

    private final ConcurrentMap<String, Set<StreamObserver<ConfigUpdate>>> subscribers =
        new ConcurrentHashMap<>();

    public ConfigStreamRegistry(MeterRegistry meterRegistry) {
        // NFR/дашборд: «активні стріми» — перша метрика етапу 2 (спека §10)
        Gauge.builder("praporets_config_streams_active", this, ConfigStreamRegistry::activeStreams)
            .description("Кількість відкритих StreamConfig-стрімів цієї репліки")
            .register(meterRegistry);
    }

    /** Додає підписника середовища. */
    public void register(String environmentKey, StreamObserver<ConfigUpdate> observer) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }

    /** Прибирає підписника; невідомий observer — тихий no-op (ідемпотентно). */
    public void deregister(String environmentKey, StreamObserver<ConfigUpdate> observer) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }

    /**
     * Шле update усім підписникам середовища. Observer, що кинув виняток,
     * дереєструється мовчки; середовище без підписників — no-op.
     */
    public void publish(String environmentKey, ConfigUpdate update) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }

    /** Середовища, що мають хоч одного підписника (для heartbeat-обходу). */
    public Set<String> activeEnvironments() {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }

    /** Сумарна кількість відкритих стрімів (значення gauge). */
    public int activeStreams() {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }
}
