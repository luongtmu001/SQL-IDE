package com.lltool.infrastructure.database.sqlserver;

import com.lltool.core.common.result.Result;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import com.lltool.infrastructure.database.api.DatabaseType;
import com.lltool.infrastructure.database.api.metadata.RoutineMetadata;
import com.lltool.infrastructure.database.jdbc.AbstractJdbcDatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SqlServerDatabaseProvider extends AbstractJdbcDatabaseProvider {

    private final SqlServerTableDesignReader tableDesignReader = new SqlServerTableDesignReader();

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

    @Override
    public Result<List<RoutineMetadata>> getRoutines(DatabaseConnection connection, String catalog, String schema) {
        try {
            List<RoutineMetadata> routines = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            String sql;
            if (schema != null && !schema.isBlank()) {
                sql = "SELECT o.name, s.name AS schema_name, RTRIM(o.type) AS type, "
                        + "TYPE_NAME(p.user_type_id) AS return_type "
                        + "FROM sys.objects o "
                        + "JOIN sys.schemas s ON s.schema_id = o.schema_id "
                        + "LEFT JOIN sys.parameters p ON p.object_id = o.object_id AND p.parameter_id = 0 "
                        + "WHERE o.schema_id = SCHEMA_ID(?) "
                        + "  AND o.type IN ('P', 'PC', 'FN', 'IF', 'TF', 'FS', 'FT') "
                        + "  AND o.is_ms_shipped = 0 "
                        + "ORDER BY o.name";
            } else {
                sql = "SELECT o.name, s.name AS schema_name, RTRIM(o.type) AS type, "
                        + "TYPE_NAME(p.user_type_id) AS return_type "
                        + "FROM sys.objects o "
                        + "JOIN sys.schemas s ON s.schema_id = o.schema_id "
                        + "LEFT JOIN sys.parameters p ON p.object_id = o.object_id AND p.parameter_id = 0 "
                        + "WHERE o.type IN ('P', 'PC', 'FN', 'IF', 'TF', 'FS', 'FT') "
                        + "  AND o.is_ms_shipped = 0 "
                        + "ORDER BY s.name, o.name";
            }

            try (PreparedStatement ps = raw.prepareStatement(sql)) {
                if (schema != null && !schema.isBlank()) {
                    ps.setString(1, schema);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        String sName = rs.getString(2);
                        String type = rs.getString(3);
                        String returnType = rs.getString(4);
                        boolean isProc = "P".equalsIgnoreCase(type) || "PC".equalsIgnoreCase(type);
                        routines.add(new RoutineMetadata(name, sName, catalog, isProc ? "PROCEDURE" : "FUNCTION", returnType));
                    }
                }
            } catch (Exception ex) {
                return super.getRoutines(connection, catalog, schema);
            }
            return Result.success(routines);
        } catch (Exception e) {
            return super.getRoutines(connection, catalog, schema);
        }
    }

    @Override
    public Result<TableDesign> getTableDesign(DatabaseConnection connection, String catalog, String schema, String table) {
        return tableDesignReader.readTableDesign(connection, catalog, schema, table);
    }
}

