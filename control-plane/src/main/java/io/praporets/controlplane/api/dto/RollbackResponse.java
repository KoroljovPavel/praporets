package io.praporets.controlplane.api.dto;

/**
 * Результат rollback (H6).
 *
 * @param rolledBackTo ревізія, ДО стану якої відкотились
 * @param revision     нова поточна ревізія середовища (історія тільки росте:
 *                     кожне відновлення — нова ревізія, H4)
 */
public record RollbackResponse(
        String environmentKey,
        long rolledBackTo,
        long revision
) {
}
