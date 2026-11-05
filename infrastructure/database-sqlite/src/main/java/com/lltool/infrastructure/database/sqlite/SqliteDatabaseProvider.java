package com.lltool.infrastructure.database.sqlite;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import com.lltool.infrastructure.database.api.DatabaseType;
import com.lltool.infrastructure.database.api.metadata.*;
import com.lltool.infrastructure.database.jdbc.AbstractJdbcDatabaseProvider;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SqliteDatabaseProvider extends AbstractJdbcDatabaseProvider {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.SQLITE;
    }

    @Override
    public String buildJdbcUrl(DatabaseConnectionConfig config) {
        String dbPath = config.database();
        if (dbPath == null || dbPath.isBlank() || ":memory:".equalsIgnoreCase(dbPath)) {
            return "jdbc:sqlite::memory:";
        }
        return "jdbc:sqlite:" + dbPath.replace('\\', '/');
    }

    @Override
    public Result<List<String>> getCatalogs(DatabaseConnection connection) {
        return Result.success(List.of("main"));
    }

    @Override
    public Result<List<String>> getSchemas(DatabaseConnection connection, String catalog) {
        return Result.success(List.of("main"));
    }

    @Override
    public Result<List<TableMetadata>> getTables(DatabaseConnection connection, String catalog, String schema) {
        try {
            List<TableMetadata> tables = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            try (Statement stmt = raw.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name;")) {
                while (rs.next()) {
                    tables.add(new TableMetadata(rs.getString("name"), "main", "main", "TABLE", 0, null));
                }
            }
            return Result.success(tables);
        } catch (Exception e) {
            return super.getTables(connection, catalog, schema);
        }
    }

    @Override
    public Result<List<TableMetadata>> getViews(DatabaseConnection connection, String catalog, String schema) {
        try {
            List<TableMetadata> views = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            try (Statement stmt = raw.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='view' ORDER BY name;")) {
                while (rs.next()) {
                    views.add(new TableMetadata(rs.getString("name"), "main", "main", "VIEW", 0, null));
                }
            }
            return Result.success(views);
        } catch (Exception e) {
            return super.getViews(connection, catalog, schema);
        }
    }

    @Override
    public Result<List<ColumnMetadata>> getColumns(DatabaseConnection connection, String catalog, String schema, String table) {
        try {
            List<ColumnMetadata> columns = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            try (Statement stmt = raw.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info('" + table + "');")) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String type = rs.getString("type");
                    boolean notnull = rs.getInt("notnull") == 1;
                    String dflt = rs.getString("dflt_value");
                    boolean pk = rs.getInt("pk") > 0;
                    int cid = rs.getInt("cid");
                    columns.add(new ColumnMetadata(name, type, 0, !notnull, pk, false, dflt, cid));
                }
            }
            return Result.success(columns);
        } catch (Exception e) {
            return super.getColumns(connection, catalog, schema, table);
        }
    }

    @Override
    public Result<List<TriggerMetadata>> getTriggers(DatabaseConnection connection, String catalog, String schema, String table) {
        try {
            List<TriggerMetadata> triggers = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            String sql = (table != null && !table.isBlank())
                    ? "SELECT name, tbl_name FROM sqlite_master WHERE type='trigger' AND tbl_name='" + table + "';"
                    : "SELECT name, tbl_name FROM sqlite_master WHERE type='trigger';";
            try (Statement stmt = raw.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    triggers.add(new TriggerMetadata(rs.getString("name"), rs.getString("tbl_name"), "main", "TRIGGER", "AFTER"));
                }
            }
            return Result.success(triggers);
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Override
    public Result<List<KeyMetadata>> getKeys(DatabaseConnection connection, String catalog, String schema, String table) {
        try {
            List<KeyMetadata> keys = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            try (Statement stmt = raw.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info('" + table + "');")) {
                List<String> pkCols = new ArrayList<>();
                while (rs.next()) {
                    if (rs.getInt("pk") > 0) {
                        pkCols.add(rs.getString("name"));
                    }
                }
                if (!pkCols.isEmpty()) {
                    keys.add(new KeyMetadata("PK_" + table, "PRIMARY KEY", table, pkCols));
                }
            }
            try (Statement stmt = raw.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list('" + table + "');")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String fromCol = rs.getString("from");
                    String refTable = rs.getString("table");
                    String toCol = rs.getString("to");
                    keys.add(new KeyMetadata("FK_" + table + "_" + id, "FOREIGN KEY", table, List.of(fromCol), refTable, List.of(toCol)));
                }
            }
            return Result.success(keys);
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Override
    public Result<List<ConstraintMetadata>> getConstraints(DatabaseConnection connection, String catalog, String schema, String table) {
        try {
            List<ConstraintMetadata> constraints = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            try (Statement stmt = raw.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list('" + table + "');")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    constraints.add(new ConstraintMetadata("FK_" + table + "_" + id, "FOREIGN KEY", table));
                }
            }
            return Result.success(constraints);
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }
}

