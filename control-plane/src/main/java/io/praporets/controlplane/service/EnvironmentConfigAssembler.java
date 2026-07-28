package io.praporets.controlplane.service;

import io.praporets.controlplane.domain.FlagConfigRepository;
import io.praporets.controlplane.domain.SegmentRepository;
import io.praporets.core.model.EnvironmentConfig;
import org.springframework.stereotype.Component;

/**
 * Збірка {@link EnvironmentConfig} (вхід ядра) з БД — H1. Окремий компонент,
 * а не приватний метод preview-сервіса: у 02b рівно цей самий код збиратиме
 * снапшоти для gRPC-стріму edge (там додасться метод «усі флаги середовища»).
 *
 * <p><b>Реалізація (твоя робота), {@link #assembleForFlag}:</b>
 * <ol>
 *   <li>{@code FlagConfigRepository.findByFlagKeyAndEnvironmentKey}:
 *       конфігурації немає → {@code flags = Map.of()} — ядро само поверне
 *       {@code FLAG_NOT_FOUND} (H2), це НЕ помилка;</li>
 *   <li>є → {@code FlagDefinition(flagKey, enabled, defaultVariant, offVariant,
 *       variants, rules, rollout)}. Entity {@code Variant.getValue()} (String
 *       з JSONB) → core {@code Variant(key, jsonValue)} напряму, без парсингу;
 *       {@code rules}/{@code rollout} з entity — вже core-records, віддавай як є;</li>
 *   <li><b>УСІ</b> сегменти середовища ({@code findAllByEnvironmentKey}) →
 *       core {@code Segment(key, conditions)} — правило {@code IN_SEGMENT}
 *       може вказувати на будь-який.</li>
 * </ol>
 *
 * <p>Ключ мапи = ключ сутності — інакше конструктор {@code EnvironmentConfig}
 * кине IAE (його інваріант). Існування середовища тут НЕ перевіряється —
 * це відповідальність викликача (preview мапить на 404).
 */
@Component
public class EnvironmentConfigAssembler {

    private final FlagConfigRepository flagConfigRepository;
    private final SegmentRepository segmentRepository;

    public EnvironmentConfigAssembler(FlagConfigRepository flagConfigRepository,
                                      SegmentRepository segmentRepository) {
        this.flagConfigRepository = flagConfigRepository;
        this.segmentRepository = segmentRepository;
    }

    /**
     * Конфігурація середовища, обрізана до ОДНОГО флага + всіх сегментів —
     * рівно те, що треба ядру для обчислення цього флага.
     */
    public EnvironmentConfig assembleForFlag(String environmentKey, String flagKey) {
        throw new UnsupportedOperationException("01h: твоя реалізація");
    }
}
