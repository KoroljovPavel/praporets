package io.praporets.controlplane.service;

/** Сутність не знайдено → 404 у {@code ApiExceptionHandler}. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
