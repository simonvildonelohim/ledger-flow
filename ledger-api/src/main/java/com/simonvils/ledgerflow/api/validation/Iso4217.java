package com.simonvils.ledgerflow.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be a currency code the JDK recognises under
 * ISO-4217.
 *
 * <p>Checked against {@link java.util.Currency} rather than a three-letter
 * pattern. A pattern accepts {@code XXX} and {@code ABC}, which are not
 * currencies; storing one means the amount beside it has no defined meaning, and
 * nothing downstream would notice.
 *
 * <p>A {@code null} value is considered valid, leaving presence to
 * {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = Iso4217Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Iso4217 {

    String message() default "must be a valid ISO-4217 currency code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
