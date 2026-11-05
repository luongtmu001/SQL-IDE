package com.lltool.core.domain.workspace.valueobject;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;

import java.io.Serializable;
import java.util.Objects;

public record WorkspaceName(String value) implements Serializable {

    public WorkspaceName {
        Objects.requireNonNull(value, "WorkspaceName cannot be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("WorkspaceName cannot be empty");
        }
        value = trimmed;
    }

    public static Result<WorkspaceName> create(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return Result.failure(AppError.validation(AppError.INVALID_WORKSPACE_NAME, "Workspace name cannot be empty"));
        }
        return Result.success(new WorkspaceName(rawName.trim()));
    }

    public static WorkspaceName of(String name) {
        return new WorkspaceName(name);
    }
}

