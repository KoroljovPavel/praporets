package io.praporets.edge.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

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

    @Inject
    EvaluationEvents evaluationEvents;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry meterRegistry;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "praporets.edge.events.enabled")
    boolean eventsEnabled;

    @ConfigProperty(name = "praporets.edge.events.batch-size")
    int batchSize;

    private static final byte[] SCHEMA_VERSION_1 = "1".getBytes(StandardCharsets.UTF_8);

    private KafkaProducer<String, String> producer;

    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("edge-events-drainer");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean stopped = false;
    private Future<?> workerFuture;

    /**
     * Топік подій (спека §6.4); key = flagKey.
     */
    public static final String TOPIC = "praporets.flag.evaluations.v1";

    void onStart(@Observes StartupEvent event) {
        if (!eventsEnabled) return;

        Counter.builder(EvaluationEvents.DROPPED_METRIC).tag("reason", "send_failed").register(meterRegistry);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5100);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 100);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);
        producer = new KafkaProducer<>(props);

        workerFuture = singleThreadExecutor.submit(this::runLoop);
    }

    private void runLoop() {
        List<EvaluationEvent> events = new ArrayList<>(batchSize);

        while (!stopped && !Thread.currentThread().isInterrupted()) {
            try {
                processBatch(events);
            } catch (InterruptedException e) {
                Log.info("Drainer thread interrupted, shutting down...");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.error("Error while processing events", e);
            } finally {
                events.clear();
            }
        }
    }

    private void processBatch(List<EvaluationEvent> batch) throws InterruptedException {
        EvaluationEvent first = evaluationEvents.poll(100);

        if (first == null) return;

        batch.add(first);

        if (batchSize > 1) {
            evaluationEvents.drainTo(batch, batchSize - 1);
        }

        for (EvaluationEvent event : batch) {
            sendToKafka(event);
        }
    }

    private long drainRemainingEvents() {
        List<EvaluationEvent> remainder = new ArrayList<>(batchSize);
        evaluationEvents.drainTo(remainder, Integer.MAX_VALUE);

        if (remainder.isEmpty()) return 0;

        Log.info(String.format("Draining final %s events from queue before shutdown...", remainder.size()));
        for (EvaluationEvent event : remainder) {
            sendToKafka(event);
        }

        return remainder.size();
    }

    private void sendToKafka(EvaluationEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);

            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, event.flagKey(), json);
            record.headers().add(new RecordHeader("schema-version", SCHEMA_VERSION_1));

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    meterRegistry.counter(EvaluationEvents.DROPPED_METRIC, Tags.of(Tag.of("reason", "send_failed"))).increment();
                    Log.warn(String.format("Failed to send event to kafka for flagKey=%s: %s", event.flagKey(), exception.getMessage()));
                }
            });
        } catch (Exception e) {
            meterRegistry.counter(EvaluationEvents.DROPPED_METRIC, Tags.of(Tag.of("reason", "send_failed"))).increment();
            Log.warn(String.format("Failed to serialize or prepare event for flagKey=%s: %s", event.flagKey(), e.getMessage()));
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (stopped || workerFuture == null) return;

        Log.info("Stopping Evaluation Events Drainer worker...");

        stopped = true;

        try {
            workerFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            Log.warn("Worker thread did not finish within timeout, forcing cancel...");
            workerFuture.cancel(true);
        } catch (InterruptedException | ExecutionException e) {
            Log.error("Error waiting for worker thread termination", e);
            Thread.currentThread().interrupt();
        }

        long drainedOnShutDown = drainRemainingEvents();

        try {
            producer.flush();
        } catch (Exception e) {
            Log.warn("Error while flushing producer", e);
        }

        try {
            producer.close();
        } catch (Exception e) {
            Log.warn("Error while closing producer", e);
        }

        Log.info(String.format("Evaluation Events Drainer stopped. Drained %s remaining events during shutdown sequence.", drainedOnShutDown));

        singleThreadExecutor.shutdown();
    }
}
