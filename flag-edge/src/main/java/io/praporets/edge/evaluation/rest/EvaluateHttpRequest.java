package io.praporets.edge.evaluation.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Тіло {@code POST /api/v1/evaluate} — дзеркало gRPC {@code EvaluateRequest}
 * (V5). ГОТОВИЙ контракт: поля і валідація пінятся тестами.
 *
 * <p>{@code attributes} може бути відсутнім у JSON → {@code null} тут;
 * нормалізація в порожню мапу — справа ресурсу, не DTO.
 */
public record EvaluateHttpRequest(
    @NotBlank String environmentKey,
    @NotBlank String flagKey,
    @NotNull @Valid Context context) {

    public record Context(
        @NotBlank String userKey,
        Map<String, String> attributes) {
    }
}
