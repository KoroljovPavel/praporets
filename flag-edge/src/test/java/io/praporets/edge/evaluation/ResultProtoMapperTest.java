package io.praporets.edge.evaluation;

import io.praporets.core.evaluation.EvaluationResult;
import io.praporets.core.evaluation.Reason;
import io.praporets.grpc.evaluation.v1.EvaluateResponse;
import io.praporets.grpc.evaluation.v1.EvaluationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 02e: контракт мапінгу core→proto (V4). Чистий юніт — без Quarkus.
 */
class ResultProtoMapperTest {

    private final ResultProtoMapper mapper = new ResultProtoMapper();

    @Test
    void rule_match_maps_all_fields_and_revision() {
        EvaluationResult result = new EvaluationResult(
            "checkout.new-flow", Reason.RULE_MATCH, "on", "true", "r1");

        EvaluateResponse response = mapper.toResponse(result, 7);

        assertThat(response.getFlagKey()).isEqualTo("checkout.new-flow");
        assertThat(response.getVariantKey()).isEqualTo("on");
        assertThat(response.getJsonValue()).isEqualTo("true");
        assertThat(response.getReason()).isEqualTo(io.praporets.grpc.evaluation.v1.Reason.RULE_MATCH);
        assertThat(response.getRuleId()).isEqualTo("r1");
        assertThat(response.getRevision()).isEqualTo(7);
    }

    @Test
    void flag_not_found_maps_nulls_to_proto_empty_strings() {
        EvaluationResult result = new EvaluationResult(
            "nope", Reason.FLAG_NOT_FOUND, null, null, null);

        EvaluateResponse response = mapper.toResponse(result, 42);

        // proto3 не має null: несетнуті string-поля читаються як ""
        assertThat(response.getVariantKey()).isEmpty();
        assertThat(response.getJsonValue()).isEmpty();
        assertThat(response.getRuleId()).isEmpty();
        assertThat(response.getReason()).isEqualTo(io.praporets.grpc.evaluation.v1.Reason.FLAG_NOT_FOUND);
        assertThat(response.getRevision()).isEqualTo(42);
    }

    @Test
    void every_core_reason_has_proto_counterpart_with_same_name() {
        // ловить розсинхрон контрактів: нове core-значення без proto-пари
        for (Reason coreReason : Reason.values()) {
            assertThat(mapper.toProtoReason(coreReason).name())
                .as("proto-пара для core Reason.%s", coreReason)
                .isEqualTo(coreReason.name());
        }
    }

    @Test
    void proto_context_maps_to_core_context() {
        EvaluationContext protoContext = EvaluationContext.newBuilder()
            .setUserKey("user-42")
            .putAttributes("country", "UA")
            .putAttributes("plan", "pro")
            .build();

        io.praporets.core.model.EvaluationContext core = mapper.toCoreContext(protoContext);

        assertThat(core.userKey()).isEqualTo("user-42");
        assertThat(core.attributes()).isEqualTo(Map.of("country", "UA", "plan", "pro"));
    }

    @Test
    void context_without_attributes_maps_to_empty_map() {
        EvaluationContext protoContext = EvaluationContext.newBuilder()
            .setUserKey("user-42")
            .build();

        io.praporets.core.model.EvaluationContext core = mapper.toCoreContext(protoContext);

        assertThat(core.attributes()).isEmpty();
    }
}
