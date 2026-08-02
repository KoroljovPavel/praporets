package io.praporets.analytics.deviation;

import io.praporets.analytics.common.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Очікувані ваги rollout-ів у пам'яті (I1): слухач compacted
 * {@code praporets.flag.changes.v1} — той самий K3-JSON, що читають
 * репліки CP у fan-out. Unique group на процес + earliest → на старті
 * компакшн віддає повний стан конфігурації, далі — live-зміни.
 *
 * <p><b>Залежності для інжекту:</b> {@code JsonMapper} (Jackson 3),
 * стан — {@code ConcurrentHashMap<String, Map<String, Map<String, Integer>>>}
 * (env → flagKey → variantKey → weight у стотисячних).
 *
 * <p><b>{@code onFlagChange} (твоя робота):</b>
 * <ol>
 *   <li>гейт {@code schema-version != "1"} → warn і return (як 03b);</li>
 *   <li>{@code readTree(record.value())} → {@code environmentKey} і
 *       {@code delta};</li>
 *   <li>{@code delta.upsertedFlags[]}: флаг МАЄ {@code rollout} →
 *       покласти мапу {@code buckets[].variantKey → weight}; НЕ має →
 *       зняти запис флага (камінь #2: дельта віддає флаг цілком, upsert =
 *       повна заміна, «мертві» ваги жити не повинні);</li>
 *   <li>{@code delta.removedFlagKeys[]} → зняти;</li>
 *   <li>усі помилки ловити ВСЕРЕДИНІ (error-лог + return): це третій
 *       консюмер проекту з семантикою «скіп» — стан відновлюваний
 *       компакшном (I1), а виняток назовні блокував би партицію.</li>
 * </ol>
 *
 * <p>ack тут не потрібен — дефолтний ack-mode перекритий на manual
 * глобально, тож у {@code @KafkaListener} сигнатурі БЕРИ
 * {@code Acknowledgment} і ack-ай одразу: стан у пам'яті, офсети групи
 * все одно одноразові (unique group).
 */
@Component
public class RolloutExpectations {

    @KafkaListener(
        id = "analytics-rollout-expectations",
        topics = KafkaTopics.FLAG_CHANGES,
        groupId = "${praporets.analytics.deviation.group-id}",
        properties = "auto.offset.reset=earliest"
    )
    public void onFlagChange(ConsumerRecord<String, String> record,
                             org.springframework.kafka.support.Acknowledgment ack) {
        throw new UnsupportedOperationException("03d-3: твоя реалізація");
    }

    /**
     * Очікувані ваги флага (variantKey → weight у стотисячних);
     * порожній Optional — флаг без rollout-а або невідомий.
     */
    public Optional<Map<String, Integer>> weightsFor(String environment, String flagKey) {
        throw new UnsupportedOperationException("03d-3: твоя реалізація");
    }
}
