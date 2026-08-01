package io.praporets.controlplane.grpc;

import com.google.protobuf.util.JsonFormat;
import io.praporets.controlplane.common.KafkaTopics;
import io.praporets.grpc.config.v1.ConfigDelta;
import io.praporets.grpc.config.v1.ConfigUpdate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Fan-out (CP-10): консюмер {@code praporets.flag.changes.v1} у КОЖНІЙ
 * репліці CP. Заміняє видалений {@code ConfigChangePublisher}: єдине джерело
 * live-пушу в стріми тепер Kafka, і код однаковий для всіх реплік — репліка,
 * що сама зробила зміну, теж отримає її з топіка і запушить своїм стрімам.
 *
 * <p>Група унікальна на процес ({@code cp-fanout-${random.uuid}}, F2) —
 * кожна репліка читає ВСІ повідомлення. {@code auto-offset-reset: latest}
 * (F3) — історію знає БД, консюмер тільки для live.
 *
 * <p><b>Залежності для інжекту:</b> {@code ConfigStreamRegistry},
 * {@code JsonMapper} (Jackson 3, бін уже є).
 *
 * <p><b>Реалізація {@code onFlagChange} (твоя робота):</b>
 * <ol>
 *   <li><b>Header-гейт (F5):</b> {@code record.headers().lastHeader("schema-version")}
 *       відсутній або не {@code "1"} → warn-лог і return — форвард-сумісність:
 *       майбутній формат v2 не повинен класти всі старі репліки;</li>
 *   <li><b>Ранній вихід:</b> environmentKey = {@code record.key()}; якщо
 *       {@code registry.activeEnvironments()} його не містить — return
 *       (не парсити payload заради нікого);</li>
 *   <li><b>Парсинг payload (K3, F4):</b> {@code jsonMapper.readTree(record.value())}
 *       → поля {@code revision} (long) і {@code delta} (об'єкт);
 *       дельту — БЕЗ походу в БД: {@code JsonFormat.parser().merge(deltaNode.toString(),
 *       builder)} у {@code ConfigDelta.Builder} (дзеркало
 *       {@code JsonFormat.printer()} з OutboxWriter);</li>
 *   <li>{@code registry.publish(env, ConfigUpdate{revision, delta})} — реєстр
 *       сам серіалізує onNext і прибирає мертві стріми;</li>
 *   <li><b>Помилки парсингу (F5):</b> будь-який виняток ловити ТУТ (error-лог,
 *       return) — виняток назовні = 10 ретраїв DefaultErrorHandler-а, тобто
 *       секунди блокованої партиції заради події, яка не стане валіднішою.</li>
 * </ol>
 *
 * <p>id слухача фіксований — тести чекають assignment партицій через
 * {@code KafkaListenerEndpointRegistry.getListenerContainer("flag-changes-fanout")}
 * (камінь #1: {@code latest} + продюс до assignment-у = загублене повідомлення).
 */
@Component
public class FlagChangesConsumer {

    private final JsonMapper jsonMapper;
    private final ConfigStreamRegistry configStreamRegistry;

    private static final Logger log = LoggerFactory.getLogger(FlagChangesConsumer.class);

    public FlagChangesConsumer(JsonMapper jsonMapper, ConfigStreamRegistry configStreamRegistry) {
        this.jsonMapper = jsonMapper;
        this.configStreamRegistry = configStreamRegistry;
    }

    @KafkaListener(
        id = "flag-changes-fanout",
        topics = KafkaTopics.FLAG_CHANGES,
        groupId = "${praporets.fanout.group-id}",
        autoStartup = "${praporets.fanout.enabled:true}"
    )
    public void onFlagChange(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("schema-version");
        if (header == null || !Arrays.equals(header.value(), "1".getBytes(StandardCharsets.UTF_8))) {
            log.warn("Flag change received for unknown schema-version header");
            return;
        }
        if (!configStreamRegistry.activeEnvironments().contains(record.key())) {
            log.debug("Flag change received for unknown environment {}", record.key());
            return;
        }

        try {
            JsonNode rawPayload = jsonMapper.readTree(record.value());
            long revision = rawPayload.get("revision").asLong();

            ConfigDelta.Builder builder = ConfigDelta.newBuilder();
            JsonFormat.parser().merge(rawPayload.get("delta").toString(), builder);
            configStreamRegistry.publish(record.key(), ConfigUpdate.newBuilder().setRevision(revision).setDelta(builder.build()).build());
        } catch (Exception e) {
            log.error("Failed to process config change for environment {}, topic {}, partition {}, offset {}", record.key(), record.topic(), record.partition(), record.offset(), e);
        }
    }
}
