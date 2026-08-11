package io.praporets.edge.evaluation.rest.exceptions;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Bean Validation → 400 у форматі RFC 9457 {@code application/problem+json}
 * — замість дефолтного квіркусівського звіту про порушення, який не є нашим
 * контрактом помилок.
 *
 * <p><b>Контракт відповіді</b> (мінімум RFC 9457, як у control-plane):
 * {@code {"type":"about:blank","title":"Bad Request","status":400,
 * "detail":"<перелік порушень: path — message>"}} з
 * {@code Content-Type: application/problem+json}.
 *
 * <p>Цей мапер перекриває вбудований, бо його тип винятку точніший.
 */
@Provider
public class ValidationProblemMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        return Response
            .status(Response.Status.BAD_REQUEST)
            .type("application/problem+json")
            .entity(ProblemDetail.ofConstraintViolationException(exception))
            .build();
    }
}
