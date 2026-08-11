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
 * Fan-out змін між репліками CP: консюмер {@code praporets.flag.changes.v1}
 * у КОЖНІЙ репліці. Kafka — єдине джерело live-пушу в gRPC-стріми, і код
 * однаковий для всіх реплік: репліка, що сама зробила зміну, теж отримає її
 * з топіка і запушить своїм стрімам.
 *
 * <p>Consumer group унікальна на процес ({@code cp-fanout-${random.uuid}}) —
 * кожна репліка читає ВСІ повідомлення, а не ділить партиції з сусідами.
 * {@code auto-offset-reset: latest} — історію знає БД (edge добере її через
 * snapshot/catch-up), консюмер потрібен тільки для live.
 *
 * <p>Обробка повідомлення:
 * <ol>
 *   <li><b>header-гейт:</b> {@code schema-version} відсутній або не
 *       {@code "1"} → warn-лог і return — форвард-сумісність: майбутній
 *       формат v2 не повинен класти всі старі репліки;</li>
 *   <li><b>ранній вихід:</b> якщо на середовище ({@code record.key()}) немає
 *       жодного підписника — payload навіть не парситься;</li>
 *   <li>з payload читаються {@code revision} і {@code delta}; дельта
 *       відновлюється в {@code ConfigDelta} прямо з JSON, без походу в БД
 *       (дзеркало {@code JsonFormat.printer()} з {@code OutboxWriter});</li>
 *   <li>{@code registry.publish(...)} — реєстр сам серіалізує onNext і
 *       прибирає мертві стріми;</li>
 *   <li><b>помилки обробки</b> ловляться тут же (error-лог, return) —
 *       виняток назовні означав би ретраї {@code DefaultErrorHandler}-а,
 *       тобто секунди блокованої партиції заради події, яка не стане
 *       валіднішою.</li>
 * </ol>
 *
 * <p>id слухача фіксований — тести чекають assignment партицій через
 * {@code KafkaListenerEndpointRegistry.getListenerContainer("flag-changes-fanout")},
 * бо з {@code latest} продюс до assignment-у означає загублене повідомлення.
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
