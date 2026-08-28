package com.simonvils.ledgerflow.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Validator for {@link NonZero}. */
public class NonZeroValidator implements ConstraintValidator<NonZero, Long> {

    @Override
    public boolean isValid(Long value, ConstraintValidatorContext context) {
        return value == null || value != 0L;
    }
}
