package io.praporets.edge.config;

import io.praporets.core.model.*;
import io.praporets.core.revision.Delta;
import io.praporets.grpc.config.v1.ConfigDelta;
import io.praporets.grpc.config.v1.ConfigSnapshot;
import io.praporets.grpc.config.v1.SegmentDefinition;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

        Map<String, FlagDefinition> flagDefinitionMap = snapshot.getFlagsList().stream().map(this::parseFlag)
            .collect(Collectors.toMap(FlagDefinition::key, entry -> entry));

        Map<String, Segment> segmentMap = snapshot.getSegmentsList().stream().map(this::parseSegment)
            .collect(Collectors.toMap(Segment::key, entry -> entry));

        return new EnvironmentConfig(flagDefinitionMap, segmentMap);
    }

    /**
     * 02d: proto-дельта → core {@link io.praporets.core.revision.Delta}.
     * Реюзни ті самі приватні parse-хелпери, що й снапшот; removed-ключі —
     * як є (це прості рядки). Ті самі правила: енуми за ім'ям, IAE на
     * UNSPECIFIED — зіпсована дельта має падати голосно ДО swap-у.
     */
    public Delta toDelta(ConfigDelta protoDelta) {
        List<FlagDefinition> upsertedFlags = protoDelta.getUpsertedFlagsList().stream().map(this::parseFlag).toList();
        List<Segment> upsertedSegments = protoDelta.getUpsertedSegmentsList().stream().map(this::parseSegment).toList();
        Set<String> removedFlagKeys = new HashSet<>(protoDelta.getRemovedFlagKeysList());
        Set<String> removedSegmentKeys = new HashSet<>(protoDelta.getRemovedSegmentKeysList());

        return new Delta(
            upsertedFlags,
            removedFlagKeys,
            upsertedSegments,
            removedSegmentKeys
        );
    }

    private FlagDefinition parseFlag(io.praporets.grpc.config.v1.FlagDefinition flag) {
        return new FlagDefinition(
            flag.getKey(), flag.getEnabled(), flag.getDefaultVariant(), flag.getOffVariant(),
            parseVariants(flag.getVariantsList()),
            parseRules(flag.getRulesList()),
            flag.hasRollout() ? parseRollout(flag.getRollout()) : null
        );
    }

    private Segment parseSegment(SegmentDefinition segment) {
        return new Segment(segment.getKey(), parseClauses(segment.getClausesList()));
    }

    private List<Variant> parseVariants(List<io.praporets.grpc.config.v1.Variant> variantsList) {
        return variantsList.stream()
            .map(variant -> new Variant(
                variant.getKey().isEmpty() ? null : variant.getKey(),
                variant.getJsonValue())
            ).toList();
    }

    private List<Rule> parseRules(List<io.praporets.grpc.config.v1.Rule> rulesList) {
        return rulesList.stream().map(rule -> new Rule(
            rule.getId().isEmpty() ? null : rule.getId(),
            parseClauses(rule.getClausesList()),
            rule.getVariantKey().isEmpty() ? null : rule.getVariantKey(),
            rule.hasRollout() ? parseRollout(rule.getRollout()) : null
        )).toList();
    }

    private List<Clause> parseClauses(List<io.praporets.grpc.config.v1.Clause> clauseList) {
        return clauseList.stream().map(clause -> new Clause(
            clause.getAttribute(),
            Operator.valueOf(clause.getOperator().name()),
            clause.getValuesList(),
            clause.getNegate()
        )).toList();
    }

    private Rollout parseRollout(io.praporets.grpc.config.v1.Rollout rollout) {
        return new Rollout(rollout.getSalt(), parseBuckets(rollout.getBucketsList()));
    }

    private List<Bucket> parseBuckets(List<io.praporets.grpc.config.v1.Bucket> bucketList) {
        return bucketList.stream().map(bucket -> new Bucket(bucket.getVariantKey(), bucket.getWeight())).toList();
    }
}
