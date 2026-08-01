package io.praporets.controlplane.outbox;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.praporets.controlplane.common.KafkaTopics;
import io.praporets.controlplane.domain.Environment;
import io.praporets.controlplane.domain.EnvironmentRepository;
import io.praporets.controlplane.grpc.DeltaAssembler;
import io.praporets.controlplane.service.ConfigChangedEvent;
import io.praporets.grpc.config.v1.ConfigDelta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.util.RawValue;

import java.util.Optional;

/**
 * Пише подію зміни в outbox У ТІЙ САМІЙ транзакції, що й сама зміна (CP-09,
 * K4). Дзеркальний близнюк {@code FlagChangesConsumer}: той слухає Kafka topic
 * {@code KafkaTopics.FLAG_CHANGES} і пушить у локальні стріми, цей — {@code BEFORE_COMMIT}
 * і пише в БД. Відкат транзакції відкочує і outbox-рядок — атомарність
 * «зміна + подія» без розподілених транзакцій.
 *
 * <p><b>Залежності для інжекту:</b> {@code DeltaAssembler} (грінка з grpc-
 * пакета — збірка дельти рівно однієї ревізії), {@code OutboxRepository},
 * {@code EnvironmentRepository} (за aggregate_id середовища).
 *
 * <p><b>Реалізація (твоя):</b>
 * <ol>
 *   <li>{@code delta = deltaAssembler.assembleSince(env, event.revision() - 1)}
 *       — ми ВСЕРЕДИНІ транзакції, щойно вставлений revision_log видимий;</li>
 *   <li>payload за K3: {@code {"environmentKey": ..., "revision": ...,
 *       "delta": <JsonFormat.printer().print(delta)>}} — зручно зібрати
 *       рядком через Jackson ObjectNode + {@code putRawValue}, або конкатенацією
 *       з JsonFormat (дельта вже валідний JSON);</li>
 *   <li>{@code outboxRepository.save(new OutboxEntry(...))} з topic =
 *       {@code KafkaTopics.FLAG_CHANGES}, partition_key = environmentKey.</li>
 * </ol>
 */
@Component
public class OutboxWriter {

    private final ObjectMapper objectMapper;
    private final DeltaAssembler deltaAssembler;
    private final OutboxRepository outboxRepository;
    private final EnvironmentRepository environmentRepository;

    public OutboxWriter(DeltaAssembler deltaAssembler, OutboxRepository outboxRepository, ObjectMapper objectMapper,
                        EnvironmentRepository environmentRepository) {
        this.objectMapper = objectMapper;
        this.deltaAssembler = deltaAssembler;
        this.outboxRepository = outboxRepository;
        this.environmentRepository = environmentRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onConfigChanged(ConfigChangedEvent event) {
        Optional<Environment> optionalEnvironment = environmentRepository.findByKey(event.environmentKey());
        if (optionalEnvironment.isPresent()) {
            Environment environment = optionalEnvironment.get();
            ConfigDelta delta = deltaAssembler.assembleSince(event.environmentKey(), event.revision() - 1);

            ObjectNode node = objectMapper.createObjectNode();
            node.put("environmentKey", environment.getKey());
            node.put("revision", event.revision());

            try {
                node.putRawValue("delta", new RawValue(JsonFormat.printer().print(delta)));
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalStateException(e);
            }

            OutboxEntry outboxEntry = new OutboxEntry(environment.getId(), KafkaTopics.FLAG_CHANGES, environment.getKey(), node.toString());

            outboxRepository.save(outboxEntry);
        }
    }
}
