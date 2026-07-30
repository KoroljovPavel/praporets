package io.praporets.edge.evaluation.rest;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Bean Validation → 400 у форматі RFC 9457 {@code application/problem+json}
 * (V6) — замість дефолтного квіркусівського звіту про порушення, який НЕ є
 * нашим контрактом помилок.
 *
 * <p><b>Контракт відповіді</b> (мінімум RFC 9457, як у CP):
 * {@code {"type":"about:blank","title":"Bad Request","status":400,
 * "detail":"<перелік порушень: path — message>"}} з
 * {@code Content-Type: application/problem+json}.
 *
 * <p>Камінь #5: без {@code @Provider} клас мовчки ігнорується. Камінь #7:
 * цей мапер перекриває вбудований, бо його тип точніший.
 */
@Provider
public class ValidationProblemMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        throw new UnsupportedOperationException("02e: твоя реалізація");
    }
}
