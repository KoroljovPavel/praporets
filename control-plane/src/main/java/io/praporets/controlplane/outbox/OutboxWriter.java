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
 * Пише подію зміни конфігурації в outbox У ТІЙ САМІЙ транзакції, що й сама
 * зміна: слухає {@link ConfigChangedEvent} у фазі {@code BEFORE_COMMIT}, тож
 * відкат транзакції відкочує і outbox-рядок — атомарність «зміна + подія»
 * без розподілених транзакцій. Дзеркальний близнюк
 * {@code FlagChangesConsumer}: той читає {@code KafkaTopics.FLAG_CHANGES}
 * і пушить у локальні gRPC-стріми, цей — пише в БД, звідки {@link OutboxRelay}
 * доставить подію в Kafka.
 *
 * <p>Дельта збирається через {@code DeltaAssembler.assembleSince(env,
 * revision - 1)} — виклик іде всередині ще не закомміченої транзакції, тому
 * щойно вставлений revision_log уже видимий. Payload — JSON виду
 * {@code {"environmentKey": ..., "revision": ..., "delta": <protobuf-JSON>}};
 * дельта вставляється як {@code RawValue}, бо {@code JsonFormat} вже видає
 * валідний JSON. Topic — {@code KafkaTopics.FLAG_CHANGES}, partition key —
 * environmentKey (зберігає порядок подій у межах середовища).
 *
 * <p>Якщо середовище з події не знайдено — подія мовчки ігнорується.
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
