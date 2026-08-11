package io.praporets.edge.evaluation;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.praporets.core.evaluation.EvaluationResult;
import io.praporets.core.evaluation.Evaluator;
import io.praporets.core.model.EvaluationContext;
import io.praporets.edge.config.ConfigStore;
import io.praporets.edge.events.EvaluationEvents;
import io.praporets.grpc.evaluation.v1.*;
import io.quarkus.grpc.GrpcService;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * gRPC-сервіс обчислення флагів — гарячий шлях: усе з пам'яті, без жодного
 * блокуючого виклику. Уся семантика — в core {@code Evaluator}; тут лише
 * межа: валідація запиту, один знімок store, мапінг відповіді, емісія
 * evaluation-подій. Edge обслуговує рівно одне середовище
 * ({@code praporets.edge.environment}) — чуже середовище в запиті означає
 * помилку маршрутизації клієнта.
 *
 * <p>Спільний каркас обох методів:
 * <ol>
 *   <li>{@code environment_key != наше} → {@code onError(NOT_FOUND)};</li>
 *   <li>blank-валідація ({@code flag_key} для Evaluate, {@code user_key}
 *       для обох) → {@code onError(INVALID_ARGUMENT)} — ДО створення
 *       core-контексту, чий конструктор інакше кинув би IAE, і клієнт
 *       побачив би UNKNOWN;</li>
 *   <li>{@code store.current()} порожній → {@code onError(UNAVAILABLE)} —
 *       той самий стан, у якому readiness каже DOWN;</li>
 *   <li>рівно ОДИН {@code StoredConfig} на запит: і конфігурація для
 *       {@code Evaluator}, і {@code revision} відповіді — з одного знімка,
 *       конкурентний swap не може їх розсинхронізувати;</li>
 *   <li>помилки — тільки через
 *       {@code onError(Status.X.withDescription(...).asRuntimeException())}.</li>
 * </ol>
 */
@GrpcService
public class EvaluationGrpcService extends EvaluationServiceGrpc.EvaluationServiceImplBase {

    @Inject
    ConfigStore configStore;

    @Inject
    ResultProtoMapper resultProtoMapper;

    @Inject
    EvaluationEvents evaluationEvents;

    @ConfigProperty(name = "praporets.edge.environment")
    String environment;

    /**
     * Обчислення одного флага. Невідомий {@code flag_key} — НЕ помилка:
     * core поверне {@code FLAG_NOT_FOUND}-результат, він мапиться у звичайну
     * відповідь — клієнт сам підставить свій default.
     */
    @Override
    public void evaluate(EvaluateRequest request, StreamObserver<EvaluateResponse> responseObserver) {
        if (!request.getEnvironmentKey().equals(environment)) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(String.format("Environment %s is not served by this edge", request.getEnvironmentKey()))
                .asRuntimeException());
            return;
        }


        if (request.getFlagKey().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Flag key is blank").asRuntimeException());
            return;
        }

        if (request.getContext().getUserKey().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("User key is blank").asRuntimeException());
            return;
        }

        ConfigStore.StoredConfig storedConfig = configStore.current().orElse(null);
        if (storedConfig == null) {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Store not loaded").asRuntimeException());
            return;
        }

        EvaluationResult evaluationResult = Evaluator.evaluate(storedConfig.config(), request.getFlagKey(),
            new EvaluationContext(request.getContext().getUserKey(), request.getContext().getAttributesMap()));

        evaluationEvents.emit(evaluationResult, storedConfig.revision(), request.getContext().getUserKey());
        responseObserver.onNext(resultProtoMapper.toResponse(evaluationResult, storedConfig.revision()));
        responseObserver.onCompleted();
    }

    /**
     * Усі флаги середовища для одного контексту (типовий старт сесії SDK).
     * Порядок — за {@code flagKey} (це гарантує core
     * {@code Evaluator.evaluateAll}); {@code revision} — і в кожному
     * елементі, і в полі відповіді, з одного знімка store. Порожня
     * конфігурація → порожній список + ревізія.
     */
    @Override
    public void evaluateAll(EvaluateAllRequest request, StreamObserver<EvaluateAllResponse> responseObserver) {
        if (!request.getEnvironmentKey().equals(environment)) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(String.format("Environment %s is not served by this edge", request.getEnvironmentKey()))
                .asRuntimeException());
            return;
        }

        if (request.getContext().getUserKey().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("User key is blank").asRuntimeException());
            return;
        }

        ConfigStore.StoredConfig storedConfig = configStore.current().orElse(null);
        if (storedConfig == null) {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Store not loaded").asRuntimeException());
            return;
        }

        List<EvaluationResult> evaluationResultList = Evaluator.evaluateAll(storedConfig.config(),
            new EvaluationContext(request.getContext().getUserKey(), request.getContext().getAttributesMap()));
        List<EvaluateResponse> evaluateResponseList = evaluationResultList.stream().map(evaluationResult ->
            resultProtoMapper.toResponse(evaluationResult, storedConfig.revision())).toList();

        EvaluateAllResponse responseBuilder = EvaluateAllResponse.newBuilder()
            .addAllEvaluations(evaluateResponseList)
            .setRevision(storedConfig.revision())
            .build();

        for (EvaluationResult evaluationResult : evaluationResultList) {
            evaluationEvents.emit(evaluationResult, storedConfig.revision(), request.getContext().getUserKey());
        }

        responseObserver.onNext(responseBuilder);
        responseObserver.onCompleted();
    }
}
