package io.praporets.controlplane.grpc;

import io.praporets.controlplane.domain.FlagConfig;
import io.praporets.grpc.config.v1.FlagDefinition;
import io.praporets.grpc.config.v1.SegmentDefinition;
import org.springframework.stereotype.Component;

/**
 * Мапінг доменного стану (entity + core-records із JSONB) → proto-повідомлення
 * {@code praporets.config.v1}. Stateless, без транзакцій — чиста трансформація.
 *
 * <p><b>Реалізація (твоя робота).</b> Core-records дзеркалять proto 1:1, тож
 * мапінг механічний, але зверни увагу:
 * <ul>
 *   <li>енуми мапляться <b>за ім'ям</b>: {@code
 *       io.praporets.grpc.config.v1.Operator.valueOf(coreOperator.name())} і
 *       так само domain {@code ValueType} → proto {@code ValueType}. Що ім'я
 *       існує в proto — гарантує drift-guard {@code CoreProtoAlignmentTest}
 *       з 02a;</li>
 *   <li>у proto немає {@code null}: відсутній {@code rollout} (флага або
 *       правила) — просто НЕ викликай {@code setRollout(...)}, читач побачить
 *       {@code hasRollout() == false}. Те саме для порожнього
 *       {@code rule_id};</li>
 *   <li>entity {@code Variant.getValue()} — вже канонічний JSON-рядок із
 *       JSONB → прямо в {@code json_value}, без парсингу;</li>
 *   <li>{@code value_type}, {@code key} і {@code variants} живуть на
 *       {@code config.getFlag()} (глобальна сутність), решта — на самому
 *       {@code FlagConfig}.</li>
 * </ul>
 */
@Component
public class ConfigProtoMapper {

    /**
     * Повне proto-визначення флага для одного середовища:
     * key/value_type/variants — з {@code config.getFlag()}, enabled/варіанти
     * за замовчуванням/rules/rollout — з конфігурації.
     */
    public FlagDefinition toFlag(FlagConfig config) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }

    /** Сегмент середовища → proto (ключ + clauses). */
    public SegmentDefinition toSegment(io.praporets.controlplane.domain.Segment segment) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }
}
