package com.lltool.core.domain.connection.valueobject;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validated Connection Name value object.
 */
public record ConnectionName(String value) implements Serializable {

    private static final int MAX_LENGTH = 100;
    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    public ConnectionName {
        Objects.requireNonNull(value, "ConnectionName cannot be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("ConnectionName cannot be empty");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("ConnectionName cannot exceed " + MAX_LENGTH + " characters");
        }
        if (ILLEGAL_CHARS.matcher(trimmed).find()) {
            throw new IllegalArgumentException("ConnectionName contains illegal characters (\\ / : * ? \" < > |)");
        }
        value = trimmed;
    }

    public static Result<ConnectionName> create(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return Result.failure(AppError.validation(AppError.INVALID_CONNECTION_NAME, "Connection name cannot be empty"));
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > MAX_LENGTH) {
            return Result.failure(AppError.validation(AppError.INVALID_CONNECTION_NAME,
                    "Connection name exceeds maximum length of " + MAX_LENGTH + " characters"));
        }
        if (ILLEGAL_CHARS.matcher(trimmed).find()) {
            return Result.failure(AppError.validation(AppError.INVALID_CONNECTION_NAME,
                    "Connection name contains invalid characters"));
        }
        return Result.success(new ConnectionName(trimmed));
    }

    public static ConnectionName of(String name) {
        return new ConnectionName(name);
    }
}

