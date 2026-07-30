package io.praporets.edge.evaluation.rest;

/**
 * Запит прийшов на середовище, яке цей edge не обслуговує (V3/V6) —
 * помилка маршрутизації клієнта, а не стан платформи. ГОТОВИЙ клас.
 * Мапиться у 404 {@code application/problem+json} в
 * {@link UnknownEnvironmentMapper}.
 */
public class UnknownEnvironmentException extends RuntimeException {

    public UnknownEnvironmentException(String requestedEnvironment) {
        super("Environment [" + requestedEnvironment + "] is not served by this edge");
    }
}
