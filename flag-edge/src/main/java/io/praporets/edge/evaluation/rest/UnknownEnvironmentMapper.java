package io.praporets.edge.evaluation.rest;

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
public class UnknownEnvironmentMapper implements ExceptionMapper<UnknownEnvironmentException> {

    @Override
    public Response toResponse(UnknownEnvironmentException exception) {
        throw new UnsupportedOperationException("02e: твоя реалізація");
    }
}
