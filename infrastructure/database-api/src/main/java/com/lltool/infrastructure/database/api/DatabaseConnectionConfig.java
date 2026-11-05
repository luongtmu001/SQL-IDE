package com.lltool.infrastructure.database.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Portable configuration model for database connections (Phase 3.6).
 * Password is stored as a reference or token, avoiding plain text.
 */
public record DatabaseConnectionConfig(
        String id,
        String name,
        DatabaseType type,
        String host,
        int port,
        String database,
        String username,
        String passwordRef,
        Map<String, String> properties
) implements Serializable {

    public DatabaseConnectionConfig {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        properties = properties != null ? Collections.unmodifiableMap(properties) : Collections.emptyMap();
        if (port <= 0 && type.getDefaultPort() > 0) {
            port = type.getDefaultPort();
        }
    }

    public static DatabaseConnectionConfig sqlite(String id, String name, String dbFilePath) {
        return new DatabaseConnectionConfig(id, name, DatabaseType.SQLITE, "localhost", 0, dbFilePath, "", "", Collections.emptyMap());
    }

    public static DatabaseConnectionConfig postgres(String id, String name, String host, int port, String database, String user, String passwordRef) {
        return new DatabaseConnectionConfig(id, name, DatabaseType.POSTGRESQL, host, port, database, user, passwordRef, Collections.emptyMap());
    }

    public static DatabaseConnectionConfig sqlServer(String id, String name, String host, int port, String database, String user, String passwordRef) {
        return new DatabaseConnectionConfig(id, name, DatabaseType.SQLSERVER, host, port, database, user, passwordRef, Collections.emptyMap());
    }
}

