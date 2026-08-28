package com.simonvils.ledgerflow.api.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Turns exceptions into RFC 9457 problem details.
 *
 * <p>Most of the work is done by {@code spring.mvc.problemdetails.enabled}, which
 * already renders Spring's own exceptions — a missing header, a malformed body —
 * as {@code application/problem+json}. This class covers the cases where the
 * default response is not useful enough on its own.
 *
 * <p>There is deliberately no handler for {@code Exception}. A blanket handler
 * here would take precedence over Spring's exception resolution and intercept
 * well-formed 4xx responses on their way out, reporting a client's malformed JSON
 * as a server error. Anything not handled below keeps Spring's own status, and
 * stack traces stay out of responses by default.
 */
// Spring Boot registers its own problem-details advice at order 0 when
// spring.mvc.problemdetails.enabled is set. Without an explicit precedence
// this class never gets the exception: the default advice answers first.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final URI VALIDATION_FAILED =
            URI.create("https://github.com/simonvildonelohim/ledger-flow/problems/validation-failed");

    private static final URI CONFLICT =
            URI.create("https://github.com/simonvildonelohim/ledger-flow/problems/conflict");

    /**
     * Validation failures on a controller method that carries constraints on its
     * own parameters.
     *
     * <p>This is the case that actually fires for {@code POST /transactions}. Once
     * a method has a constraint directly on a parameter — the {@code @NotBlank} on
     * the idempotency key header — Spring applies method validation to the whole
     * method, and a rejected {@code @Valid @RequestBody} surfaces here rather than
     * as {@link MethodArgumentNotValidException}.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleInvalidRequest(HandlerMethodValidationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

            for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                // A rejected @Valid body: errors are per field.
                for (FieldError fieldError : parameterErrors.getFieldErrors()) {
                    record(errors, fieldError.getField(), fieldError.getDefaultMessage());
                }
            } else {
                // A rejected parameter, such as the header. There is no field name,
                // so the parameter name identifies it.
                String name = result.getMethodParameter().getParameterName();
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    record(errors, name == null ? "request" : name, error.getDefaultMessage());
                }
            }
        }

        return validationProblem(errors);
    }

    /**
     * A request body that failed Bean Validation on a method with no other
     * constrained parameters. Kept so the behaviour does not depend on whether a
     * given endpoint happens to constrain a parameter.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            record(errors, fieldError.getField(), fieldError.getDefaultMessage());
        }

        return validationProblem(errors);
    }

    /**
     * A database constraint the application did not anticipate.
     *
     * <p>409 rather than 500: a constraint violation means the request conflicts
     * with data that already exists, which the caller can act on. The exception
     * message is not echoed — it carries table and constraint names that describe
     * the schema and belong in logs, not in a response.
     *
     * <p>Note that a repeated {@code Idempotency-Key} never reaches this handler.
     * That conflict is resolved in the repository and answered with 200 and the
     * original transaction, because a retry is normal client behaviour rather than
     * an error.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConflict(DataIntegrityViolationException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT, "The request conflicts with existing data.");
        problem.setTitle("Conflict");
        problem.setType(CONFLICT);
        return problem;
    }

    private static ProblemDetail validationProblem(Map<String, String> errors) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setTitle("Invalid request");
        problem.setType(VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return problem;
    }

    private static void record(Map<String, String> errors, String name, String message) {
        errors.merge(
                name,
                message == null ? "is invalid" : message,
                (existing, added) -> existing + "; " + added);
    }
}
