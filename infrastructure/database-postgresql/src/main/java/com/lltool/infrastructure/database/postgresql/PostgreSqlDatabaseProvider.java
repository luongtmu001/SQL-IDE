package com.lltool.infrastructure.database.postgresql;

import com.lltool.core.common.result.Result;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import com.lltool.infrastructure.database.api.DatabaseType;
import com.lltool.infrastructure.database.jdbc.AbstractJdbcDatabaseProvider;

public class PostgreSqlDatabaseProvider extends AbstractJdbcDatabaseProvider {

    private final PostgresTableDesignReader tableDesignReader = new PostgresTableDesignReader();

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

    @Override
    public Result<TableDesign> getTableDesign(DatabaseConnection connection, String catalog, String schema, String table) {
        return tableDesignReader.readTableDesign(connection, catalog, schema, table);
    }
}

