package io.praporets.edge.evaluation;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.praporets.edge.config.ConfigStore;
import io.praporets.edge.config.FakeControlPlane;
import io.praporets.grpc.evaluation.v1.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Специфікує контракт gRPC-обчислення (Evaluate/EvaluateAll) проти
 * справжнього едж-сервера.
 * Канонічний снапшот {@link FakeControlPlane}: флаг {@code checkout.new-flow}
 * (enabled, rule r1: country IN [UA] → "on", rollout 100% → "on"),
 * ревізія 7.
 *
 * <p>Власний {@link TestProfile}: останній тест мутує store дельтою — свіжий
 * контекст, щоб не зламати інваріанти сусідніх класів. Тести впорядковані:
 * дельта-тест іде строго останнім.
 *
 * <p>Канал — на {@code quarkus.grpc.server.test-port} 8081
 */
@QuarkusTest
@QuarkusTestResource(value = FakeControlPlane.class, restrictToAnnotatedClass = true)
@TestProfile(EvaluationGrpcTest.EvalProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EvaluationGrpcTest {

    public static class EvalProfile implements QuarkusTestProfile {
    }

    private static ManagedChannel channel;

    @Inject
    ConfigStore store;

    @BeforeEach
    void awaitLoadedAndOpenChannel() throws InterruptedException {
        await("снапшот завантажено", () -> store.isLoaded());
        if (channel == null) {
            channel = ManagedChannelBuilder.forAddress("localhost", 8081).usePlaintext().build();
        }
    }

    @AfterAll
    static void closeChannel() {
        if (channel != null) {
            channel.shutdownNow();
            channel = null;
        }
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    private static EvaluationServiceGrpc.EvaluationServiceBlockingStub stub() {
        return EvaluationServiceGrpc.newBlockingStub(channel);
    }

    private static EvaluateRequest request(String flagKey, String country) {
        return EvaluateRequest.newBuilder()
            .setEnvironmentKey(FakeControlPlane.ENVIRONMENT)
            .setFlagKey(flagKey)
            .setContext(EvaluationContext.newBuilder()
                .setUserKey("user-42")
                .putAttributes("country", country))
            .build();
    }

    @Test
    @Order(1)
    void rule_match_for_matching_context() {
        EvaluateResponse response = stub().evaluate(request(FakeControlPlane.FLAG_KEY, "UA"));

        assertThat(response.getReason()).isEqualTo(Reason.RULE_MATCH);
        assertThat(response.getVariantKey()).isEqualTo("on");
        assertThat(response.getJsonValue()).isEqualTo("true");
        assertThat(response.getRuleId()).isEqualTo("r1");
        assertThat(response.getRevision()).isEqualTo(FakeControlPlane.SNAPSHOT_REVISION);
    }

    @Test
    @Order(2)
    void rollout_applies_when_no_rule_matches() {
        EvaluateResponse response = stub().evaluate(request(FakeControlPlane.FLAG_KEY, "DE"));

        // єдиний бакет вагою 100_000 → будь-який користувач приземляється в "on"
        assertThat(response.getReason()).isEqualTo(Reason.ROLLOUT);
        assertThat(response.getVariantKey()).isEqualTo("on");
        assertThat(response.getRuleId()).as("rollout флага, не правила — без ruleId").isEmpty();
    }

    @Test
    @Order(3)
    void unknown_flag_is_flag_not_found_response_not_error() {
        EvaluateResponse response = stub().evaluate(request("no.such.flag", "UA"));

        assertThat(response.getReason()).isEqualTo(Reason.FLAG_NOT_FOUND);
        assertThat(response.getVariantKey()).isEmpty();
        assertThat(response.getRevision())
            .as("ревізія є навіть у FLAG_NOT_FOUND — клієнт бачить, ЩО саме edge знав")
            .isEqualTo(FakeControlPlane.SNAPSHOT_REVISION);
    }

    @Test
    @Order(4)
    void foreign_environment_is_not_found_error() {
        EvaluateRequest foreign = request(FakeControlPlane.FLAG_KEY, "UA").toBuilder()
            .setEnvironmentKey("prod")
            .build();

        assertThatThrownBy(() -> stub().evaluate(foreign))
            .isInstanceOfSatisfying(StatusRuntimeException.class, e ->
                assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    @Order(5)
    void blank_user_key_is_invalid_argument() {
        EvaluateRequest blankUser = EvaluateRequest.newBuilder()
            .setEnvironmentKey(FakeControlPlane.ENVIRONMENT)
            .setFlagKey(FakeControlPlane.FLAG_KEY)
            .setContext(EvaluationContext.newBuilder().setUserKey("   "))
            .build();

        assertThatThrownBy(() -> stub().evaluate(blankUser))
            .isInstanceOfSatisfying(StatusRuntimeException.class, e ->
                assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    @Order(6)
    void evaluate_all_returns_all_flags_with_revision() {
        EvaluateAllResponse response = stub().evaluateAll(EvaluateAllRequest.newBuilder()
            .setEnvironmentKey(FakeControlPlane.ENVIRONMENT)
            .setContext(EvaluationContext.newBuilder()
                .setUserKey("user-42")
                .putAttributes("country", "UA"))
            .build());

        assertThat(response.getRevision()).isEqualTo(FakeControlPlane.SNAPSHOT_REVISION);
        assertThat(response.getEvaluationsList())
            .hasSize(1)
            .first()
            .satisfies(e -> {
                assertThat(e.getFlagKey()).isEqualTo(FakeControlPlane.FLAG_KEY);
                assertThat(e.getReason()).isEqualTo(Reason.RULE_MATCH);
                assertThat(e.getRevision()).isEqualTo(FakeControlPlane.SNAPSHOT_REVISION);
            });
    }

    @Test
    @Order(7)
    void delta_changes_evaluation_response() throws InterruptedException {
        // міст стрім↔обчислення: вимкнення флага дельтою міняє ВІДПОВІДЬ обчислення
        FakeControlPlane.awaitSessionCount(1, 10_000);
        FakeControlPlane.latestSession().sendDelta(8, false);
        await("дельта 8 у store", () -> store.current()
            .map(c -> c.revision() == 8).orElse(false));

        EvaluateResponse response = stub().evaluate(request(FakeControlPlane.FLAG_KEY, "UA"));

        assertThat(response.getReason()).isEqualTo(Reason.FLAG_DISABLED);
        assertThat(response.getVariantKey()).isEqualTo("off");
        assertThat(response.getJsonValue()).isEqualTo("false");
        assertThat(response.getRevision()).isEqualTo(8);
    }
}
