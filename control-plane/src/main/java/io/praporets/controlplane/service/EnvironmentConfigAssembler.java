package io.praporets.controlplane.service;

import io.praporets.controlplane.domain.FlagConfigRepository;
import io.praporets.controlplane.domain.SegmentRepository;
import io.praporets.core.model.EnvironmentConfig;
import io.praporets.core.model.FlagDefinition;
import io.praporets.core.model.Segment;
import io.praporets.core.model.Variant;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

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

    private final SegmentRepository segmentRepository;
    private final FlagConfigRepository flagConfigRepository;

    public EnvironmentConfigAssembler(FlagConfigRepository flagConfigRepository,
                                      SegmentRepository segmentRepository) {
        this.segmentRepository = segmentRepository;
        this.flagConfigRepository = flagConfigRepository;
    }

    /**
     * Конфігурація середовища, обрізана до ОДНОГО флага + всіх сегментів —
     * рівно те, що треба ядру для обчислення цього флага.
     */
    public EnvironmentConfig assembleForFlag(String environmentKey, String flagKey) {
        Map<String, FlagDefinition> flags = flagConfigRepository.findByFlagKeyAndEnvironmentKey(flagKey, environmentKey)
            .map(f -> Map.of(flagKey, new FlagDefinition(
                flagKey, f.isEnabled(), f.getDefaultVariant(), f.getOffVariant(),
                f.getFlag().getVariants().stream().map(v -> new Variant(v.getKey(), v.getValue())).toList(),
                f.getRules(), f.getRollout()
            ))).orElse(Map.of());

        Map<String, Segment> segmentMap = segmentRepository.findAllByEnvironmentKey(environmentKey).stream()
            .map(s -> new io.praporets.core.model.Segment(s.getKey(), s.getConditions()))
            .collect(Collectors.toMap(io.praporets.core.model.Segment::key, s -> s));

        return new EnvironmentConfig(flags, segmentMap);
    }
}
