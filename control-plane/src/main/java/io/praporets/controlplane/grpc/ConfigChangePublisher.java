package io.praporets.controlplane.grpc;

import io.praporets.controlplane.service.ConfigChangedEvent;
import io.praporets.grpc.config.v1.ConfigDelta;
import io.praporets.grpc.config.v1.ConfigUpdate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Live-пуш змін у відкриті стріми цієї репліки: слухає
 * {@link ConfigChangedEvent} і після коміту транзакції шле дельту всім
 * підписникам середовища (спека §7.3, останній блок).
 *
 * <p>Чому {@code AFTER_COMMIT}: слати ДО коміту — розіслати edge-ам зміну,
 * яку транзакція ще може відкотити. Ціна: слухач працює вже поза
 * транзакцією, тож {@code DeltaAssembler} відкриває свою readOnly.
 *
 * <p><b>Реалізація (твоя робота):</b> якщо
 * {@code registry.activeEnvironments()} не містить середовища — вийти
 * (не ходити в БД заради нікого); інакше
 * {@code delta = deltaAssembler.assembleSince(env, event.revision() - 1)}
 * (дельта рівно однієї ревізії) → {@code registry.publish(env,
 * ConfigUpdate{revision = event.revision(), delta})}.
 *
 * <p><b>Шов для етапу 3 (CP-09/CP-10):</b> зараз пуш локальний — інші репліки
 * CP цю зміну не побачать. У 03a та сама транзакція почне писати в outbox, у
 * 03b дельти поїдуть через Kafka {@code praporets.flag.changes.v1}, і на
 * місце цього слухача стане консюмер топіка в КОЖНІЙ репліці. Реєстр і
 * {@code publish} лишаться незмінними — міняється лише джерело події.
 */
@Component
public class ConfigChangePublisher {

    private final DeltaAssembler deltaAssembler;
    private final ConfigStreamRegistry configStreamRegistry;

    public ConfigChangePublisher(DeltaAssembler deltaAssembler, ConfigStreamRegistry configStreamRegistry) {
        this.deltaAssembler = deltaAssembler;
        this.configStreamRegistry = configStreamRegistry;
    }

    @TransactionalEventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        if (!configStreamRegistry.activeEnvironments().contains(event.environmentKey()))
            return;

        ConfigDelta delta = deltaAssembler.assembleSince(event.environmentKey(), event.revision() - 1);
        configStreamRegistry.publish(event.environmentKey(), ConfigUpdate.newBuilder()
            .setRevision(event.revision())
            .setDelta(delta)
            .build());
    }
}
