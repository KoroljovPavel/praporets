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
 * Консюмер {@code praporets.flag.evaluations.v1}: парсинг →
 * {@link EvaluationProcessor} → ack. Група і offset-reset — з
 * {@code application.yaml} (спільна група реплік, earliest).
 *
 * <p>У листенері свідомо жодного try/catch: будь-який виняток летить у
 * {@code DefaultErrorHandler} → ретраї → DLT; ack не відбувається,
 * тож at-least-once гарантований.
 */
@Component
public class EvaluationsConsumer {

    JsonMapper jsonMapper;
    EvaluationProcessor evaluationProcessor;

    public EvaluationsConsumer(JsonMapper jsonMapper, EvaluationProcessor evaluationProcessor) {
        this.jsonMapper = jsonMapper;
        this.evaluationProcessor = evaluationProcessor;
    }

    /**
     * Обробляє один запис топіка. Відсутній або невідомий header
     * {@code schema-version} → виняток: evaluation-події — безцінні дані,
     * їм місце в DLT, звідки їх можна переграти після апгрейду консюмера
     * (на відміну від відновлюваного стану, який можна просто скіпнути).
     * Ack — тільки після успішного коміту обробки.
     */
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
