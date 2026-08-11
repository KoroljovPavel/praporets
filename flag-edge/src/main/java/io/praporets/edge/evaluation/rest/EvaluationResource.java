package io.praporets.edge.evaluation.rest;

import io.praporets.core.evaluation.EvaluationResult;
import io.praporets.core.evaluation.Evaluator;
import io.praporets.core.model.EvaluationContext;
import io.praporets.edge.config.ConfigStore;
import io.praporets.edge.evaluation.rest.exceptions.EnvironmentNotLoadedException;
import io.praporets.edge.evaluation.rest.exceptions.UnknownEnvironmentException;
import io.praporets.edge.evaluation.rest.exceptions.ValidationProblemMapper;
import io.praporets.edge.events.EvaluationEvents;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * REST-обчислення одного флага: {@code POST /api/v1/evaluate} на спільному
 * HTTP-порту 8081 (там же health і metrics). Proto в REST-шляху не бере
 * участі: core-результат перекладається одразу в {@link EvaluateHttpResponse}.
 *
 * <p>Каркас той самий, що в gRPC-сервісі, але помилки — винятками:
 * <ol>
 *   <li>Bean Validation спрацьовує ДО тіла методу ({@code @Valid}) —
 *       {@code ConstraintViolationException} ловить
 *       {@link ValidationProblemMapper} → 400;</li>
 *   <li>чуже середовище → {@link UnknownEnvironmentException} → 404;</li>
 *   <li>конфігурація ще не завантажена →
 *       {@link EnvironmentNotLoadedException} → 503;</li>
 *   <li>рівно один {@code StoredConfig} на запит — конфігурація для
 *       обчислення і ревізія відповіді завжди узгоджені;</li>
 *   <li>{@code attributes == null} у запиті → порожня мапа;</li>
 *   <li>невідомий флаг → звичайна 200-відповідь із
 *       {@code reason=FLAG_NOT_FOUND};</li>
 *   <li>{@code reason} у відповіді — {@code result.reason().name()}.</li>
 * </ol>
 */
@Path("/api/v1/evaluate")
public class EvaluationResource {

    @Inject
    ConfigStore configStore;

    @Inject
    EvaluationEvents evaluationEvents;

    @ConfigProperty(name = "praporets.edge.environment")
    String environment;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public EvaluateHttpResponse evaluate(@Valid EvaluateHttpRequest request) {
        if (!request.environmentKey().equals(environment)) {
            throw new UnknownEnvironmentException(request.environmentKey());
        }

        Map<String, String> attributes = new HashMap<>();
        if (request.context().attributes() != null) {
            attributes.putAll(request.context().attributes());
        }

        ConfigStore.StoredConfig config = configStore.current().orElseThrow(() -> new EnvironmentNotLoadedException("Environment not loaded"));
        EvaluationResult result = Evaluator.evaluate(config.config(), request.flagKey(),
            new EvaluationContext(request.context().userKey(), attributes));

        evaluationEvents.emit(result, config.revision(), request.context().userKey());
        return new EvaluateHttpResponse(result.flagKey(), result.variantKey(), result.jsonValue(), result.reason().name(), result.ruleId(), config.revision());
    }
}
