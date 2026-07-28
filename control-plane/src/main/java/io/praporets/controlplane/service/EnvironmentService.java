package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.CreateEnvironmentRequest;
import io.praporets.controlplane.api.dto.EnvironmentResponse;
import io.praporets.controlplane.api.dto.RevisionResponse;
import io.praporets.controlplane.domain.Environment;
import io.praporets.controlplane.domain.EnvironmentRepository;
import io.praporets.controlplane.domain.RevisionLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * CRUD середовищ (CP-01). Найпростіший сервіс кроку — почни з нього.
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @Service}; клас-рівневий
 * {@code @Transactional} або на методах, читання — {@code readOnly = true}.
 * Ін'єкція — через конструктор (ArchUnit валить field injection).
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
     * інкрементується (G6 — середовище не видиме edge як зміна конфігурації).
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
     * Останні ревізії середовища, новіші перші
     * ({@code RevisionLogRepository.findByEnvironmentKeyOrderByRevisionDesc}).
     *
     * @param limit максимум записів (контролер дає дефолт 50)
     * @throws NotFoundException якщо середовища немає
     */
    public List<RevisionResponse> revisions(String environmentKey, int limit) {
        return revisionLogRepository.findByEnvironmentKeyOrderByRevisionDesc(environmentKey, Limit.of(limit)).stream().map(r -> new RevisionResponse(
            r.getRevision(), r.getChangeType(), r.getPayload(), r.getCreatedAt()
        )).toList();
    }
}
