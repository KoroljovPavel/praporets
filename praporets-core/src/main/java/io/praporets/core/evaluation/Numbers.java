package io.praporets.core.evaluation;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Парсинг чисел за правилами, невалідне -> empty, ніколи не кидає exceptions
 */
final class Numbers {
    private Numbers() {}

    static Optional<BigDecimal> parse(String raw) {
        try {
            return Optional.of(new BigDecimal(raw));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
