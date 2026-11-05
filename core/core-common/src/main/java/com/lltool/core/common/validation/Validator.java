package com.lltool.core.common.validation;

import com.lltool.core.common.error.AppError;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fluent builder for validating object properties and domain invariants.
 */
public class Validator<T> {

    private final T target;
    private final List<AppError.ValidationError> errors = new ArrayList<>();

    private Validator(T target) {
        this.target = target;
    }

    public static <T> Validator<T> of(T target) {
        return new Validator<>(target);
    }

    public Validator<T> check(Predicate<T> condition, String errorCode, String errorMessage) {
        if (!condition.test(target)) {
            errors.add(AppError.validation(errorCode, errorMessage));
        }
        return this;
    }

    public Validator<T> notNull(String errorCode, String errorMessage) {
        if (target == null) {
            errors.add(AppError.validation(errorCode, errorMessage));
        }
        return this;
    }

    public ValidationResult validate() {
        return ValidationResult.of(errors);
    }
}

