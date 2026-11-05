package com.lltool.core.domain.project.valueobject;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validated Project Name value object.
 * Enforces business constraints: 1-100 characters, no illegal characters, trimmed.
 */
public record ProjectName(String value) implements Serializable {

    private static final int MAX_LENGTH = 100;
    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    public ProjectName {
        Objects.requireNonNull(value, "ProjectName cannot be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("ProjectName cannot be empty");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("ProjectName cannot exceed " + MAX_LENGTH + " characters");
        }
        if (ILLEGAL_CHARS.matcher(trimmed).find()) {
            throw new IllegalArgumentException("ProjectName contains illegal characters (\\ / : * ? \" < > |)");
        }
        value = trimmed;
    }

    public static Result<ProjectName> create(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return Result.failure(AppError.validation(AppError.INVALID_PROJECT_NAME, "Project name cannot be empty"));
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > MAX_LENGTH) {
            return Result.failure(AppError.validation(AppError.INVALID_PROJECT_NAME,
                    "Project name exceeds maximum length of " + MAX_LENGTH + " characters"));
        }
        if (ILLEGAL_CHARS.matcher(trimmed).find()) {
            return Result.failure(AppError.validation(AppError.INVALID_PROJECT_NAME,
                    "Project name contains invalid filesystem characters"));
        }
        return Result.success(new ProjectName(trimmed));
    }

    public static ProjectName of(String name) {
        return new ProjectName(name);
    }
}

