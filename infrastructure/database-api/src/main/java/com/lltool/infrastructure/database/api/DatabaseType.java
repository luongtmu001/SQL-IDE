package com.lltool.infrastructure.database.api;

public enum DatabaseType {
    POSTGRESQL("PostgreSQL", 5432, "org.postgresql.Driver"),
    SQLITE("SQLite", 0, "org.sqlite.JDBC"),
    SQLSERVER("SQL Server", 1433, "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
    MYSQL("MySQL", 3306, "com.mysql.cj.jdbc.Driver");

    private final String displayName;
    private final int defaultPort;
    private final String driverClassName;

    DatabaseType(String displayName, int defaultPort, String driverClassName) {
        this.displayName = displayName;
        this.defaultPort = defaultPort;
        this.driverClassName = driverClassName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public String getDriverClassName() {
        return driverClassName;
    }
}

