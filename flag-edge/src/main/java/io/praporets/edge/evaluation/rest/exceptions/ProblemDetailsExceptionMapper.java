package io.praporets.edge.evaluation.rest.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@link UnknownEnvironmentException} → 404 у форматі RFC 9457
 * {@code application/problem+json} (V6).
 *
 * <p><b>Контракт відповіді:</b> {@code {"type":"about:blank",
 * "title":"Not Found","status":404,"detail":"<message винятку>"}} з
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
