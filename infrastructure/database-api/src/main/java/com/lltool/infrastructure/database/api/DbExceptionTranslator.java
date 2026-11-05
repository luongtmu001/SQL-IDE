package com.lltool.infrastructure.database.api;

import com.lltool.core.common.error.AppError;

import java.sql.SQLException;
import java.util.Map;

/**
 * Translates low-level SQLExceptions to structured AppErrors.
 */
public final class DbExceptionTranslator {

    private DbExceptionTranslator() {}

    public static AppError translate(String context, SQLException ex) {
        String sqlState = ex.getSQLState() != null ? ex.getSQLState() : "UNKNOWN";
        int errorCode = ex.getErrorCode();

        Map<String, Object> details = Map.of(
                "sqlState", sqlState,
                "vendorErrorCode", errorCode,
                "context", context != null ? context : "Database Operation"
        );

        if (sqlState.startsWith("08")) { // Connection failure
            return AppError.resourceNotFound("DB_CONNECTION_FAILED", "Failed to connect to database: " + ex.getMessage());
        } else if (sqlState.startsWith("28")) { // Authentication failure
            return AppError.business("DB_AUTH_FAILED", "Database authentication failed (invalid credentials)");
        } else if (sqlState.startsWith("42")) { // Syntax error / Access rule violation
            return AppError.validation("DB_SYNTAX_ERROR", "SQL syntax or schema error: " + ex.getMessage(), details);
        }

        return AppError.system("Database operation failed: " + ex.getMessage(), ex);
    }
}

