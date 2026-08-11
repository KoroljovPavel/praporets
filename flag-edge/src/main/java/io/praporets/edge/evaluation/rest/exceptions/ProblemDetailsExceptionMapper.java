package io.praporets.edge.evaluation.rest.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Будь-який {@link ProblemDetailsException} → HTTP-відповідь у форматі
 * RFC 9457 {@code application/problem+json}: статус, title і detail — з
 * винятку.
 *
 * <p><b>Контракт відповіді:</b> {@code {"type":"about:blank",
 * "title":"<title>","status":<statusCode>,"detail":"<detail>"}} з
 * {@code Content-Type: application/problem+json}.
 */
@Provider
public class ProblemDetailsExceptionMapper implements ExceptionMapper<ProblemDetailsException> {

    @Override
    public Response toResponse(ProblemDetailsException exception) {
        ProblemDetail problemDetail = ProblemDetail.ofException(exception);

        return Response
            .status(Response.Status.fromStatusCode(problemDetail.status()))
            .type("application/problem+json")
            .entity(problemDetail)
            .build();
    }
}
