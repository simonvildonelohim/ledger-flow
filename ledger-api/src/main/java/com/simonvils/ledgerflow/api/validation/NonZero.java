package com.simonvils.ledgerflow.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated numeric value must not be zero.
 *
 * <p>Bean Validation ships no such constraint: {@code @Positive} and
 * {@code @Negative} each rule out one sign, and a ledger accepts both — a credit
 * and a debit are equally valid. Only zero is meaningless, since it moves no
 * money while still consuming an identifier and an event.
 *
 * <p>A {@code null} value is considered valid, so that presence is expressed by
 * {@code @NotNull} rather than conflated with this constraint.
 */
@Documented
@Constraint(validatedBy = NonZeroValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NonZero {

    String message() default "must not be zero";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
