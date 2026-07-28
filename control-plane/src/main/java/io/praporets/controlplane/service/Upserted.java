package io.praporets.controlplane.service;

/**
 * Результат upsert-операції: тіло відповіді + чи була сутність створена
 * (контролеру треба вибрати між 201 і 200).
 */
public record Upserted<T>(T body, boolean created) {
}
