package io.praporets.controlplane.api;

/**
 * Єдина точка перекладу винятків у RFC 9457 Problem Details (G3).
 * Самописний формат {"error": ...} заборонений спекою (розділ 6.3).
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @RestControllerAdvice}; методи
 * {@code @ExceptionHandler}, які повертають {@code ProblemDetail}
 * ({@code ProblemDetail.forStatusAndDetail(...)}). Spring сам виставить
 * {@code Content-Type: application/problem+json} — тести це перевіряють.
 *
 * <p>Мапінг:
 * <ul>
 *   <li>{@code NotFoundException} → 404;</li>
 *   <li>{@code StaleVersionException},
 *       {@code ObjectOptimisticLockingFailureException} (страховка @Version),
 *       {@code DataIntegrityViolationException} (unique constraint під гонкою) → 409;</li>
 *   <li>{@code DomainValidationException} → 400;</li>
 *   <li>{@code MissingRequestHeaderException} (немає If-Match) → 400.</li>
 * </ul>
 *
 * <p>Bean Validation ({@code MethodArgumentNotValidException}) і битий JSON
 * ({@code HttpMessageNotReadableException} — сюди ж загорнуті IAE з канонічних
 * конструкторів core-records) вже перекладає в ProblemDetail базовий
 * {@code ResponseEntityExceptionHandler} — успадкуйся від нього, і ці два
 * кейси дістанеш безкоштовно.
 */
public class ApiExceptionHandler {
}
