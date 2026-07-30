package io.praporets.edge.evaluation;

import io.grpc.stub.StreamObserver;
import io.praporets.grpc.evaluation.v1.*;
import io.quarkus.grpc.GrpcService;

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

    /**
     * Один флаг (E-03). Невідомий {@code flag_key} — НЕ помилка:
     * core поверне {@code FLAG_NOT_FOUND}-результат, він мапиться у звичайну
     * відповідь (V3) — клієнт сам підставить свій default.
     */
    @Override
    public void evaluate(EvaluateRequest request, StreamObserver<EvaluateResponse> responseObserver) {
        throw new UnsupportedOperationException("02e: твоя реалізація");
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
        throw new UnsupportedOperationException("02e: твоя реалізація");
    }
}
