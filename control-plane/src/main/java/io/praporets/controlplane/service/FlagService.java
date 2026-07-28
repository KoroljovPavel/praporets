package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.CreateFlagRequest;
import io.praporets.controlplane.api.dto.FlagResponse;
import io.praporets.controlplane.api.dto.UpdateFlagRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * CRUD флагів (CP-02). Флаг — глобальна сутність: зміни йдуть в audit_log,
 * але НЕ створюють ревізію середовища (G6).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @Service} + {@code @Transactional}.
 * Валідація понад Bean Validation — тут, кидай {@link DomainValidationException}:
 * <ul>
 *   <li>значення кожного варіанта відповідає {@code valueType}
 *       (BOOLEAN → {@code JsonNode.isBoolean()}, NUMBER → {@code isNumber()},
 *       STRING → {@code isTextual()}, JSON → будь-що);</li>
 *   <li>ключі варіантів унікальні в межах флага;</li>
 *   <li>ключ флага ще не зайнятий.</li>
 * </ul>
 * Значення варіанта зберігається в {@code Variant.value} як JSON-рядок
 * ({@code JsonNode#toString()}); назад у {@code JsonNode} —
 * {@code ObjectMapper#readTree} (кинутий {@code JsonProcessingException}
 * можна загортати в {@code IllegalStateException} — БД містить валідний JSON).
 */
public class FlagService {

    /**
     * Сторінка флагів. {@code archived == null} — всі; інакше фільтр за прапорцем.
     * Знадобиться метод репозиторію {@code findAllByArchived(boolean, Pageable)}.
     */
    public Page<FlagResponse> list(Boolean archived, Pageable pageable) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** @throws NotFoundException якщо флага немає */
    public FlagResponse get(String key) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /**
     * Створює флаг разом із варіантами (каскад із 01f) + аудит
     * ({@code CREATE}, {@code entityType=FLAG}, before=null, after=response).
     */
    public FlagResponse create(CreateFlagRequest request, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /**
     * PATCH-семантика: {@code null}-поля запиту не змінюються.
     *
     * @param expectedVersion значення з If-Match
     * @throws NotFoundException     якщо флага немає
     * @throws StaleVersionException якщо {@code expectedVersion} ≠ поточній версії (→ 409)
     */
    public FlagResponse update(String key, long expectedVersion, UpdateFlagRequest request, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /**
     * Архівує флаг (G7 спеки кроку: DELETE — це {@code archived=true},
     * hard delete зламав би історію ревізій). Ідемпотентно: повторна архівація
     * не помилка. Аудит: {@code action=ARCHIVE}, before/after зі зміною прапорця.
     *
     * @throws NotFoundException якщо флага немає
     */
    public void archive(String key, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
