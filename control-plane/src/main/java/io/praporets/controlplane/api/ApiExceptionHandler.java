package io.praporets.controlplane.api;

import io.praporets.controlplane.service.DomainValidationException;
import io.praporets.controlplane.service.NotFoundException;
import io.praporets.controlplane.service.StaleVersionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

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
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({ StaleVersionException.class, ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class })
    public ProblemDetail handleConflict(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({ DomainValidationException.class, MissingRequestHeaderException.class })
    public ProblemDetail handleBadRequest(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
