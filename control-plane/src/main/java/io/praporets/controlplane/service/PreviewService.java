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
 * Dry-run обчислення флага через {@code praporets-core} — та сама бібліотека,
 * що працює на edge, тож preview ніколи не розійдеться з реальним
 * обчисленням.
 *
 * <p>Чистий dry-run — жодних викликів {@code RevisionRecorder}, транзакція
 * {@code readOnly}: після preview журнал ревізій і аудит не ростуть.
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

    /**
     * Обчислює флаг для заданого контексту без побічних ефектів. Невідомий
     * флаг — не помилка: ядро поверне {@code FLAG_NOT_FOUND}, а {@code value}
     * у відповіді буде {@code null}. {@code revision} у відповіді — поточна
     * ревізія середовища.
     *
     * @throws NotFoundException якщо середовища немає (єдиний не-200 сценарій)
     */
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
