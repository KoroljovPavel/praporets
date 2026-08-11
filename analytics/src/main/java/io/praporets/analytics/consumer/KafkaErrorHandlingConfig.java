package io.praporets.analytics.consumer;

import io.praporets.analytics.common.KafkaTopics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * DLT-маршрутизація: помилка обробки після ретраїв відправляє
 * ОРИГІНАЛЬНИЙ запис у {@code praporets.flag.evaluations.v1.dlt} і партиція
 * їде далі. Boot сам підхоплює {@code DefaultErrorHandler}-бін для всіх
 * listener-контейнерів.
 *
 * <p>Деталі:
 * <ul>
 *   <li>резолвер призначення явний: дефолтний ліпить суфікс {@code .DLT}
 *       і ту САМУ партицію оригіналу (в основному топіку партицій більше,
 *       ніж у DLT) — тому {@code (topic, 0)} руками;</li>
 *   <li>{@code FixedBackOff(500, 3)} = 4 спроби сумарно: достатньо
 *       пережити транзієнтний збій БД, не тримаючи партицію;</li>
 *   <li>recoverer сам додає діагностичні header-и (exception, original
 *       topic/offset) — руками нічого не пакується.</li>
 * </ul>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, exception) ->
            new TopicPartition(KafkaTopics.FLAG_EVALUATIONS_DLT, 0));

        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3L));
    }
}
