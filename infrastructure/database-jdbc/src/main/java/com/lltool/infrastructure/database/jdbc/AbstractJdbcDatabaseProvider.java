package com.lltool.infrastructure.database.jdbc;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import com.lltool.infrastructure.database.api.DatabaseProvider;
import com.lltool.infrastructure.database.api.DbExceptionTranslator;
import com.lltool.infrastructure.database.api.metadata.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Base JDBC Database Provider implementation with Metadata support (Phase 3.6 & Phase 6.3).
 */
public abstract class AbstractJdbcDatabaseProvider implements DatabaseProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractJdbcDatabaseProvider.class);

    @Override
    public Result<DatabaseConnection> connect(DatabaseConnectionConfig config, String resolvedPassword) {
        String url = buildJdbcUrl(config);
        Properties props = new Properties();
        if (config.username() != null && !config.username().isBlank()) {
            props.put("user", config.username());
        }
        if (resolvedPassword != null && !resolvedPassword.isBlank()) {
            props.put("password", resolvedPassword);
        }
        if (config.properties() != null) {
            props.putAll(config.properties());
        }

        try {
            Connection conn = DriverManager.getConnection(url, props);
            log.info("Successfully connected to {} database ({})", config.type().getDisplayName(), config.name());
            return Result.success(new JdbcDatabaseConnection(config, conn));
        } catch (SQLException e) {
            log.error("Failed to connect to {}: {}", url, e.getMessage());
            return Result.failure(DbExceptionTranslator.translate("Connect to " + config.name(), e));
        }
    }

    @Override
    public Result<Boolean> testConnection(DatabaseConnectionConfig config, String resolvedPassword) {
        Result<DatabaseConnection> connRes = connect(config, resolvedPassword);
        if (connRes.isFailure()) {
            return Result.failure(connRes.getError().get());
        }
        try (DatabaseConnection conn = connRes.getDataOrThrow()) {
            boolean valid = conn.isValid(5);
            return Result.success(valid);
        } catch (Exception e) {
            return Result.failure(AppError.system("Connection validation error", e));
        }
    }

    @Override
    public Result<List<String>> getCatalogs(DatabaseConnection connection) {
        try {
            List<String> list = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            DatabaseMetaData meta = raw.getMetaData();
            try (ResultSet rs = meta.getCatalogs()) {
                while (rs.next()) {
                    String cat = rs.getString("TABLE_CAT");
                    if (cat != null && !cat.isBlank()) {
                        list.add(cat);
                    }
                }
            }
            if (list.isEmpty() && connection.getConfig().database() != null && !connection.getConfig().database().isBlank()) {
                list.add(connection.getConfig().database());
            }
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to fetch catalogs: {}", e.getMessage());
            return Result.failure(AppError.system("Failed to fetch catalogs", e));
        }
    }

    @Override
    public Result<List<String>> getSchemas(DatabaseConnection connection, String catalog) {
        try {
            List<String> list = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            DatabaseMetaData meta = raw.getMetaData();
            try (ResultSet rs = meta.getSchemas(catalog, "%")) {
                while (rs.next()) {
                    String s = rs.getString("TABLE_SCHEM");
                    if (s != null && !s.isBlank()) {
                        list.add(s);
                    }
                }
            } catch (Exception ignored) {
                try (ResultSet rs = meta.getSchemas()) {
                    while (rs.next()) {
                        String s = rs.getString("TABLE_SCHEM");
                        if (s != null && !s.isBlank()) {
                            list.add(s);
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                list.add("public");
            }
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to fetch schemas: {}", e.getMessage());
            return Result.failure(AppError.system("Failed to fetch schemas", e));
        }
    }

    @Override
    public Result<List<TableMetadata>> getTables(DatabaseConnection connection, String catalog, String schema) {
        try {
            List<TableMetadata> tables = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            DatabaseMetaData meta = raw.getMetaData();
            try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "BASE TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String comment = rs.getString("REMARKS");
                    tables.add(new TableMetadata(name, schema, catalog, "TABLE", 0, comment));
                }
            }
            return Result.success(tables);
        } catch (Exception e) {
            log.error("Failed to fetch tables: {}", e.getMessage());
            return Result.failure(AppError.system("Failed to fetch tables", e));
        }
    }

    @Override
    public Result<List<TableMetadata>> getViews(DatabaseConnection connection, String catalog, String schema) {
        try {
            List<TableMetadata> views = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            DatabaseMetaData meta = raw.getMetaData();
            try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"VIEW"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String comment = rs.getString("REMARKS");
                    views.add(new TableMetadata(name, schema, catalog, "VIEW", 0, comment));
                }
            }
            return Result.success(views);
        } catch (Exception e) {
            log.error("Failed to fetch views: {}", e.getMessage());
            return Result.failure(AppError.system("Failed to fetch views", e));
        }
    }

    @Override
    public Result<List<ColumnMetadata>> getColumns(DatabaseConnection connection, String catalog, String schema, String table) {
        try {
            List<ColumnMetadata> columns = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            DatabaseMetaData meta = raw.getMetaData();

            Set<String> pkColumns = new HashSet<>();
            try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col != null) pkColumns.add(col);
                }
            } catch (Exception ignored) {}

            Set<String> fkColumns = new HashSet<>();
            try (ResultSet rs = meta.getImportedKeys(catalog, schema, table)) {
                while (rs.next()) {
                    String col = rs.getString("FKCOLUMN_NAME");
                    if (col != null) fkColumns.add(col);
                }
            } catch (Exception ignored) {}

            try (ResultSet rs = meta.getColumns(catalog, schema, table, "%")) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    int size = rs.getInt("COLUMN_SIZE");
                    boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                    String defaultVal = rs.getString("COLUMN_DEF");
                    int ord = rs.getInt("ORDINAL_POSITION");
                    columns.add(new ColumnMetadata(
                            colName, typeName, size, nullable,
                            pkColumns.contains(colName),
                            fkColumns.contains(colName),
                            defaultVal, ord
                    ));
                }
            }
            return Result.success(columns);
        } catch (Exception e) {
            log.error("Failed to fetch columns: {}", e.getMessage());
            return Result.failure(AppError.system("Failed to fetch columns", e));
        }
    }

    @Override
    public Result<List<RoutineMetadata>> getRoutines(DatabaseConnection connection, String catalog, String schema) {
        try {
            Map<String, RoutineMetadata> routines = new LinkedHashMap<>();
            Connection raw = connection.getRawJdbcConnection();
            DatabaseMetaData meta = raw.getMetaData();

            try (ResultSet rs = meta.getProcedures(catalog, schema, "%")) {
                while (rs.next()) {
                    String name = rs.getString("PROCEDURE_NAME");
                    if (name != null) {
                        int semi = name.indexOf(';');
                        if (semi >= 0) {
                            name = name.substring(0, semi);
                        }
                        routines.putIfAbsent(name + "|PROCEDURE", new RoutineMetadata(name, schema, catalog, "PROCEDURE", null));
                    }
                }
            } catch (Exception ignored) {}

            try (ResultSet rs = meta.getFunctions(catalog, schema, "%")) {
                while (rs.next()) {
                    String name = rs.getString("FUNCTION_NAME");
                    if (name != null) {
                        int semi = name.indexOf(';');
                        if (semi >= 0) {
                            name = name.substring(0, semi);
                        }
                        routines.putIfAbsent(name + "|FUNCTION", new RoutineMetadata(name, schema, catalog, "FUNCTION", null));
                    }
                }
            } catch (Exception ignored) {}

            return Result.success(new ArrayList<>(routines.values()));
        } catch (Exception e) {
            log.error("Failed to fetch routines: {}", e.getMessage());
            return Result.failure(AppError.system("Failed to fetch routines", e));
        }
    }

    @Override
    public Result<List<IndexMetadata>> getIndexes(DatabaseConnection connection, String catalog, String schema, String table) {
        try {
            List<IndexMetadata> indexes = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            DatabaseMetaData meta = raw.getMetaData();
            Map<String, List<String>> indexCols = new LinkedHashMap<>();
            Map<String, Boolean> indexUnique = new LinkedHashMap<>();

            try (ResultSet rs = meta.getIndexInfo(catalog, schema, table, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null) continue;
                    boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                    String col = rs.getString("COLUMN_NAME");
                    indexUnique.putIfAbsent(indexName, !nonUnique);
                    if (col != null) {
                        indexCols.computeIfAbsent(indexName, k -> new ArrayList<>()).add(col);
                    }
                }
            } catch (Exception ignored) {}

            for (var entry : indexCols.entrySet()) {
                indexes.add(new IndexMetadata(entry.getKey(), table, schema, indexUnique.getOrDefault(entry.getKey(), false), entry.getValue()));
            }
            return Result.success(indexes);
        } catch (Exception e) {
            log.error("Failed to fetch indexes: {}", e.getMessage());
            return Result.failure(AppError.system("Failed to fetch indexes", e));
        }
    }

    @Override
    public Result<List<TriggerMetadata>> getTriggers(DatabaseConnection connection, String catalog, String schema, String table) {
        try {
            List<TriggerMetadata> triggers = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            String dbType = getDatabaseType() != null ? getDatabaseType().name() : "";

            if (dbType.contains("POSTGRE")) {
                String targetSchema = (schema != null && !schema.isBlank()) ? schema.trim() : "public";
                boolean hasTable = table != null && !table.isBlank();
                String sql = """
                    SELECT t.tgname AS trigger_name, c.relname AS table_name
                    FROM pg_catalog.pg_trigger t
                    JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE (n.nspname = ? OR lower(n.nspname) = lower(?))
                      AND NOT t.tgisinternal
                """;
                if (hasTable) {
                    sql += " AND (c.relname = ? OR lower(c.relname) = lower(?))";
                }
                sql += " ORDER BY t.tgname";

                try (java.sql.PreparedStatement ps = raw.prepareStatement(sql)) {
                    ps.setString(1, targetSchema);
                    ps.setString(2, targetSchema);
                    if (hasTable) {
                        ps.setString(3, table);
                        ps.setString(4, table);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            triggers.add(new TriggerMetadata(rs.getString("trigger_name"), rs.getString("table_name"), "TRIGGER", "TRIGGER", null));
                        }
                    }
                }
                return Result.success(triggers);
            } else if (dbType.contains("SQLSERVER")) {
                String targetSchema = (schema != null && !schema.isBlank()) ? schema.trim() : "dbo";
                boolean hasTable = table != null && !table.isBlank();
                String sql = """
                    SELECT tr.name AS trigger_name, OBJECT_NAME(tr.parent_id) AS table_name
                    FROM sys.triggers tr
                    JOIN sys.objects o ON o.object_id = tr.parent_id
                    JOIN sys.schemas s ON s.schema_id = o.schema_id
                    WHERE s.name = ?
                """;
                if (hasTable) {
                    sql += " AND o.name = ?";
                }
                sql += " ORDER BY tr.name";

                try (java.sql.PreparedStatement ps = raw.prepareStatement(sql)) {
                    ps.setString(1, targetSchema);
                    if (hasTable) {
                        ps.setString(2, table);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            triggers.add(new TriggerMetadata(rs.getString("trigger_name"), rs.getString("table_name"), "TRIGGER", "TRIGGER", null));
                        }
                    }
                }
                return Result.success(triggers);
            }

            return Result.success(Collections.emptyList());
        } catch (Exception e) {
            log.warn("Failed to fetch triggers: {}", e.getMessage());
            return Result.success(Collections.emptyList());
        }
    }

    @Override
    public Result<List<TableMetadata>> getMaterializedViews(DatabaseConnection connection, String catalog, String schema) {
        try {
            List<TableMetadata> mViews = new ArrayList<>();
            String dbType = getDatabaseType() != null ? getDatabaseType().name() : "";
            if (dbType.contains("POSTGRE")) {
                Connection raw = connection.getRawJdbcConnection();
                String targetSchema = (schema != null && !schema.isBlank()) ? schema.trim() : "public";
                String sql = """
                    SELECT c.relname, n.nspname
                    FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE (n.nspname = ? OR lower(n.nspname) = lower(?))
                      AND c.relkind = 'm'
                    ORDER BY c.relname
                """;
                try (java.sql.PreparedStatement ps = raw.prepareStatement(sql)) {
                    ps.setString(1, targetSchema);
                    ps.setString(2, targetSchema);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            mViews.add(new TableMetadata(rs.getString("relname"), rs.getString("nspname"), catalog, "MATERIALIZED VIEW", 0L, null));
                        }
                    }
                }
            }
            return Result.success(mViews);
        } catch (Exception e) {
            log.warn("Failed to fetch materialized views: {}", e.getMessage());
            return Result.success(Collections.emptyList());
        }
    }

    @Override
    public Result<List<UserTypeMetadata>> getUserDefinedTypes(DatabaseConnection connection, String catalog, String schema) {
        try {
            List<UserTypeMetadata> udtList = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            String dbType = getDatabaseType() != null ? getDatabaseType().name() : "";

            if (dbType.contains("POSTGRE")) {
                String targetSchema = (schema != null && !schema.isBlank()) ? schema.trim() : "public";
                String sql = """
                    SELECT t.typname, pg_catalog.format_type(t.typbasetype, t.typtypmod) AS base_type, n.nspname AS schema_name
                    FROM pg_catalog.pg_type t
                    JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
                    WHERE (n.nspname = ? OR lower(n.nspname) = lower(?))
                      AND t.typtype IN ('c', 'd', 'e')
                      AND NOT EXISTS (
                          SELECT 1 FROM pg_catalog.pg_class c WHERE c.reltype = t.oid AND c.relkind IN ('r', 'v', 'm', 'S')
                      )
                    ORDER BY t.typname
                """;
                try (java.sql.PreparedStatement ps = raw.prepareStatement(sql)) {
                    ps.setString(1, targetSchema);
                    ps.setString(2, targetSchema);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            udtList.add(new UserTypeMetadata(rs.getString("typname"), rs.getString("schema_name"), catalog, rs.getString("base_type")));
                        }
                    }
                }
            } else if (dbType.contains("SQLSERVER")) {
                String targetSchema = (schema != null && !schema.isBlank()) ? schema.trim() : "dbo";
                String sql = """
                    SELECT t.name, TYPE_NAME(t.system_type_id) AS base_type, s.name AS schema_name
                    FROM sys.types t
                    JOIN sys.schemas s ON s.schema_id = t.schema_id
                    WHERE s.name = ? AND t.is_user_defined = 1
                    ORDER BY t.name
                """;
                try (java.sql.PreparedStatement ps = raw.prepareStatement(sql)) {
                    ps.setString(1, targetSchema);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            udtList.add(new UserTypeMetadata(rs.getString("name"), rs.getString("schema_name"), catalog, rs.getString("base_type")));
                        }
                    }
                }
            }

            return Result.success(udtList);
        } catch (Exception e) {
            log.warn("Failed to fetch user defined types: {}", e.getMessage());
            return Result.success(Collections.emptyList());
        }
    }

    @Override
    public Result<List<KeyMetadata>> getKeys(DatabaseConnection connection, String catalog, String schema, String table) {
        try {
            List<KeyMetadata> keys = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            String dbType = getDatabaseType() != null ? getDatabaseType().name() : "";

            if (dbType.contains("POSTGRE")) {
                String targetSchema = (schema != null && !schema.isBlank()) ? schema.trim() : "public";
                String sql = """
                    SELECT con.conname AS key_name,
                           CASE con.contype
                               WHEN 'p' THEN 'PRIMARY KEY'
                               WHEN 'f' THEN 'FOREIGN KEY'
                               WHEN 'u' THEN 'UNIQUE'
                               ELSE con.contype::text
                           END AS key_type
                    FROM pg_catalog.pg_constraint con
                    JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE (n.nspname = ? OR lower(n.nspname) = lower(?))
                      AND (c.relname = ? OR lower(c.relname) = lower(?))
                      AND con.contype IN ('p', 'f', 'u')
                    ORDER BY con.conname
                """;
                try (java.sql.PreparedStatement ps = raw.prepareStatement(sql)) {
                    ps.setString(1, targetSchema);
                    ps.setString(2, targetSchema);
                    ps.setString(3, table);
                    ps.setString(4, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            keys.add(new KeyMetadata(rs.getString("key_name"), rs.getString("key_type"), table));
                        }
                    }
                }
                return Result.success(keys);
            } else if (dbType.contains("SQLSERVER")) {
                String targetSchema = (schema != null && !schema.isBlank()) ? schema.trim() : "dbo";
                String sql = """
                    SELECT i.name AS key_name, 'PRIMARY KEY' AS key_type
                    FROM sys.key_constraints i
                    JOIN sys.tables t ON t.object_id = i.parent_object_id
                    JOIN sys.schemas s ON s.schema_id = t.schema_id
                    WHERE s.name = ? AND t.name = ?
                    UNION ALL
                    SELECT f.name AS key_name, 'FOREIGN KEY' AS key_type
                    FROM sys.foreign_keys f
                    JOIN sys.tables t ON t.object_id = f.parent_object_id
                    JOIN sys.schemas s ON s.schema_id = t.schema_id
                    WHERE s.name = ? AND t.name = ?
                """;
                try (java.sql.PreparedStatement ps = raw.prepareStatement(sql)) {
                    ps.setString(1, targetSchema);
                    ps.setString(2, table);
                    ps.setString(3, targetSchema);
                    ps.setString(4, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            keys.add(new KeyMetadata(rs.getString("key_name"), rs.getString("key_type"), table));
                        }
                    }
                }
                return Result.success(keys);
            }

            // Generic JDBC metadata fallback
            DatabaseMetaData meta = raw.getMetaData();
            try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
                while (rs.next()) {
                    String pkName = rs.getString("PK_NAME");
                    if (pkName == null) pkName = "PK_" + table;
                    keys.add(new KeyMetadata(pkName, "PRIMARY KEY", table, List.of(rs.getString("COLUMN_NAME"))));
                }
            } catch (Exception ignored) {}

            try (ResultSet rs = meta.getImportedKeys(catalog, schema, table)) {
                while (rs.next()) {
                    String fkName = rs.getString("FK_NAME");
                    if (fkName == null) fkName = "FK_" + table + "_" + rs.getString("FKCOLUMN_NAME");
                    keys.add(new KeyMetadata(fkName, "FOREIGN KEY", table, List.of(rs.getString("FKCOLUMN_NAME")), rs.getString("PKTABLE_NAME"), List.of(rs.getString("PKCOLUMN_NAME"))));
                }
            } catch (Exception ignored) {}

            return Result.success(keys);
        } catch (Exception e) {
            log.warn("Failed to fetch keys: {}", e.getMessage());
            return Result.success(Collections.emptyList());
        }
    }

    @Override
    public Result<List<ConstraintMetadata>> getConstraints(DatabaseConnection connection, String catalog, String schema, String table) {
        try {
            List<ConstraintMetadata> constraints = new ArrayList<>();
            Connection raw = connection.getRawJdbcConnection();
            String dbType = getDatabaseType() != null ? getDatabaseType().name() : "";

            if (dbType.contains("POSTGRE")) {
                String targetSchema = (schema != null && !schema.isBlank()) ? schema.trim() : "public";
                String sql = """
                    SELECT con.conname AS constraint_name, 'CHECK' AS constraint_type
                    FROM pg_catalog.pg_constraint con
                    JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE (n.nspname = ? OR lower(n.nspname) = lower(?))
                      AND (c.relname = ? OR lower(c.relname) = lower(?))
                      AND con.contype = 'c'
                    ORDER BY con.conname
                """;
                try (java.sql.PreparedStatement ps = raw.prepareStatement(sql)) {
                    ps.setString(1, targetSchema);
                    ps.setString(2, targetSchema);
                    ps.setString(3, table);
                    ps.setString(4, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            constraints.add(new ConstraintMetadata(rs.getString("constraint_name"), rs.getString("constraint_type"), table));
                        }
                    }
                }
                return Result.success(constraints);
            } else if (dbType.contains("SQLSERVER")) {
                String targetSchema = (schema != null && !schema.isBlank()) ? schema.trim() : "dbo";
                String sql = """
                    SELECT c.name AS constraint_name, 'CHECK' AS constraint_type
                    FROM sys.check_constraints c
                    JOIN sys.tables t ON t.object_id = c.parent_object_id
                    JOIN sys.schemas s ON s.schema_id = t.schema_id
                    WHERE s.name = ? AND t.name = ?
                    UNION ALL
                    SELECT d.name AS constraint_name, 'DEFAULT' AS constraint_type
                    FROM sys.default_constraints d
                    JOIN sys.tables t ON t.object_id = d.parent_object_id
                    JOIN sys.schemas s ON s.schema_id = t.schema_id
                    WHERE s.name = ? AND t.name = ?
                """;
                try (java.sql.PreparedStatement ps = raw.prepareStatement(sql)) {
                    ps.setString(1, targetSchema);
                    ps.setString(2, table);
                    ps.setString(3, targetSchema);
                    ps.setString(4, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            constraints.add(new ConstraintMetadata(rs.getString("constraint_name"), rs.getString("constraint_type"), table));
                        }
                    }
                }
                return Result.success(constraints);
            }

            return Result.success(Collections.emptyList());
        } catch (Exception e) {
            log.warn("Failed to fetch constraints: {}", e.getMessage());
            return Result.success(Collections.emptyList());
        }
    }
}

