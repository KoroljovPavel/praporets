package io.praporets.contracts;

import io.grpc.MethodDescriptor;
import io.praporets.grpc.config.v1.Bucket;
import io.praporets.grpc.config.v1.Clause;
import io.praporets.grpc.config.v1.ConfigDelta;
import io.praporets.grpc.config.v1.ConfigServiceGrpc;
import io.praporets.grpc.config.v1.ConfigSnapshot;
import io.praporets.grpc.config.v1.ConfigUpdate;
import io.praporets.grpc.config.v1.FlagDefinition;
import io.praporets.grpc.config.v1.Heartbeat;
import io.praporets.grpc.config.v1.Operator;
import io.praporets.grpc.config.v1.Rollout;
import io.praporets.grpc.config.v1.Rule;
import io.praporets.grpc.config.v1.SegmentDefinition;
import io.praporets.grpc.config.v1.StreamRequest;
import io.praporets.grpc.config.v1.ValueType;
import io.praporets.grpc.config.v1.Variant;
import io.praporets.grpc.evaluation.v1.EvaluateResponse;
import io.praporets.grpc.evaluation.v1.EvaluationServiceGrpc;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 02a: форма контракту. Тести не скомпіляться, доки немає proto-файлів
 * (§6.1–6.2 спеки) — це і є червоний стан кроку.
 */
class ProtoContractTest {

    @Test
    void config_snapshot_survives_serialization_round_trip() throws Exception {
        ConfigSnapshot snapshot = ConfigSnapshot.newBuilder()
                .setEnvironmentKey("dev")
                .setRevision(42)
                .addFlags(FlagDefinition.newBuilder()
                        .setKey("checkout.new-flow")
                        .setValueType(ValueType.BOOLEAN)
                        .setEnabled(true)
                        .setDefaultVariant("off")
                        .setOffVariant("off")
                        .addVariants(Variant.newBuilder().setKey("on").setJsonValue("true"))
                        .addVariants(Variant.newBuilder().setKey("off").setJsonValue("false"))
                        .addRules(Rule.newBuilder()
                                .setId("r1")
                                .addClauses(Clause.newBuilder()
                                        .setAttribute("country").setOperator(Operator.IN).addValues("UA"))
                                .setVariantKey("on"))
                        .setRollout(Rollout.newBuilder()
                                .setSalt("v1")
                                .addBuckets(Bucket.newBuilder().setVariantKey("on").setWeight(100_000))))
                .addSegments(SegmentDefinition.newBuilder()
                        .setKey("beta-testers")
                        .addClauses(Clause.newBuilder()
                                .setAttribute("plan").setOperator(Operator.IN).addValues("pro")))
                .build();

        assertThat(ConfigSnapshot.parseFrom(snapshot.toByteArray())).isEqualTo(snapshot);
    }

    @Test
    void config_update_payload_oneof_is_mutually_exclusive() {
        // «останній set переміг» — без помилки компіляції; читати через getPayloadCase()
        ConfigUpdate update = ConfigUpdate.newBuilder()
                .setRevision(7)
                .setDelta(ConfigDelta.newBuilder().addRemovedFlagKeys("dead.flag"))
                .setHeartbeat(Heartbeat.newBuilder().setServerTimeMillis(123))
                .build();

        assertThat(update.getPayloadCase()).isEqualTo(ConfigUpdate.PayloadCase.HEARTBEAT);
        assertThat(update.hasDelta()).isFalse();
    }

    @Test
    void proto3_scalar_defaults_do_not_travel_on_wire() throws Exception {
        // from_revision = 0 («хочу все з нуля») на wire відсутній — і це та сама
        // причина, чому перший елемент енумів мусить бути *_UNSPECIFIED = 0
        StreamRequest request = StreamRequest.newBuilder().setEnvironmentKey("dev").build();

        assertThat(request.getFromRevision()).isZero();
        assertThat(StreamRequest.parseFrom(request.toByteArray()).getFromRevision()).isZero();
        assertThat(Operator.forNumber(0)).isEqualTo(Operator.OPERATOR_UNSPECIFIED);
        assertThat(ValueType.forNumber(0)).isEqualTo(ValueType.VALUE_TYPE_UNSPECIFIED);
    }

    @Test
    void packages_follow_canonical_naming() {
        // A3/камінь #7: wire-пакет і java-пакет — різні речі, потрібні обидва
        var configFile = FlagDefinition.getDescriptor().getFile();
        assertThat(configFile.getPackage()).isEqualTo("praporets.config.v1");
        assertThat(configFile.getOptions().getJavaPackage()).isEqualTo("io.praporets.grpc.config.v1");

        var evaluationFile = EvaluateResponse.getDescriptor().getFile();
        assertThat(evaluationFile.getPackage()).isEqualTo("praporets.evaluation.v1");
        assertThat(evaluationFile.getOptions().getJavaPackage()).isEqualTo("io.praporets.grpc.evaluation.v1");
    }

    @Test
    void grpc_services_expose_expected_method_shapes() {
        assertThat(ConfigServiceGrpc.getGetSnapshotMethod().getType())
                .isEqualTo(MethodDescriptor.MethodType.UNARY);
        assertThat(ConfigServiceGrpc.getStreamConfigMethod().getType())
                .isEqualTo(MethodDescriptor.MethodType.SERVER_STREAMING);
        assertThat(EvaluationServiceGrpc.getEvaluateMethod().getFullMethodName())
                .isEqualTo("praporets.evaluation.v1.EvaluationService/Evaluate");
        assertThat(EvaluationServiceGrpc.getEvaluateAllMethod().getType())
                .isEqualTo(MethodDescriptor.MethodType.UNARY);
    }
}
