package app.framework.syntax;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Standard interface for database dialect syntax parsers.
 */
public interface SyntaxAdapter {

    /**
     * Validates SQL syntax and returns standardized diagnostic records.
     *
     * @param sql        full SQL query or batch
     * @param connection dedicated read-only connection
     * @param database   target database name
     * @param schema     target default schema
     * @param isDebug    whether debug mode is enabled
     * @return list of normalized syntax diagnostics
     * @throws SQLException on connection loss (SQLState 08xxx)
     */
    List<SyntaxDiagnostic> validate(String sql, Connection connection, String database, String schema, boolean isDebug) throws SQLException;

    /** Dialect identifier (e.g. "sqlserver", "postgresql"). */
    String dialect();
}

