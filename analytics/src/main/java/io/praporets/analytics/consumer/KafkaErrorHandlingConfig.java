package io.praporets.analytics.consumer;

import org.springframework.context.annotation.Configuration;

/**
 * DLT-маршрутизація (G5, A-05): помилка обробки після ретраїв відправляє
 * ОРИГІНАЛЬНИЙ запис у {@code praporets.flag.evaluations.v1.dlt} і партиція
 * їде далі.
 *
 * <p><b>Бін (твоя робота):</b> {@code DefaultErrorHandler} —
 * Boot сам підхоплює його для всіх listener-контейнерів:
 * <pre>
 * &#64;Bean
 * DefaultErrorHandler kafkaErrorHandler(KafkaTemplate&lt;String, String&gt; template) {
 *     var recoverer = new DeadLetterPublishingRecoverer(template,
 *         (record, ex) -&gt; new TopicPartition(KafkaTopics.FLAG_EVALUATIONS_DLT, 0));
 *     return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3L));
 * }
 * </pre>
 * Деталі:
 * <ul>
 *   <li>резолвер призначення явний: дефолтний ліпить суфікс {@code .DLT}
 *       і ту САМУ партицію оригіналу (у нас 6 → у DLT-топіку її нема) —
 *       тому {@code (topic, 0)} руками;</li>
 *   <li>{@code FixedBackOff(500, 3)} = 4 спроби сумарно (камінь #6):
 *       достатньо пережити транзієнтний збій БД, не тримаючи партицію;</li>
 *   <li>recoverer сам додає діагностичні header-и (exception, original
 *       topic/offset) — нічого не пакувати руками (камінь #5).</li>
 * </ul>
 */
@Configuration
public class KafkaErrorHandlingConfig {
}
