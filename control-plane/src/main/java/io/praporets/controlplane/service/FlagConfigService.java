package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.FlagConfigResponse;
import io.praporets.controlplane.api.dto.UpsertFlagConfigRequest;
import io.praporets.controlplane.domain.*;
import io.praporets.core.model.Rule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Конфігурація флага в середовищі (CP-03) — серце кроку. Кожна зміна тут
 * видима edge → ревізія + аудит в одній транзакції (спека 7.3, кроки 1–4).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @Service} + {@code @Transactional}.
 * Доменна валідація ({@link DomainValidationException}): {@code defaultVariant}
 * і {@code offVariant} існують серед варіантів флага; варіанти в rollout-бакетах
 * і правилах теж (достатньо перевірити bucket-и rollout-ів і {@code variantKey}
 * правил — clauses валідує ядро при десеріалізації).
 */
@Service
@Transactional(readOnly = true)
public class FlagConfigService {

    private final JsonMapper jsonMapper;
    private final FlagRepository flagRepository;
    private final RevisionRecorder revisionRecorder;
    private final FlagConfigRepository flagConfigRepository;
    private final EnvironmentRepository environmentRepository;

    public FlagConfigService(JsonMapper jsonMapper, FlagRepository flagRepository, FlagConfigRepository flagConfigRepository,
                             EnvironmentRepository environmentRepository, RevisionRecorder revisionRecorder) {
        this.jsonMapper = jsonMapper;
        this.flagRepository = flagRepository;
        this.revisionRecorder = revisionRecorder;
        this.flagConfigRepository = flagConfigRepository;
        this.environmentRepository = environmentRepository;
    }

    /** @throws NotFoundException якщо середовища, флага або конфігурації немає */
    public FlagConfigResponse get(String environmentKey, String flagKey) {
        return flagConfigRepository.findByFlagKeyAndEnvironmentKey(flagKey, environmentKey).map(f -> new FlagConfigResponse(
            f.getId(), f.getFlag().getKey(), f.getEnvironment().getKey(), f.isEnabled(), f.getDefaultVariant(),
            f.getOffVariant(), f.getRules(), f.getRollout(), f.getVersion(), f.getUpdatedAt()
        )).orElseThrow(() -> new NotFoundException("Entity with flagKey [" + flagKey + "] and environmentKey " +
            "[" + environmentKey + "] not found"));
    }

