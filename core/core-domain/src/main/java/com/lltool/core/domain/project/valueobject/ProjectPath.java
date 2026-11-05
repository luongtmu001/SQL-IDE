package com.lltool.core.domain.project.valueobject;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;

import java.io.Serializable;
import java.util.Objects;

/**
 * Validated Project filesystem root path value object.
 */
public record ProjectPath(String value) implements Serializable {

    public ProjectPath {
        Objects.requireNonNull(value, "ProjectPath cannot be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("ProjectPath cannot be empty");
        }
        value = trimmed.replace('\\', '/');
    }

    public static Result<ProjectPath> create(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return Result.failure(AppError.validation(AppError.INVALID_PROJECT_PATH, "Project path cannot be empty"));
        }
        return Result.success(new ProjectPath(rawPath));
    }

    public static ProjectPath of(String path) {
        return new ProjectPath(path);
    }
}

