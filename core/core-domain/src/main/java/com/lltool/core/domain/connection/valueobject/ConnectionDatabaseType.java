package com.lltool.core.domain.connection.valueobject;

public enum ConnectionDatabaseType {
    POSTGRESQL("PostgreSQL", 5432),
    SQLITE("SQLite", 0),
    SQLSERVER("SQL Server", 1433);

    private final String displayName;
    private final int defaultPort;

    ConnectionDatabaseType(String displayName, int defaultPort) {
        this.displayName = displayName;
        this.defaultPort = defaultPort;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public static ConnectionDatabaseType fromString(String raw) {
        if (raw == null) return POSTGRESQL;
        String s = raw.trim().toUpperCase().replace(" ", "").replace("-", "").replace("_", "");
        if (s.contains("SQLITE")) return SQLITE;
        if (s.contains("SQLSERVER") || s.contains("MSSQL")) return SQLSERVER;
        return POSTGRESQL;
    }
}

