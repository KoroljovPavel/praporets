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
 * Єдина точка перекладу винятків у RFC 9457 Problem Details — Spring сам
 * виставляє {@code Content-Type: application/problem+json}.
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
 * <p>Успадкування від {@code ResponseEntityExceptionHandler} дає безкоштовно
 * переклад Bean Validation ({@code MethodArgumentNotValidException}) і битого
 * JSON ({@code HttpMessageNotReadableException} — сюди ж загорнуті IAE з
 * канонічних конструкторів core-records).
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
