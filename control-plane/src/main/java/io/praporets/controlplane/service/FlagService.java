package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.CreateFlagRequest;
import io.praporets.controlplane.api.dto.FlagResponse;
import io.praporets.controlplane.api.dto.UpdateFlagRequest;
import io.praporets.controlplane.api.dto.VariantDto;
import io.praporets.controlplane.domain.Flag;
import io.praporets.controlplane.domain.FlagRepository;
import io.praporets.controlplane.domain.ValueType;
import io.praporets.controlplane.domain.Variant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CRUD флагів. Флаг — глобальна сутність (не прив'язана до середовища):
 * зміни йдуть в audit_log, але НЕ створюють ревізію середовища — edge
 * бачить лише зміни конфігурацій, не довідника флагів.
 *
 * <p>Валідація понад Bean Validation живе тут і кидає
 * {@link DomainValidationException}: значення кожного варіанта має
 * відповідати {@code valueType} флага, ключі варіантів — унікальні в межах
 * флага, ключ флага — ще не зайнятий. Значення варіанта зберігається в
 * {@code Variant.value} як JSON-рядок і назад у {@code JsonNode} читається
 * через {@code readTree} — БД містить валідний JSON.
 */
@Service
@Transactional(readOnly = true)
public class FlagService {

    private final JsonMapper jsonMapper;
    private final FlagRepository flagRepository;
    private final RevisionRecorder revisionRecorder;

    public FlagService(FlagRepository flagRepository, JsonMapper jsonMapper, RevisionRecorder revisionRecorder) {
        this.jsonMapper = jsonMapper;
        this.flagRepository = flagRepository;
        this.revisionRecorder = revisionRecorder;
    }

    /**
     * Сторінка флагів. {@code archived == null} — всі; інакше фільтр за прапорцем.
     */
    public Page<FlagResponse> list(Boolean archived, Pageable pageable) {
        return (archived == null
            ? flagRepository.findAll(pageable)
            : flagRepository.findAllByArchived(archived, pageable)).map(this::toResponse);
    }

    /** @throws NotFoundException якщо флага немає */
    public FlagResponse get(String key) {
        return flagRepository.findByKeyWithVariants(key)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("Entity with key [" + key + "] not found"));
    }

    /**
     * Створює флаг разом із варіантами (каскадне збереження) + запис аудиту
     * ({@code CREATE}, {@code entityType=FLAG}, before=null, after=response).
     */
    @Transactional
    public FlagResponse create(CreateFlagRequest request, String actor) {
        if (request.variants().stream().map(VariantDto::key).distinct().count() != request.variants().size())
            throw new DomainValidationException("Duplicate variant keys");
        validateVariants(request);

        if (flagRepository.findByKey(request.key()).isPresent()) {
            throw new DuplicateKeyException("flag '%s' already exists".formatted(request.key()));
        }

        Flag flag = new Flag(request.key(), request.name(), request.description(), request.valueType());
        for (VariantDto variant : request.variants()) {
            flag.addVariant(new Variant(variant.key(), variant.value().toString()));
        }
        FlagResponse response = toResponse(flagRepository.save(flag));
        revisionRecorder.audit(actor, "CREATE", "FLAG", flag.getId(), null, jsonMapper.valueToTree(response));
        return response;
    }

    /**
     * PATCH-семантика: {@code null}-поля запиту не змінюються.
     *
     * @param expectedVersion значення з If-Match
     * @throws NotFoundException     якщо флага немає
     * @throws StaleVersionException якщо {@code expectedVersion} ≠ поточній версії (→ 409)
     */
    @Transactional
    public FlagResponse update(String key, long expectedVersion, UpdateFlagRequest request, String actor) {
        Flag flag = flagRepository.findByKeyWithVariants(key).orElseThrow(() -> new NotFoundException("Entity with key [" + key + "] not found"));
        if (flag.getVersion() != expectedVersion)
            throw new StaleVersionException(expectedVersion, flag.getVersion());

        FlagResponse prevResponse = toResponse(flag);

        if (request.name() != null) flag.setName(request.name());
        if (request.description() != null) flag.setDescription(request.description());

        flagRepository.flush();

        FlagResponse response = toResponse(flag);
        revisionRecorder.audit(actor, "UPDATE", "FLAG", flag.getId(), jsonMapper.valueToTree(prevResponse), jsonMapper.valueToTree(response));

        return response;
    }

    /**
     * Архівує флаг: DELETE у API — це {@code archived=true}, hard delete
     * зламав би історію ревізій. Ідемпотентно: повторна архівація не помилка.
     * Аудит: {@code action=ARCHIVE}, before/after зі зміною прапорця.
     *
     * @throws NotFoundException якщо флага немає
     */
    @Transactional
    public void archive(String key, String actor) {
        Flag flag = flagRepository.findByKeyWithVariants(key).orElseThrow(() -> new NotFoundException("Entity with key [" + key + "] not found"));

        FlagResponse prevResponse = toResponse(flag);

        flag.setArchived(true);
        revisionRecorder.audit(actor, "ARCHIVE", "FLAG", flag.getId(), jsonMapper.valueToTree(prevResponse), jsonMapper.valueToTree(toResponse(flag)));
    }

    private void validateVariants(CreateFlagRequest request) {
        ValueType expectedType = request.valueType();
        for (int i = 0; i < request.variants().size(); i++) {
            VariantDto variant = request.variants().get(i);

            if (variant == null || variant.value() == null || variant.value().isNull())
                continue;

            if (!isMatchingType(expectedType, variant.value()))
                throw new DomainValidationException(
                    String.format("Variant '%s' (at index %d) with value '%s' does not match type %s",
                        variant.key() != null ? variant.key() : "unknown",
                        i,
                        variant.value().asString(),
                        expectedType)
                );
        }
    }

    private boolean isMatchingType(ValueType type, JsonNode value) {
        return switch (type) {
            case BOOLEAN -> value.isBoolean();
            case NUMBER  -> value.isNumber();
            case STRING  -> value.isString();
            case JSON    -> value.isObject() || value.isArray();
        };
    }

    private FlagResponse toResponse(Flag flag) {
        return new FlagResponse(
                flag.getId(), flag.getKey(), flag.getName(), flag.getDescription(), flag.getValueType(), flag.isArchived(),
                flag.getVersion(), flag.getCreatedAt(),
                flag.getVariants()
                        .stream()
                        .map(v -> new VariantDto(v.getKey(), jsonMapper.readTree(v.getValue())))
                        .toList());
    }
}
