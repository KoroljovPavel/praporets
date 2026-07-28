package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.FlagConfigResponse;
import io.praporets.controlplane.api.dto.ToggleRequest;
import io.praporets.controlplane.api.dto.UpsertFlagConfigRequest;
import io.praporets.controlplane.service.FlagConfigService;
import org.springframework.http.ResponseEntity;

/**
 * {@code /api/v1/environments/{env}/flags/{key}/config} + {@code .../toggle} (CP-03).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @RestController} + анотації.
 * If-Match тут ОПЦІЙНИЙ на рівні HTTP ({@code @RequestHeader(required = false)
 * Long}) — обов'язковість залежить від того, чи існує конфігурація, і це
 * вирішує сервіс (G4). Toggle — без If-Match узагалі (G5).
 */
public class FlagConfigController {

    private final FlagConfigService configs;

    public FlagConfigController(FlagConfigService configs) {
        this.configs = configs;
    }

    /** {@code GET} → 200 | 404. */
    public FlagConfigResponse get(String environmentKey, String flagKey) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** {@code PUT} → 201 (створено, без If-Match) | 200 | 400 (оновлення без If-Match) | 409 (stale). */
    public ResponseEntity<FlagConfigResponse> upsert(String environmentKey, String flagKey,
                                                     Long ifMatch, UpsertFlagConfigRequest request,
                                                     String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }

    /** {@code POST .../toggle} → 200. */
    public FlagConfigResponse toggle(String environmentKey, String flagKey,
                                     ToggleRequest request, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
