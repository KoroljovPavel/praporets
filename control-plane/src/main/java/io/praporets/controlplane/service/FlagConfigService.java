package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.FlagConfigResponse;
import io.praporets.controlplane.api.dto.UpsertFlagConfigRequest;

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
public class FlagConfigService {

    /** @throws NotFoundException якщо середовища, флага або конфігурації немає */
    public FlagConfigResponse get(String environmentKey, String flagKey) {
        throw new UnsupportedOperationException("не реалізовано");
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
    public Upserted<FlagConfigResponse> upsert(String environmentKey, String flagKey,
                                               Long expectedVersion, UpsertFlagConfigRequest request,
                                               String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /**
     * Kill switch (G5): перемикає {@code enabled} БЕЗ If-Match — last-write-wins
     * свідомо. Ревізія ({@code FLAG_TOGGLED}) + аудит ({@code TOGGLE}) як завжди.
     * Ідемпотентність не потрібна: повторний toggle у той самий стан — теж ревізія
     * (простіше і чесніше для журналу).
     *
     * @throws NotFoundException якщо конфігурації немає
     */
    public FlagConfigResponse toggle(String environmentKey, String flagKey, boolean enabled, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
