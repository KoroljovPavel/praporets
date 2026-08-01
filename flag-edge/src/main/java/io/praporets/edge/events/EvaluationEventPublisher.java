package io.praporets.edge.events;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Дренер буфера подій (E6): єдиний власник {@code KafkaProducer} і єдине
 * місце, де живе {@code send()} — з гарячого шляху його викликати ЗАБОРОНЕНО
 * (може блокувати до {@code max.block.ms}, камінь #2).
 *
 * <p><b>Залежності для інжекту (конструктор — твоя робота):</b>
 * {@link EvaluationEvents}, {@code ObjectMapper} (бін Quarkus — НЕ
 * {@code new ObjectMapper()}, інакше {@code Instant} серіалізується числом,
 * камінь #6), {@code MeterRegistry} (лічильник {@code send_failed}),
 * {@code @ConfigProperty}: {@code kafka.bootstrap.servers},
 * {@code praporets.edge.events.enabled}, {@code praporets.edge.events.batch-size}.
 *
 * <p><b>{@code onStart} (твоя робота):</b> якщо {@code enabled == false} —
 * тихий return (тестові контексти без Kafka). Інакше: зібрати
 * {@code KafkaProducer<String, String>} (StringSerializer ×2;
 * {@code delivery.timeout.ms=5000} — E7, короткоживучі події) і запустити
 * daemon-тред «edge-events-drainer» із циклом:
 * <ol>
 *   <li>{@code first = queue.poll(100ms)} (через
 *       {@code events.drainTo}? — ні: перший елемент бери блокуючим
 *       {@code poll} з таймаутом, щоб не крутити CPU; додай у
 *       {@code EvaluationEvents} package-private метод або тримай сам цикл
 *       на {@code drainTo} + {@code Thread.sleep(100)} — обидва варіанти
 *       прийнятні, обери і зафіксуй коментарем);</li>
 *   <li>добрати решту: {@code drainTo(batch, batchSize - 1)};</li>
 *   <li>для кожної події: JSON через ObjectMapper →
 *       {@code ProducerRecord(TOPIC, flagKey, json)} + header
 *       {@code schema-version: "1"} → {@code producer.send(record, callback)};
 *       у callback помилка → {@code flag_edge_events_dropped_total{reason="send_failed"}}
 *       + warn-лог (НЕ error на кожну — Kafka може лежати довго);</li>
 *   <li>увесь цикл ітерації — у {@code try/catch}: дренер не має права
 *       померти від одного битого елемента (камінь #7).</li>
 * </ol>
 *
 * <p><b>{@code onStop} (твоя робота, E-09):</b> підняти стоп-прапорець,
 * дочекатись тред ({@code join} з таймаутом), злити залишок буфера в
 * producer, {@code producer.flush()}, {@code producer.close(Duration)};
 * залогувати скільки подій дреновано.
 *
 * <p>Тіла lifecycle-методів ЗАРАЗ порожні свідомо: UOE з
 * {@code @Observes StartupEvent} поклав би застосунок і всі старі тести
 * (камінь #3). Червоними крок тримають тести буфера і публікації.
 */
@ApplicationScoped
public class EvaluationEventPublisher {

    /**
     * Топік подій (спека §6.4); key = flagKey.
     */
    public static final String TOPIC = "praporets.flag.evaluations.v1";

    void onStart(@Observes StartupEvent event) {
        // 03c: твоя реалізація (порожньо, НЕ UOE — див. JavaDoc)
    }

    void onStop(@Observes ShutdownEvent event) {
        // 03c: твоя реалізація (порожньо, НЕ UOE — див. JavaDoc)
    }
}
