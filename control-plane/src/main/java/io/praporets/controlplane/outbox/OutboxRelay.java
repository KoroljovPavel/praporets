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
 * Доставляє події з outbox у Kafka: планувальник кожні
 * {@code praporets.outbox.relay.poll-interval} захоплює пачку через
 * {@code FOR UPDATE SKIP LOCKED} і публікує. Семантика at-least-once —
 * падіння між send і commit дасть повтор, дедуп на споживачах за ревізією.
 *
 * <p>У конструкторі реєструється Micrometer-гейдж
 * {@code praporets.outbox.lag.seconds} (Prometheus-експортер перетворить на
 * {@code praporets_outbox_lag_seconds}) — вік найстарішої неопублікованої
 * події в секундах, 0 якщо черга порожня.
 *
 * <p>{@code @Transactional} стоїть і на {@code scheduledTick()} свідомо:
 * самовиклик {@code this.relayBatch()} зсередини того ж біна обходить
 * Spring-проксі, тож анотація на самому {@code relayBatch()} для
 * планувальника не спрацювала б — лок відпускався б одразу після SELECT і
 * SKIP LOCKED ставав би беззубим. Планувальник викликає {@code scheduledTick}
 * через проксі, і вкладений {@code relayBatch} приєднується до його
 * транзакції.
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

    /**
     * Публікує одну пачку: захоплює до {@code batch-size} найстаріших
     * неопублікованих рядків і відправляє кожен у Kafka з заголовком
     * {@code schema-version: 1} та <b>синхронним</b> очікуванням ack
     * (5 секунд) — лише після ack рядок позначається опублікованим.
     * Помилка або таймаут відправки → warn-лог і негайний вихід із циклу:
     * наступні рядки того ж середовища не повинні обігнати проблемний,
     * наступний тік повторить спробу.
     *
     * <p>Public і {@code @Transactional} — інтеграційні тести викликають
     * його напряму (плановий relay у тестових properties вимкнений).
     *
     * @return кількість успішно опублікованих подій
     */
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
