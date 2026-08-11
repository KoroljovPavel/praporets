package io.praporets.edge.evaluation.rest.exceptions;

/**
 * Запит прийшов на середовище, яке цей edge не обслуговує — помилка
 * маршрутизації клієнта, а не стан платформи. Мапиться у 404
 * {@code application/problem+json} в {@link ProblemDetailsExceptionMapper}.
 */
public class UnknownEnvironmentException extends ProblemDetailsException {

    public UnknownEnvironmentException(String requestedEnvironment) {
        super(404, "Not Found", "Environment [" + requestedEnvironment + "] is not served by this edge");
    }
}
