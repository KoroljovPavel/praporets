package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.CreateEnvironmentRequest;
import io.praporets.controlplane.api.dto.EnvironmentResponse;
import io.praporets.controlplane.api.dto.RevisionResponse;

import java.util.List;

/**
 * CRUD середовищ (CP-01). Найпростіший сервіс кроку — почни з нього.
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @Service}; клас-рівневий
 * {@code @Transactional} або на методах, читання — {@code readOnly = true}.
 * Ін'єкція — через конструктор (ArchUnit валить field injection).
 */
public class EnvironmentService {

    /** Усі середовища, відсортовані за ключем. */
    public List<EnvironmentResponse> list() {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /**
     * Створює середовище + запис аудиту ({@code action=CREATE},
     * {@code entityType=ENVIRONMENT}, {@code before=null}). Ревізія НЕ
     * інкрементується (G6 — середовище не видиме edge як зміна конфігурації).
     *
     * @throws DomainValidationException якщо ключ уже зайнятий
     */
    public EnvironmentResponse create(CreateEnvironmentRequest request, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /**
     * Останні ревізії середовища, новіші перші
     * ({@code RevisionLogRepository.findByEnvironmentKeyOrderByRevisionDesc}).
     *
     * @param limit максимум записів (контролер дає дефолт 50)
     * @throws NotFoundException якщо середовища немає
     */
    public List<RevisionResponse> revisions(String environmentKey, int limit) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
