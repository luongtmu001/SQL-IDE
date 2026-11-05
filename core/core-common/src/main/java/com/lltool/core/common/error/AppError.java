package com.lltool.core.common.error;

import java.util.Collections;
import java.util.Map;

/**
 * Standardized application error hierarchy using Java sealed classes.
 * Distinguishes business/validation/resource/configuration errors from unhandled system failures.
 */
public sealed interface AppError permits
        AppError.ValidationError,
        AppError.BusinessError,
        AppError.ResourceError,
        AppError.ConfigurationError,
        AppError.SystemError {

    String code();
    String message();
    Map<String, Object> details();

    // Standard Error Codes
    String PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND";
    String PROJECT_ALREADY_EXISTS = "PROJECT_ALREADY_EXISTS";
    String INVALID_PROJECT_NAME = "INVALID_PROJECT_NAME";
    String INVALID_PROJECT_PATH = "INVALID_PROJECT_PATH";
    String WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND";
    String WORKSPACE_ALREADY_EXISTS = "WORKSPACE_ALREADY_EXISTS";
    String INVALID_WORKSPACE_NAME = "INVALID_WORKSPACE_NAME";
    String CONNECTION_NOT_FOUND = "CONNECTION_NOT_FOUND";
    String CONNECTION_ALREADY_EXISTS = "CONNECTION_ALREADY_EXISTS";
    String INVALID_CONNECTION_NAME = "INVALID_CONNECTION_NAME";
    String CONNECTION_FAILED = "CONNECTION_FAILED";
    String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    String VALIDATION_FAILED = "VALIDATION_FAILED";
    String INTERNAL_ERROR = "INTERNAL_ERROR";

    record ValidationError(String code, String message, Map<String, Object> details) implements AppError {
        public ValidationError {
            details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
        }
    }

    record BusinessError(String code, String message, Map<String, Object> details) implements AppError {
        public BusinessError {
            details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
        }
    }

    record ResourceError(String code, String message, Map<String, Object> details) implements AppError {
        public ResourceError {
            details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
        }
    }

    record ConfigurationError(String code, String message, Map<String, Object> details) implements AppError {
        public ConfigurationError {
            details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
        }
    }

    record SystemError(String code, String message, Map<String, Object> details, Throwable cause) implements AppError {
        public SystemError {
            details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
        }
    }

    // Factory methods
    static ValidationError validation(String code, String message) {
        return new ValidationError(code, message, Collections.emptyMap());
    }

    static ValidationError validation(String code, String message, Map<String, Object> details) {
        return new ValidationError(code, message, details);
    }

    static BusinessError business(String code, String message) {
        return new BusinessError(code, message, Collections.emptyMap());
    }

    static BusinessError business(String code, String message, Map<String, Object> details) {
        return new BusinessError(code, message, details);
    }

    static ResourceError resourceNotFound(String code, String message) {
        return new ResourceError(code, message, Collections.emptyMap());
    }

    static ConfigurationError configuration(String code, String message) {
        return new ConfigurationError(code, message, Collections.emptyMap());
    }

    static SystemError system(String message, Throwable cause) {
        return new SystemError(INTERNAL_ERROR, message, Collections.emptyMap(), cause);
    }
}

