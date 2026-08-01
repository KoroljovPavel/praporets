package io.praporets.edge.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.praporets.core.evaluation.EvaluationResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Гарячий шлях подій (E-05): збирає {@link EvaluationEvent} із результату
 * обчислення і кладе в bounded-чергу. НІКОЛИ не блокує і не кидає — обчислення
 * важливіше за аналітику (E1). Дренує чергу {@link EvaluationEventPublisher}.
 *
 * <p><b>Залежності для інжекту (конструктор — твоя робота):</b>
 * <ul>
 *   <li>{@code @ConfigProperty praporets.edge.environment} → поле події;</li>
 *   <li>{@code @ConfigProperty praporets.edge.instance-id} → поле події;</li>
 *   <li>{@code @ConfigProperty praporets.edge.events.buffer-size} → ємність
 *       {@code ArrayBlockingQueue<EvaluationEvent>};</li>
 *   <li>{@code MeterRegistry} → лічильник
 *       {@code flag_edge_events_dropped_total} з тегом
 *       {@code reason="buffer_full"} (E3; зареєструй у конструкторі, як
 *       гейджі в {@code SyncMetrics} — лічильник видимий і коли нулю).</li>
 * </ul>
 *
 * <p><b>{@code emit} (твоя робота):</b> побудувати подію —
 * {@link Uuid7#generate()}, {@code Instant.now()}, SHA-256 hex від
 * {@code userKey} (E5: сирий ключ НЕ зберігати ніде, хеш рахується тут же;
 * {@code MessageDigest.getInstance("SHA-256")} на виклик — дешево),
 * {@code reason().name()}, поля з результату — і {@code offer()} у чергу.
 * {@code offer() == false} → інкремент лічильника, повернути false. Жодних
 * {@code put()} (блокує) чи {@code add()} (кидає) — камінь #4.
 *
 * <p><b>{@code drainTo} (твоя робота):</b> тонка обгортка над
 * {@code queue.drainTo(sink, max)} — для дренера і тестів.
 */
@ApplicationScoped
public class EvaluationEvents {

    @ConfigProperty(name = "praporets.edge.environment")
    String environment;

    @ConfigProperty(name = "praporets.edge.instance-id")
    String instanceId;

    MeterRegistry meterRegistry;

    private final ArrayBlockingQueue<EvaluationEvent> events;

    public EvaluationEvents(@ConfigProperty(name = "praporets.edge.events.buffer-size") int bufferSize, MeterRegistry meterRegistry) {
        events = new ArrayBlockingQueue<>(bufferSize);
        this.meterRegistry = meterRegistry;
        Counter.builder(DROPPED_METRIC).tag("reason", "buffer_full").register(meterRegistry);
    }

    /**
     * Ім'я лічильника втрат (E3); теги: {@code reason=buffer_full|send_failed}.
     */
    public static final String DROPPED_METRIC = "flag_edge_events_dropped_total";

    /**
     * Кладе подію обчислення в буфер; при переповненні — рахує втрату і
     * повертає {@code false}. O(1), без блокувань і винятків.
     *
     * @param result   результат обчислення (будь-який reason, включно з
     *                 FLAG_NOT_FOUND — E8)
     * @param revision ревізія конфігурації, на якій рахували
     * @param userKey  сирий ключ користувача — тут же хешується, далі не йде
     * @return {@code true}, якщо подія в буфері; {@code false} — втрачена
     */
    public boolean emit(EvaluationResult result, long revision, String userKey) {

        boolean offer = events.offer(new EvaluationEvent(
            Uuid7.generate(), Instant.now(), environment, result.flagKey(), result.variantKey(), result.reason().name(),
            result.ruleId(), revision, getSha256(userKey), instanceId
        ));

        if (!offer)
            meterRegistry.counter(DROPPED_METRIC, Tags.of(Tag.of("reason", "buffer_full"))).increment();

        return offer;
    }

    /**
     * Зливає до {@code max} подій у {@code sink} (порядок — FIFO).
     *
     * @return скільки подій злито
     */
    public int drainTo(List<EvaluationEvent> sink, int max) {
        return events.drainTo(sink, max);
    }

    /**
     *
     * @param timeout in ms
     * @return EvaluationEvent
     * @throws InterruptedException
     */
    protected EvaluationEvent poll(long timeout) throws InterruptedException {
        return events.poll(timeout, TimeUnit.MILLISECONDS);
    }

    private String getSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm SHA-256 not found", e);
        }
    }
}
