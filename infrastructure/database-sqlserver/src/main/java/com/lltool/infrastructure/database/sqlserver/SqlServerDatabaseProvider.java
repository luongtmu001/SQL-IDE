package com.lltool.infrastructure.database.sqlserver;

import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import com.lltool.infrastructure.database.api.DatabaseType;
import com.lltool.infrastructure.database.jdbc.AbstractJdbcDatabaseProvider;

public class SqlServerDatabaseProvider extends AbstractJdbcDatabaseProvider {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.SQLSERVER;
    }

    @Override
    public String buildJdbcUrl(DatabaseConnectionConfig config) {
        String host = config.host() != null && !config.host().isBlank() ? config.host() : "localhost";
        int port = config.port() > 0 ? config.port() : DatabaseType.SQLSERVER.getDefaultPort();
        String db = config.database() != null && !config.database().isBlank() ? ";databaseName=" + config.database() : "";
        return String.format("jdbc:sqlserver://%s:%d%s;encrypt=true;trustServerCertificate=true", host, port, db);
    }
}