    /**
     * Створення або повна заміна конфігурації (G4):
     * <ul>
     *   <li>конфігурації ще немає → {@code expectedVersion} має бути {@code null}
     *       (If-Match не надіслано) → створення, {@code created=true};</li>
     *   <li>конфігурація існує, {@code expectedVersion == null} →
     *       {@link DomainValidationException} («оновлення вимагає If-Match») → 400;</li>
     *   <li>існує, {@code expectedVersion} ≠ поточній версії →
     *       {@link StaleVersionException} → 409;</li>
     *   <li>існує, версія збіглась → заміна, {@code created=false}.</li>
     * </ul>
     * В одній транзакції: {@link RevisionRecorder#recordChange}
     * ({@code FLAG_CONFIG_UPDATED}, payload = {@code valueToTree(response)}) +
     * {@link RevisionRecorder#audit} ({@code CREATE}/{@code UPDATE},
     * {@code entityType=FLAG_CONFIG}, before = попередній response або null).
     *
     * @throws NotFoundException якщо середовища або флага немає
     */
    @Transactional
    public Upserted<FlagConfigResponse> upsert(String environmentKey, String flagKey,
                                               Long expectedVersion, UpsertFlagConfigRequest request,
                                               String actor) {
        boolean isNew = expectedVersion == null;

        Environment environment = environmentRepository.findByKey(environmentKey)
            .orElseThrow(() -> new NotFoundException("Environment with key [" + environmentKey + "] not found"));
        Flag flag = flagRepository.findByKey(flagKey)
            .orElseThrow(() -> new NotFoundException("Flag with key [" + flagKey + "] not found"));
        Optional<FlagConfig> optionalFlagConfig = flagConfigRepository.findByFlagKeyAndEnvironmentKey(flagKey, environmentKey);

        FlagConfig flagConfig;
        Optional<FlagConfigResponse> prevResponse = Optional.empty();

        if (optionalFlagConfig.isPresent()) {
            flagConfig = optionalFlagConfig.get();
            validateVersion(expectedVersion, flagConfig);
            prevResponse = Optional.of(toResponse(flagConfig));
        } else {
            if (!isNew)
                throw new DomainValidationException("expectedVersion must be null if flagConfig doesn't exist");

            flagConfig = new FlagConfig(flag, environment, request.defaultVariant(), request.offVariant());
        }

        Set<String> knownVariants = flag.getVariants().stream().map(Variant::getKey).collect(Collectors.toSet());
        Set<String> referencedVariants = new LinkedHashSet<>();
        referencedVariants.add(request.defaultVariant());
        referencedVariants.add(request.offVariant());

        List<Rule> rules = request.rules() == null ? List.of() : request.rules();
        for (Rule rule : rules) {
            if (rule.variantKey() != null) referencedVariants.add(rule.variantKey());
            if (rule.rollout() != null)
                rule.rollout().buckets().forEach(b -> referencedVariants.add(b.variantKey()));
        }
        if (request.rollout() != null)
            request.rollout().buckets().forEach(b -> referencedVariants.add(b.variantKey()));

        referencedVariants.removeAll(knownVariants);
        if (!referencedVariants.isEmpty())
            throw new DomainValidationException("unknown variants referenced: " + referencedVariants);

        flagConfig.setEnabled(request.enabled());
        flagConfig.setRules(rules);
        flagConfig.setRollout(request.rollout());
        flagConfig.setDefaultVariant(request.defaultVariant());
        flagConfig.setOffVariant(request.offVariant());

        flagConfig = flagConfigRepository.saveAndFlush(flagConfig);

        FlagConfigResponse response = toResponse(flagConfig);

        revisionRecorder.recordChange(response.environmentKey(), ChangeType.FLAG_CONFIG_UPDATED, jsonMapper.valueToTree(response));
        revisionRecorder.audit(actor, isNew ? "CREATE" : "UPDATE", "FLAG_CONFIG", flagConfig.getId(),
            prevResponse.<JsonNode>map(jsonMapper::valueToTree).orElse(null), jsonMapper.valueToTree(response));

        return new Upserted<>(response, isNew);
    }

    private void validateVersion(Long expectedVersion, FlagConfig flagConfig) {
        if (expectedVersion == null)
            throw new DomainValidationException("expectedVersion must be non-null if flagConfig exists");
        if (flagConfig.getVersion() != expectedVersion)
            throw new StaleVersionException(expectedVersion, flagConfig.getVersion());
    }

    /**
     * Kill switch (G5): перемикає {@code enabled} БЕЗ If-Match — last-write-wins
     * свідомо. Ревізія ({@code FLAG_TOGGLED}) + аудит ({@code TOGGLE}) як завжди.
     * Ідемпотентність не потрібна: повторний toggle у той самий стан — теж ревізія
     * (простіше і чесніше для журналу).
     *
     * @throws NotFoundException якщо конфігурації немає
     */
    @Transactional
    public FlagConfigResponse toggle(String environmentKey, String flagKey, boolean enabled, String actor) {
        FlagConfig flagConfig = flagConfigRepository.findByFlagKeyAndEnvironmentKey(flagKey, environmentKey)
            .orElseThrow(() -> new NotFoundException("Entity with flagKey [" + flagKey + "] and environmentKey [" + environmentKey + "] not found"));

        FlagConfigResponse prevResponse = toResponse(flagConfig);

        flagConfig.setEnabled(enabled);

        flagConfigRepository.flush();

        FlagConfigResponse response = toResponse(flagConfig);

        revisionRecorder.recordChange(response.environmentKey(), ChangeType.FLAG_TOGGLED, jsonMapper.valueToTree(response));
        revisionRecorder.audit(actor, "TOGGLE", "FLAG_CONFIG", flagConfig.getId(),
            jsonMapper.valueToTree(prevResponse), jsonMapper.valueToTree(response));

        return response;
    }

    private static FlagConfigResponse toResponse(FlagConfig flagConfig) {
        return new FlagConfigResponse(flagConfig.getId(), flagConfig.getFlag().getKey(),
            flagConfig.getEnvironment().getKey(), flagConfig.isEnabled(), flagConfig.getDefaultVariant(),
            flagConfig.getOffVariant(), flagConfig.getRules(), flagConfig.getRollout(),
            flagConfig.getVersion(), flagConfig.getUpdatedAt());
    }
}
