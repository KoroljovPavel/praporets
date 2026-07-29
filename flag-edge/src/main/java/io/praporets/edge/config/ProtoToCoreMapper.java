package io.praporets.edge.config;

import io.praporets.core.model.EnvironmentConfig;
import io.praporets.grpc.config.v1.ConfigSnapshot;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Зворотний бік мапера з 02b: proto {@code ConfigSnapshot} → core
 * {@link EnvironmentConfig}. Виконується один раз на снапшот/дельту — гарячий
 * шлях обчислення (02e) працює вже з core-моделлю і proto не бачить.
 *
 * <p>Stateless, без залежностей.
 *
 * <p><b>Реалізація (твоя робота):</b>
 * <ul>
 *   <li>кожен proto {@code FlagDefinition} → core {@code FlagDefinition(key,
 *       enabled, defaultVariant, offVariant, variants, rules, rollout)}; ключ
 *       мапи = {@code key} сутності, інакше конструктор
 *       {@code EnvironmentConfig} кине IAE (його інваріант);</li>
 *   <li>енуми — за ім'ям: core {@code Operator.valueOf(protoOperator.name())}.
 *       Напрям core ⊆ proto (drift-guard 02a) гарантує лише що ІМЕНА core
 *       існують у proto — у зворотний бік можуть прилетіти
 *       {@code OPERATOR_UNSPECIFIED} (зіпсований запис) → кидай
 *       {@code IllegalArgumentException}, хай стрім/снапшот упаде голосно, а
 *       не мовчки зламає обчислення;</li>
 *   <li>proto не має null: {@code flag.hasRollout() == false} → core
 *       {@code rollout = null}; те саме для {@code rule.hasRollout()};
 *       порожній {@code rule_id}/{@code variant_key} → {@code null} у core
 *       (proto3-дефолт «порожній рядок» — це і є «відсутнє»);</li>
 *   <li>{@code value_type} свідомо викидається — ядру він не потрібен
 *       (обчислення повертає jsonValue як рядок незалежно від типу).</li>
 * </ul>
 */
@ApplicationScoped
public class ProtoToCoreMapper {

    /**
     * Повний снапшот → готова до обчислення core-конфігурація середовища.
     */
    public EnvironmentConfig toEnvironmentConfig(ConfigSnapshot snapshot) {
        throw new UnsupportedOperationException("02c: твоя реалізація");
    }
}
