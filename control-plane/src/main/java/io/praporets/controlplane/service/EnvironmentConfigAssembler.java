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
 * Збірка {@link EnvironmentConfig} (входу core-ядра) з БД для preview-обчислення.
 *
 * <p>Якщо конфігурації флага в середовищі немає — флагів у результаті нуль,
 * і ядро само поверне {@code FLAG_NOT_FOUND}: це НЕ помилка збірки. Сегменти
 * беруться <b>усі</b> для середовища — правило {@code IN_SEGMENT} може
 * вказувати на будь-який. Значення варіанта (JSON-рядок із JSONB) іде в core
 * {@code Variant} напряму, без парсингу; {@code rules}/{@code rollout} з
 * entity — вже core-records.
 *
 * <p>Існування середовища тут НЕ перевіряється — це відповідальність
 * викликача (preview мапить на 404).
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
