package io.praporets.edge.evaluation.rest.exceptions;

import jakarta.validation.ConstraintViolationException;

public record ProblemDetail(String type, String title, String detail, int status) {

    public static ProblemDetail ofException(ProblemDetailsException exception) {
        return new ProblemDetail("about:blank", exception.title, exception.detail, exception.statusCode);
    }

    public static ProblemDetail ofConstraintViolationException(ConstraintViolationException exception) {
        return new ProblemDetail("about:blank", "Bad Request", exception.getMessage(), 400);
    }
}
