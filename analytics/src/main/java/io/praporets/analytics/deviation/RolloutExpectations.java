package io.praporets.analytics.deviation;

import io.praporets.analytics.common.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    private final JsonMapper jsonMapper;

    private final ConcurrentHashMap<String, Map<String, Map<String, Integer>>> state = new ConcurrentHashMap<>();
    private final Logger log = LoggerFactory.getLogger(RolloutExpectations.class);

    public RolloutExpectations(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
        id = "analytics-rollout-expectations",
        topics = KafkaTopics.FLAG_CHANGES,
        groupId = "${praporets.analytics.deviation.group-id}",
        properties = "auto.offset.reset=earliest"
    )
    public void onFlagChange(ConsumerRecord<String, String> record,
                             Acknowledgment ack) {
        ack.acknowledge();

        Header header = record.headers().lastHeader("schema-version");
        if (header == null || !Arrays.equals(header.value(), "1".getBytes(StandardCharsets.UTF_8))) {
            log.warn("Flag change received for unknown schema-version header");
            return;
        }

        try {
            JsonNode node = jsonMapper.readTree(record.value());
            String environment = node.get("environmentKey").asString();

            Map<String, Map<String, Integer>> envState = state.computeIfAbsent(environment, k -> new HashMap<>());

            JsonNode delta = node.get("delta");

            for (JsonNode upsertedFlag : delta.get("upsertedFlags")) {
                JsonNode rollout = upsertedFlag.get("rollout");
                if (rollout != null) {
                    Map<String, Integer> rolloutMap = new HashMap<>();
                    JsonNode buckets = rollout.get("buckets").asArray();
                    for (JsonNode bucket : buckets) {
                        rolloutMap.put(bucket.get("variantKey").asString(), bucket.get("weight").asInt());
                    }
                    envState.put(upsertedFlag.get("key").asString(), rolloutMap);
                } else {
                    envState.remove(upsertedFlag.get("key").asString());
                }
            }

            JsonNode removedFlagKeys = delta.get("removedFlagKeys");
            if (removedFlagKeys != null) {
                for (JsonNode key : removedFlagKeys) {
                    envState.remove(key.asString());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process flag change for environment {}, topic {}, partition {}, offset {}", record.key(), record.topic(), record.partition(), record.offset(), e);
        }
    }

    /**
     * Очікувані ваги флага (variantKey → weight у стотисячних);
     * порожній Optional — флаг без rollout-а або невідомий.
     */
    public Optional<Map<String, Integer>> weightsFor(String environment, String flagKey) {
        Map<String, Map<String, Integer>> envState = state.get(environment);
        if (envState == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(envState.get(flagKey)).map(Map::copyOf);
    }
}
