package com.lltool.core.domain.workspace.valueobject;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.identity.EntityId;
import com.lltool.core.common.result.Result;

import java.util.Objects;
import java.util.UUID;

public record WorkspaceId(String value) implements EntityId<String> {

    public WorkspaceId {
        Objects.requireNonNull(value, "WorkspaceId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("WorkspaceId cannot be blank");
        }
    }

    public static WorkspaceId generate() {
        return new WorkspaceId(UUID.randomUUID().toString());
    }

    public static WorkspaceId of(String value) {
        return new WorkspaceId(value);
    }

    public static Result<WorkspaceId> create(String value) {
        if (value == null || value.isBlank()) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Workspace ID cannot be empty"));
        }
        return Result.success(new WorkspaceId(value.trim()));
    }
}

