package io.praporets.controlplane.grpc;

import io.praporets.controlplane.domain.Flag;
import io.praporets.controlplane.domain.FlagConfig;
import io.praporets.controlplane.domain.Segment;
import io.praporets.grpc.config.v1.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Мапінг доменного стану (entity + core-records із JSONB) → proto-повідомлення
 * {@code praporets.config.v1}. Stateless, без транзакцій — чиста трансформація.
 *
 * <p>Core-records дзеркалять proto 1:1, тож мапінг механічний. Особливості:
 * <ul>
 *   <li>енуми мапляться <b>за ім'ям</b> ({@code Operator.valueOf(name())},
 *       {@code ValueType.valueOf(name())}) — що ім'я існує в proto, гарантує
 *       drift-guard-тест вирівнювання core ↔ proto;</li>
 *   <li>у proto немає {@code null}: відсутній {@code rollout} (флага або
 *       правила) — {@code setRollout(...)} просто не викликається, читач
 *       побачить {@code hasRollout() == false};</li>
 *   <li>entity {@code Variant.getValue()} — вже канонічний JSON-рядок із
 *       JSONB, тому йде прямо в {@code json_value}, без парсингу;</li>
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
        Flag flag = config.getFlag();

        List<Variant> variants = flag.getVariants().stream().map(v -> Variant.newBuilder()
            .setKey(v.getKey())
            .setJsonValue(v.getValue())
            .build()).toList();

        List<Rule> rules = config.getRules().stream().map(r -> {
            Rule.Builder ruleBuilder = Rule.newBuilder()
                .setId(r.id())
                .addAllClauses(
                    r.clauses().stream().map(clause -> Clause.newBuilder()
                        .setAttribute(clause.attribute())
                        .setOperator(Operator.valueOf(clause.operator().name()))
                        .addAllValues(clause.values())
                        .setNegate(clause.negate())
                        .build()).toList()
                )
                .setVariantKey(r.variantKey());

            if (r.rollout() != null) {
                ruleBuilder.setRollout(Rollout.newBuilder()
                    .setSalt(r.rollout().salt())
                    .addAllBuckets(r.rollout().buckets().stream().map(bucket -> Bucket.newBuilder()
                        .setVariantKey(bucket.variantKey())
                        .setWeight(bucket.weight())
                        .build()).toList())
                    .build());
            }

            return ruleBuilder.build();
        }).toList();

        FlagDefinition.Builder flagDefinitionBuilder = FlagDefinition.newBuilder()
            .setKey(flag.getKey())
            .setValueType(ValueType.valueOf(flag.getValueType().name()))
            .setEnabled(config.isEnabled())
            .setDefaultVariant(config.getDefaultVariant())
            .setOffVariant(config.getOffVariant())
            .addAllVariants(variants)
            .addAllRules(rules);

        if (config.getRollout() != null) {
            flagDefinitionBuilder.setRollout(Rollout.newBuilder()
                .setSalt(config.getRollout().salt())
                .addAllBuckets(config.getRollout().buckets().stream().map(bucket -> Bucket.newBuilder()
                    .setVariantKey(bucket.variantKey())
                    .setWeight(bucket.weight())
                    .build()).toList())
                .build()
            );
        }

        return flagDefinitionBuilder.build();
    }

    /** Сегмент середовища → proto (ключ + clauses). */
    public SegmentDefinition toSegment(Segment segment) {
        return SegmentDefinition.newBuilder()
            .setKey(segment.getKey())
            .addAllClauses(segment.getConditions().stream().map(clause -> Clause.newBuilder()
                .setAttribute(clause.attribute())
                .setOperator(Operator.valueOf(clause.operator().name()))
                .addAllValues(clause.values())
                .setNegate(clause.negate())
                .build()).toList())
            .build();
    }
}
