package io.praporets.controlplane.service;

/**
 * Порушення доменного правила, яке не ловиться Bean Validation
 * (значення варіанта не відповідає типу флага, defaultVariant не існує тощо) → 400.
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
