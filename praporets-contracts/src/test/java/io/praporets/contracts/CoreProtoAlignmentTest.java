package io.praporets.contracts;

import io.praporets.core.evaluation.Reason;
import io.praporets.core.model.Operator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A5 — drift-guard замість Pact: ядро і proto ніколи не редагуються разом
 * автоматично, тож перейменування констант у будь-якому з них має ламати
 * ЦЕЙ тест, а не інтеграцію CP↔edge у рантаймі.
 *
 * <p>Напрям — core ⊆ proto: у proto легально є зайві {@code *_UNSPECIFIED}
 * (проблема відсутнього поля proto3) і {@code ERROR} (транспортний стан edge,
 * ядру невідомий).
 */
class CoreProtoAlignmentTest {

    @Test
    void every_core_operator_exists_in_proto_operator_enum() {
        for (Operator operator : Operator.values()) {
            assertThat(io.praporets.grpc.config.v1.Operator.getDescriptor()
                    .findValueByName(operator.name()))
                    .as("proto praporets.config.v1.Operator не має константи %s", operator.name())
                    .isNotNull();
        }
    }

    @Test
    void every_core_reason_exists_in_proto_reason_enum() {
        for (Reason reason : Reason.values()) {
            assertThat(io.praporets.grpc.evaluation.v1.Reason.getDescriptor()
                    .findValueByName(reason.name()))
                    .as("proto praporets.evaluation.v1.Reason не має константи %s", reason.name())
                    .isNotNull();
        }
    }
}
