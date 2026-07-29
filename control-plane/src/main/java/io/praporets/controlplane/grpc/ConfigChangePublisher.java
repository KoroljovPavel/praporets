package io.praporets.controlplane.grpc;

import io.praporets.controlplane.service.ConfigChangedEvent;
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
    private final ConfigStreamRegistry registry;

    public ConfigChangePublisher(DeltaAssembler deltaAssembler, ConfigStreamRegistry registry) {
        this.registry = registry;
        this.deltaAssembler = deltaAssembler;
    }

    @TransactionalEventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }
}
