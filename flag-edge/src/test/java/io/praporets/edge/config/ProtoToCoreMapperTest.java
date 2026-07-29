package io.praporets.edge.config;

import io.praporets.core.model.EnvironmentConfig;
import io.praporets.core.model.FlagDefinition;
import io.praporets.grpc.config.v1.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 02c: чистий юніт на зворотний мапер proto → core (без Quarkus). Пінить
 * граничні місця: proto3-«відсутність» (hasRollout, порожні рядки) → null
 * у core, і захист від {@code *_UNSPECIFIED}.
 */
class ProtoToCoreMapperTest {

    private final ProtoToCoreMapper mapper = new ProtoToCoreMapper();

    private static ConfigSnapshot.Builder snapshotBase() {
        return ConfigSnapshot.newBuilder().setEnvironmentKey("dev").setRevision(3);
    }

    @Test
    void maps_flags_segments_rules_and_rollout_to_core_model() {
        ConfigSnapshot snapshot = snapshotBase()
            .addFlags(io.praporets.grpc.config.v1.FlagDefinition.newBuilder()
                .setKey("checkout.new-flow")
                .setValueType(ValueType.BOOLEAN)
                .setEnabled(true)
                .setDefaultVariant("on")
                .setOffVariant("off")
                .addVariants(Variant.newBuilder().setKey("on").setJsonValue("true"))
                .addVariants(Variant.newBuilder().setKey("off").setJsonValue("false"))
                .addRules(Rule.newBuilder()
                    .setId("r1")
                    .addClauses(Clause.newBuilder()
                        .setAttribute("plan").setOperator(Operator.IN_SEGMENT).addValues("beta-testers"))
                    .setVariantKey("on"))
                .setRollout(Rollout.newBuilder()
                    .setSalt("v1")
                    .addBuckets(Bucket.newBuilder().setVariantKey("on").setWeight(40_000))
                    .addBuckets(Bucket.newBuilder().setVariantKey("off").setWeight(60_000))))
            .addSegments(SegmentDefinition.newBuilder()
                .setKey("beta-testers")
                .addClauses(Clause.newBuilder()
                    .setAttribute("plan").setOperator(Operator.IN).addValues("pro")))
            .build();

        EnvironmentConfig config = mapper.toEnvironmentConfig(snapshot);

        FlagDefinition flag = config.flags().get("checkout.new-flow");
        assertThat(flag.key()).isEqualTo("checkout.new-flow");
        assertThat(flag.variants()).extracting(v -> v.jsonValue()).containsExactly("true", "false");
        assertThat(flag.rules().getFirst().id()).isEqualTo("r1");
        assertThat(flag.rules().getFirst().clauses().getFirst().operator())
            .isEqualTo(io.praporets.core.model.Operator.IN_SEGMENT);
        assertThat(flag.rollout().buckets()).hasSize(2);
        assertThat(config.segments().get("beta-testers").clauses().getFirst().values())
            .containsExactly("pro");
    }

    @Test
    void proto_absence_becomes_core_null_not_empty_objects() {
        // без setRollout і з rollout-правилом без variant_key: proto3 віддає
        // «порожні» дефолти, core чекає null — мапер мусить розрізняти
        ConfigSnapshot snapshot = snapshotBase()
            .addFlags(io.praporets.grpc.config.v1.FlagDefinition.newBuilder()
                .setKey("simple.flag")
                .setValueType(ValueType.STRING)
                .setEnabled(false)
                .setDefaultVariant("a")
                .setOffVariant("a")
                .addVariants(Variant.newBuilder().setKey("a").setJsonValue("\"x\""))
                .addVariants(Variant.newBuilder().setKey("b").setJsonValue("\"y\""))
                .addRules(Rule.newBuilder()
                    .setId("r-rollout")
                    .addClauses(Clause.newBuilder()
                        .setAttribute("country").setOperator(Operator.IN).addValues("UA"))
                    .setRollout(Rollout.newBuilder()
                        .setSalt("s")
                        .addBuckets(Bucket.newBuilder().setVariantKey("a").setWeight(100_000)))))
            .build();

        FlagDefinition flag = mapper.toEnvironmentConfig(snapshot).flags().get("simple.flag");

        assertThat(flag.rollout()).as("hasRollout()==false → null, не порожній Rollout").isNull();
        assertThat(flag.rules().getFirst().variantKey())
            .as("порожній proto-рядок variant_key → null у core").isNull();
        assertThat(flag.rules().getFirst().rollout()).isNotNull();
    }

    @Test
    void unspecified_operator_is_rejected_loudly() {
        ConfigSnapshot snapshot = snapshotBase()
            .addSegments(SegmentDefinition.newBuilder()
                .setKey("broken")
                .addClauses(Clause.newBuilder()
                    .setAttribute("plan").setOperator(Operator.OPERATOR_UNSPECIFIED).addValues("x")))
            .build();

        // зіпсований запис має валити завантаження голосно, а не мовчки
        // перетворюватись на «правило, що ніколи не матчиться»
        assertThatThrownBy(() -> mapper.toEnvironmentConfig(snapshot))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
