package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.FlagConfigResponse;
import io.praporets.controlplane.api.dto.ToggleRequest;
import io.praporets.controlplane.api.dto.UpsertFlagConfigRequest;
import io.praporets.controlplane.service.FlagConfigService;
import io.praporets.controlplane.service.Upserted;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/v1/environments/{env}/flags/{key}/config} + {@code .../toggle} (CP-03).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @RestController} + анотації.
 * If-Match тут ОПЦІЙНИЙ на рівні HTTP ({@code @RequestHeader(required = false)
 * Long}) — обов'язковість залежить від того, чи існує конфігурація, і це
 * вирішує сервіс (G4). Toggle — без If-Match узагалі (G5).
 */
@RestController
@RequestMapping("/api/v1/environments/{env}/flags/{key}")
public class FlagConfigController {

    private final FlagConfigService configs;

    public FlagConfigController(FlagConfigService configs) {
        this.configs = configs;
    }

    /** {@code GET} → 200 | 404. */
    @GetMapping("/config")
    public FlagConfigResponse get(@PathVariable(name = "env") String environmentKey, @PathVariable(name = "key") String flagKey) {
        return configs.get(environmentKey, flagKey);
    }

    /** {@code PUT} → 201 (створено, без If-Match) | 200 | 400 (оновлення без If-Match) | 409 (stale). */
    @PutMapping("/config")
    public ResponseEntity<FlagConfigResponse> upsert(@PathVariable(name = "env") String environmentKey,
                                                     @PathVariable(name = "key") String flagKey,
                                                     @RequestHeader(name = "If-Match", required = false) Long ifMatch,
                                                     @RequestBody @Valid UpsertFlagConfigRequest request,
                                                     @RequestHeader(name = "X-Actor", defaultValue = "anonymous") String actor) {

        Upserted<FlagConfigResponse> response = configs.upsert(environmentKey, flagKey, ifMatch, request, actor);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK).body(response.body());
    }

    /** {@code POST .../toggle} → 200. */
    @PostMapping("/toggle")
    public FlagConfigResponse toggle(@PathVariable(name = "env") String environmentKey,
                                     @PathVariable(name = "key") String flagKey,
                                     @RequestBody @Valid ToggleRequest request,
                                     @RequestHeader(name = "X-Actor", defaultValue = "anonymous") String actor) {

        return configs.toggle(environmentKey, flagKey, request.enabled(), actor);
    }
}
