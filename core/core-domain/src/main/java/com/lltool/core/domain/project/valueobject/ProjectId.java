package com.lltool.core.domain.project.valueobject;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.identity.EntityId;
import com.lltool.core.common.result.Result;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for a Project.
 */
public record ProjectId(String value) implements EntityId<String> {

    public ProjectId {
        Objects.requireNonNull(value, "ProjectId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProjectId value cannot be blank");
        }
    }

    public static ProjectId generate() {
        return new ProjectId(UUID.randomUUID().toString());
    }

    public static ProjectId of(String value) {
        return new ProjectId(value);
    }

    public static Result<ProjectId> create(String value) {
        if (value == null || value.isBlank()) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Project ID cannot be empty"));
        }
        return Result.success(new ProjectId(value.trim()));
    }
}

