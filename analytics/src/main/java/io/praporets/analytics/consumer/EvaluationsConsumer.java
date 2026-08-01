package io.praporets.analytics.consumer;

import io.praporets.analytics.common.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Консюмер {@code praporets.flag.evaluations.v1} (A-01): парсинг →
 * {@link EvaluationProcessor} → ack. Група і offset-reset — з
 * {@code application.yaml} (G2: спільна група, earliest).
 *
 * <p><b>Залежності для інжекту:</b> {@link EvaluationProcessor},
 * {@code JsonMapper} (Jackson 3, бін Boot).
 *
 * <p><b>{@code onEvaluation} (твоя робота):</b>
 * <ol>
 *   <li>header {@code schema-version}: відсутній або не {@code "1"} →
 *       КИНУТИ виняток (G7 — на відміну від 03b це дані, їм місце в DLT,
 *       звідки їх можна переграти після апгрейду);</li>
 *   <li>{@code jsonMapper.readValue(record.value(),
 *       EvaluationEventPayload.class)};</li>
 *   <li>{@code processor.process(payload)};</li>
 *   <li>{@code ack.acknowledge()} — ТІЛЬКИ якщо все вище пройшло
 *       (камінь #2).</li>
 * </ol>
 * НІЯКИХ try/catch (G5, дзеркальна протилежність F5 з 03b): будь-який
 * виняток летить у {@code DefaultErrorHandler} → ретраї → DLT; ack не
 * відбувається, тож at-least-once гарантований.
 */
@Component
public class EvaluationsConsumer {

    JsonMapper jsonMapper;
    EvaluationProcessor evaluationProcessor;

    public EvaluationsConsumer(JsonMapper jsonMapper, EvaluationProcessor evaluationProcessor) {
        this.jsonMapper = jsonMapper;
        this.evaluationProcessor = evaluationProcessor;
    }

    @KafkaListener(id = "analytics-evaluations", topics = KafkaTopics.FLAG_EVALUATIONS)
    public void onEvaluation(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Header header = record.headers().lastHeader("schema-version");
        if (header == null || !Arrays.equals(header.value(), "1".getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid schema version");
        }

        EvaluationEventPayload payload = jsonMapper.readValue(record.value(), EvaluationEventPayload.class);

        evaluationProcessor.process(payload);
        ack.acknowledge();
    }
}
