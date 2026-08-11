package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.CreateEnvironmentRequest;
import io.praporets.controlplane.api.dto.EnvironmentResponse;
import io.praporets.controlplane.api.dto.RevisionResponse;
import io.praporets.controlplane.domain.Environment;
import io.praporets.controlplane.domain.EnvironmentRepository;
import io.praporets.controlplane.domain.RevisionLogRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * CRUD середовищ і читання журналу ревізій середовища.
 */
@Service
@Transactional(readOnly = true)
public class EnvironmentService {

    private final JsonMapper jsonMapper;
    private final RevisionRecorder revisionRecorder;
    private final EnvironmentRepository environmentRepository;
    private final RevisionLogRepository revisionLogRepository;


    public EnvironmentService(RevisionRecorder revisionRecorder, EnvironmentRepository environmentRepository,
                              RevisionLogRepository revisionLogRepository, JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.revisionRecorder = revisionRecorder;
        this.environmentRepository = environmentRepository;
        this.revisionLogRepository = revisionLogRepository;
    }

    /** Усі середовища, відсортовані за ключем. */
    public List<EnvironmentResponse> list() {
        return environmentRepository.findAll(Sort.by("key")).stream().map(e -> new EnvironmentResponse(
            e.getId(), e.getKey(), e.getName(), e.getRevision(), e.getCreatedAt()
        )).toList();
    }

    /**
     * Створює середовище + запис аудиту ({@code action=CREATE},
     * {@code entityType=ENVIRONMENT}, {@code before=null}). Ревізія НЕ
     * інкрементується — створення середовища не є зміною конфігурації,
     * видимою edge.
     *
     * @throws DomainValidationException якщо ключ уже зайнятий
     */
    @Transactional
    public EnvironmentResponse create(CreateEnvironmentRequest request, String actor) throws DomainValidationException {
        if (environmentRepository.findByKey(request.key()).isPresent()) {
            throw new DuplicateKeyException("Environment with key [" + request.key() + "] already exists");
        }
        Environment environment = environmentRepository.save(new Environment(request.key(), request.name()));
        EnvironmentResponse response = new EnvironmentResponse(
            environment.getId(), environment.getKey(), environment.getName(), environment.getRevision(), environment.getCreatedAt()
        );

        revisionRecorder.audit(actor, "CREATE", "ENVIRONMENT", environment.getId(), null, jsonMapper.valueToTree(response));
        return response;
    }

    /**
     * Останні ревізії середовища, новіші перші. Невідоме середовище дає
     * порожній список — існування тут не перевіряється.
     *
     * @param limit максимум записів (контролер дає дефолт 50)
     */
    public List<RevisionResponse> revisions(String environmentKey, int limit) {
        return revisionLogRepository.findByEnvironmentKeyOrderByRevisionDesc(environmentKey, Limit.of(limit)).stream().map(r -> new RevisionResponse(
            r.getRevision(), r.getChangeType(), r.getPayload(), r.getCreatedAt()
        )).toList();
    }
}
