package com.lltool.infrastructure.database.postgresql;

import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import com.lltool.infrastructure.database.api.DatabaseType;
import com.lltool.infrastructure.database.jdbc.AbstractJdbcDatabaseProvider;

public class PostgreSqlDatabaseProvider extends AbstractJdbcDatabaseProvider {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public String buildJdbcUrl(DatabaseConnectionConfig config) {
        String host = config.host() != null && !config.host().isBlank() ? config.host() : "localhost";
        int port = config.port() > 0 ? config.port() : DatabaseType.POSTGRESQL.getDefaultPort();
        String db = config.database() != null ? config.database() : "";
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, db);
    }
}

