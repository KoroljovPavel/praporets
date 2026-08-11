package io.praporets.controlplane.service;

/**
 * If-Match не збігся з поточною версією → 409. Клієнт має
 * перечитати ресурс і повторити зміну поверх свіжої версії.
 */
public class StaleVersionException extends RuntimeException {

    public StaleVersionException(long expected, long actual) {
        super("expected version %d but current version is %d".formatted(expected, actual));
    }
}
