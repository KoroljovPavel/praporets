package io.praporets.controlplane.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Доставляє події з outbox у Kafka (K5): планувальник кожні
 * {@code praporets.outbox.relay.poll-interval} захоплює пачку через
 * {@code FOR UPDATE SKIP LOCKED} і публікує. Семантика at-least-once —
 * падіння між send і commit дасть повтор, дедуп на споживачах за ревізією.
 *
 * <p><b>Залежності для інжекту:</b> {@code OutboxRepository},
 * {@code KafkaTemplate<String, String>}, {@code MeterRegistry},
 * {@code @Value}-властивості {@code praporets.outbox.relay.enabled} і
 * {@code praporets.outbox.relay.batch-size}.
 *
 * <p><b>Гейдж (K6, реєструй у конструкторі):</b> Micrometer-ім'я
 * <b>{@code praporets.outbox.lag.seconds}</b> (крапки! Prometheus-експортер
 * сам перетворить на {@code praporets_outbox_lag_seconds}) =
 * age(найстаріший неопублікований) у секундах, 0.0 якщо порожньо — через
 * {@code outboxRepository.findOldestUnpublishedCreatedAt()}.
 *
 * <p><b>УВАГА, самовиклик:</b> {@code this.relayBatch()} зсередини
 * {@code scheduledTick()} обходить Spring-проксі — {@code @Transactional}
 * НЕ спрацює, лок відпуститься одразу після SELECT, і в проді SKIP LOCKED
 * стане беззубим (тести цього не зловлять — вони кличуть relayBatch через
 * проксі!). Найпростіше рішення: познач {@code @Transactional} і сам
 * {@code scheduledTick} — планувальник викликає його через проксі, а
 * вкладений relayBatch приєднається до транзакції.
 *
 * <p><b>{@code relayBatch()} (твоя реалізація):</b>
 * <ol>
 *   <li>{@code lockNextBatch(batchSize)};</li>
 *   <li>для кожного рядка:
 *       {@code kafkaTemplate.send(entry.topic(), entry.partitionKey(),
 *       entry.payload())} із заголовком {@code schema-version: 1}
 *       (через {@code ProducerRecord} + headers) і <b>синхронним</b>
 *       {@code .get(5, SECONDS)} (камінь #2) → {@code published_at = now()};</li>
 *   <li>помилка відправки → warn-лог, рядок НЕ чіпати, вихід із циклу
 *       (порядок! наступні рядки того ж env не повинні обігнати цей) —
 *       наступний тік повторить;</li>
 *   <li>повернути кількість опублікованих.</li>
 * </ol>
 * Метод public і {@code @Transactional} — тести смикають його напряму
 * (relay у тестових properties вимкнений, K7).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private final int batchSize;
    private final boolean relayEnabled;

    public OutboxRelay(MeterRegistry meterRegistry, OutboxRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       @Value("${praporets.outbox.relay.enabled:true}") boolean relayEnabled,
                       @Value("${praporets.outbox.relay.batch-size:100}") int batchSize) {
        this.batchSize = batchSize;
        this.relayEnabled = relayEnabled;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxRepository = outboxRepository;

        Gauge.builder("praporets.outbox.lag.seconds", outboxRepository, repo ->
                repo.findOldestUnpublishedCreatedAt()
                    .map(createdAt -> Duration.between(createdAt, Instant.now()).toSeconds())
                    .orElse(0L))
            .description("Oldest not published event")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${praporets.outbox.relay.poll-interval:200ms}")
    @Transactional
    void scheduledTick() {
        if (relayEnabled)
            relayBatch();
    }

    @Transactional
    public int relayBatch() {
        List<OutboxEntry> outboxEntries = outboxRepository.lockNextBatch(batchSize);

        int countSent = 0;

        for (OutboxEntry entry : outboxEntries) {
            ProducerRecord<String, String> record = new ProducerRecord<>(entry.getTopic(), entry.getPartitionKey(), entry.getPayload());
            record.headers().add("schema-version", "1".getBytes(StandardCharsets.UTF_8));

            try {
                kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
                entry.markPublished(Instant.now());
                countSent++;
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for record to be published", e);
                Thread.currentThread().interrupt();
                return countSent;
            } catch (ExecutionException | TimeoutException e) {
                log.warn("Error publishing event", e);
                return countSent;
            }
        }

        return countSent;
    }
}
