package com.lltool.core.common.validation;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the outcome of input or domain validation rules.
 */
public record ValidationResult(List<AppError.ValidationError> errors) {

    public ValidationResult {
        errors = errors != null ? Collections.unmodifiableList(errors) : Collections.emptyList();
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasErrors() {
        return !isValid();
    }

    public <T> Result<T> toResult(T value) {
        if (isValid()) {
            return Result.success(value);
        }
        AppError.ValidationError first = errors.getFirst();
        return Result.failure(AppError.validation(
                first.code(),
                first.message(),
                Map.of("totalErrors", errors.size(), "errors", errors)
        ));
    }

    public static ValidationResult valid() {
        return new ValidationResult(Collections.emptyList());
    }

    public static ValidationResult invalid(AppError.ValidationError error) {
        return new ValidationResult(List.of(error));
    }

    public static ValidationResult of(List<AppError.ValidationError> errors) {
        return new ValidationResult(errors);
    }
}

