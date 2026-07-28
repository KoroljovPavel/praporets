package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.EvaluatePreviewRequest;
import io.praporets.controlplane.api.dto.EvaluatePreviewResponse;
import io.praporets.controlplane.domain.Environment;
import io.praporets.controlplane.domain.EnvironmentRepository;
import io.praporets.core.evaluation.EvaluationResult;
import io.praporets.core.evaluation.Evaluator;
import io.praporets.core.model.EnvironmentConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dry-run обчислення через {@code praporets-core} (CP-11, H2/H3). Перше місце,
 * де control-plane ВИКОРИСТОВУЄ ядро — та сама бібліотека, що працюватиме на
 * edge, тож preview ніколи не розійдеться з реальним обчисленням.
 *
 * <p><b>Реалізація (твоя робота):</b>
 * <ol>
 *   <li>середовище за ключем — немає → {@link NotFoundException} (єдиний
 *       не-200 сценарій, H2); з нього ж бери {@code revision} для відповіді;</li>
 *   <li>{@code assembler.assembleForFlag(...)} →
 *       {@code Evaluator.evaluate(config, flagKey, context)};</li>
 *   <li>відповідь: {@code value = result.jsonValue() == null ? null :
 *       jsonMapper.readTree(result.jsonValue())} — обережно, D6 і
 *       FLAG_NOT_FOUND дають {@code null}.</li>
 * </ol>
 *
 * <p><b>H3:</b> чистий dry-run — ЖОДНИХ викликів {@code RevisionRecorder};
 * {@code readOnly = true} і по духу, і по букві (тест пінить, що журнал
 * після preview не росте).
 */
@Service
@Transactional(readOnly = true)
public class PreviewService {

    private final JsonMapper jsonMapper;
    private final EnvironmentConfigAssembler assembler;
    private final EnvironmentRepository environmentRepository;

    public PreviewService(JsonMapper jsonMapper, EnvironmentRepository environmentRepository,
                          EnvironmentConfigAssembler assembler) {
        this.jsonMapper = jsonMapper;
        this.assembler = assembler;
        this.environmentRepository = environmentRepository;
    }

    /** @throws NotFoundException якщо середовища немає (єдиний не-200, H2) */
    public EvaluatePreviewResponse preview(EvaluatePreviewRequest request) {
        Environment environment = environmentRepository.findByKey(request.environment())
            .orElseThrow(() -> new NotFoundException("Entity with key [" + request.environment() + "] not found"));
        EnvironmentConfig config = assembler.assembleForFlag(environment.getKey(), request.flagKey());
        EvaluationResult result = Evaluator.evaluate(config, request.flagKey(), request.context());

        return new EvaluatePreviewResponse(result.flagKey(), result.variantKey(),
            result.jsonValue() == null ? null : jsonMapper.readTree(result.jsonValue()),
            result.reason(), result.ruleId(), environment.getRevision());
    }
}
