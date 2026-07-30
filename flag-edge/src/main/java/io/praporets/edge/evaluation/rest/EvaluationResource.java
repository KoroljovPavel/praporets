package io.praporets.edge.evaluation.rest;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST-обчислення одного флага (E-03, V5): {@code POST /api/v1/evaluate}
 * на спільному HTTP-порту 8081 (там же health і metrics).
 *
 * <p><b>Залежності для інжекту:</b>
 * {@link io.praporets.edge.config.ConfigStore},
 * {@code @ConfigProperty praporets.edge.environment}.
 * {@link io.praporets.edge.evaluation.ResultProtoMapper} тут НЕ потрібен —
 * proto в REST-шляху не бере участі: core-результат перекладається одразу в
 * {@link EvaluateHttpResponse}.
 *
 * <p><b>Каркас (той самий, що в gRPC-сервісі, але помилки — винятками):</b>
 * <ol>
 *   <li>Bean Validation спрацьовує ДО тіла методу ({@code @Valid}) —
 *       {@code ConstraintViolationException} ловить
 *       {@link ValidationProblemMapper} → 400;</li>
 *   <li>чуже середовище → {@link UnknownEnvironmentException} → 404;</li>
 *   <li>рівно один {@code StoredConfig} на запит (V2);</li>
 *   <li>{@code attributes == null} у запиті → порожня мапа;</li>
 *   <li>невідомий флаг → звичайна 200-відповідь із
 *       {@code reason=FLAG_NOT_FOUND} (V3);</li>
 *   <li>{@code reason} у відповіді — {@code result.reason().name()}.</li>
 * </ol>
 */
@Path("/api/v1/evaluate")
public class EvaluationResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public EvaluateHttpResponse evaluate(@Valid EvaluateHttpRequest request) {
        throw new UnsupportedOperationException("02e: твоя реалізація");
    }
}
