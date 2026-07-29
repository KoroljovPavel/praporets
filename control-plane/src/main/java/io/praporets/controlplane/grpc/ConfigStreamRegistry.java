package io.praporets.controlplane.grpc;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.praporets.grpc.config.v1.ConfigUpdate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

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
        subscribers.computeIfAbsent(environmentKey, k -> ConcurrentHashMap.newKeySet())
            .add(observer);
    }

    /** Прибирає підписника; невідомий observer — тихий no-op (ідемпотентно). */
    public void deregister(String environmentKey, StreamObserver<ConfigUpdate> observer) {
        Set<StreamObserver<ConfigUpdate>> streamObservers = subscribers.get(environmentKey);
        if (streamObservers != null) {
            streamObservers.remove(observer);
        }
    }

    /**
     * Шле update усім підписникам середовища. Observer, що кинув виняток,
     * дереєструється мовчки; середовище без підписників — no-op.
     */
    // лок = сам observer: він спільний для всіх publisher у цей стрім (лежить у
    // subscribers), а лок-на-стрім не серіалізує розсилку в ІНШІ стріми
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public void publish(String environmentKey, ConfigUpdate update) {
        Set<StreamObserver<ConfigUpdate>> streamObservers = subscribers.get(environmentKey);
        if (streamObservers != null) {
            for (StreamObserver<ConfigUpdate> streamObserver : streamObservers) {
                send(environmentKey, streamObserver, update);
            }
        }
    }

    // лок = сам observer: він спільний для всіх publishers у цей стрім (лежить у
    // subscribers), а лок-на-стрім не серіалізує розсилку в ІНШІ стріми
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public void send(String environmentKey, StreamObserver<ConfigUpdate> observer, ConfigUpdate update) {
        synchronized (observer) {
            try {
                observer.onNext(update);
            } catch (IllegalStateException | StatusRuntimeException e) {
                deregister(environmentKey, observer);
            }
        }
    }

    /** Середовища, що мають хоч одного підписника (для heartbeat-обходу). */
    public Set<String> activeEnvironments() {
        return subscribers.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /** Сумарна кількість відкритих стрімів (значення gauge). */
    public int activeStreams() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }
}
