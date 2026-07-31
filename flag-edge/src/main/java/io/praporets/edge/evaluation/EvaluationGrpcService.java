package io.praporets.edge.evaluation;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.praporets.core.evaluation.EvaluationResult;
import io.praporets.core.evaluation.Evaluator;
import io.praporets.core.model.EvaluationContext;
import io.praporets.edge.config.ConfigStore;
import io.praporets.grpc.evaluation.v1.*;
import io.quarkus.grpc.GrpcService;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * Гарячий шлях (E-03/E-04): обчислення флагів із пам'яті, без жодного
 * блокуючого виклику. Уся семантика — в core {@code Evaluator} (V1); тут
 * лише межа: валідація запиту, один знімок store (V2), мапінг відповіді.
 *
 * <p><b>Залежності для інжекту:</b> {@link io.praporets.edge.config.ConfigStore},
 * {@link ResultProtoMapper},
 * {@code @ConfigProperty praporets.edge.environment} (edge обслуговує рівно
 * одне середовище — чуже в запиті означає помилку маршрутизації клієнта).
 *
 * <p><b>Спільний каркас обох методів:</b>
 * <ol>
 *   <li>{@code environment_key != наше} → {@code onError(NOT_FOUND)} (V3);</li>
 *   <li>blank-валідація ({@code flag_key} для Evaluate, {@code user_key}
 *       для обох) → {@code onError(INVALID_ARGUMENT)} — ДО створення
 *       core-контексту, його конструктор кидає IAE (камінь #2);</li>
 *   <li>{@code store.current()} порожній → {@code onError(UNAVAILABLE)}
 *       («configuration not loaded») — прямий виклик до readiness (V3);</li>
 *   <li>рівно ОДИН {@code StoredConfig} на запит: і конфігурація для
 *       {@code Evaluator}, і {@code revision} відповіді — з нього (V2,
 *       камінь #1);</li>
 *   <li>помилки — тільки через
 *       {@code onError(Status.X.withDescription(...).asRuntimeException())},
 *       як у 02b: будь-який інший Throwable клієнт побачить як UNKNOWN.</li>
 * </ol>
 */
@GrpcService
public class EvaluationGrpcService extends EvaluationServiceGrpc.EvaluationServiceImplBase {

    @Inject
    ConfigStore configStore;

    @Inject
    ResultProtoMapper resultProtoMapper;

    @ConfigProperty(name = "praporets.edge.environment")
    String environment;

    /**
     * Один флаг (E-03). Невідомий {@code flag_key} — НЕ помилка:
     * core поверне {@code FLAG_NOT_FOUND}-результат, він мапиться у звичайну
     * відповідь (V3) — клієнт сам підставить свій default.
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
        responseObserver.onNext(resultProtoMapper.toResponse(evaluationResult, storedConfig.revision()));
        responseObserver.onCompleted();
    }

    /**
     * Усі флаги середовища для одного контексту (E-04, старт сесії SDK).
     * Порядок — за {@code flagKey} (це вже гарантує core
     * {@code Evaluator.evaluateAll}); {@code revision} — і в кожному
     * елементі, і в полі відповіді (те саме значення, V2). Порожня
     * конфігурація → порожній список + ревізія (камінь #6).
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

        responseObserver.onNext(responseBuilder);
        responseObserver.onCompleted();
    }
}
