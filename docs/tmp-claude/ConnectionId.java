package com.lltool.core.domain.connection.valueobject;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.identity.EntityId;
import com.lltool.core.common.result.Result;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for a Database Connection.
 */
public record ConnectionId(String value) implements EntityId<String> {

    public ConnectionId {
        Objects.requireNonNull(value, "ConnectionId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ConnectionId value cannot be blank");
        }
    }

    public static ConnectionId generate() {
        return new ConnectionId(UUID.randomUUID().toString());
    }

    public static ConnectionId of(String value) {
        return new ConnectionId(value);
    }

    public static Result<ConnectionId> create(String value) {
        if (value == null || value.isBlank()) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Connection ID cannot be empty"));
        }
        return Result.success(new ConnectionId(value.trim()));
    }
}

