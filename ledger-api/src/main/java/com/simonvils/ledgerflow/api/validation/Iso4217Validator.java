package com.simonvils.ledgerflow.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;

/**
 * Validator for {@link Iso4217}.
 *
 * <p>{@link Currency#getInstance(String)} throws on anything it does not
 * recognise, including lowercase input, so this also enforces the uppercase form
 * the standard specifies.
 */
public class Iso4217Validator implements ConstraintValidator<Iso4217, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            Currency.getInstance(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException ex) {
            return false;
        }
    }
}
