package app.framework.metadata;

import app.framework.connection.ConnectionSessionManager;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseType;
import javafx.application.Platform;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Phase 8 / D2: "Script … as" and "View Definition" read the real object definition from the
 * server (port of LLTool {@code get_object_definition} / {@code script_object}). Before Phase 8 the
 * Explorer opened a placeholder template ("định nghĩa thật sẽ được đọc từ database…").
 *
 * <ul>
 *   <li>SQL Server: {@code sys.sql_modules} (views, procedures, functions, triggers), sequences and
 *       alias types from {@code sys.sequences} / {@code sys.types}.</li>
 *   <li>PostgreSQL: {@code pg_get_viewdef}, {@code pg_get_functiondef} (every overload),
 *       {@code pg_get_triggerdef}, {@code pg_sequences}.</li>
 *   <li>SQLite: {@code sqlite_master.sql}.</li>
 *   <li>Tables: CREATE TABLE built from JDBC metadata (columns, defaults, identity, primary key);
 *       SQLite returns the stored DDL.</li>
 * </ul>
 * Only SELECTs run, on the Explorer's read-only connection.
 */
public final class ObjectScriptService {

    public enum Kind { TABLE, VIEW, MATERIALIZED_VIEW, PROCEDURE, FUNCTION, TRIGGER, SEQUENCE, USER_TYPE, SYNONYM }

    public enum Action { CREATE, ALTER, DROP, DROP_AND_CREATE }

    private static final ObjectScriptService INSTANCE = new ObjectScriptService();
    private static final Pattern PLAIN_PG = Pattern.compile("[a-z_][a-z0-9_$]*");
    private static final Pattern LEADING_CREATE_OR_ALTER = Pattern.compile("(?is)^\\s*CREATE\\s+OR\\s+ALTER\\b");
    private static final Pattern LEADING_ALTER = Pattern.compile("(?is)^\\s*ALTER\\b");
    private static final Pattern LEADING_CREATE = Pattern.compile("(?is)^\\s*CREATE\\b");

    private ObjectScriptService() {
    }

    public static ObjectScriptService getInstance() {
        return INSTANCE;
    }

    /** Builds the script in the background; {@code onDone} / {@code onError} run on the FX thread. */
    public void scriptAsync(ConnectionProfileDto profile, String catalog, String schema, String name, String parentTable,
                            Kind kind, Action action, Consumer<String> onDone, Consumer<String> onError) {
        Thread.ofVirtual().name("object-script").start(() -> {
            try {
                String sql = script(profile, catalog, schema, name, parentTable, kind, action);
                Platform.runLater(() -> onDone.accept(sql));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                Platform.runLater(() -> onError.accept(msg));
            }
        });
    }

    public String script(ConnectionProfileDto profile, String catalog, String schema, String name, String parentTable,
                         Kind kind, Action action) throws Exception {
        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
        if (type == null) throw new IllegalStateException("Loại cơ sở dữ liệu chưa được hỗ trợ.");
        String effSchema = schema != null && !schema.isBlank() ? schema
                : (type == DatabaseType.SQLSERVER ? "dbo" : type == DatabaseType.POSTGRESQL ? "public" : null);
        String cleanName = name != null && name.contains(";") ? name.substring(0, name.indexOf(';')).trim() : name;
        try (DatabaseConnection dc = DesktopMetadataService.getInstance().explorerConnection(profile, catalog)) {
            Connection c = dc.getRawJdbcConnection();
            return switch (action) {
                case CREATE -> create(c, type, catalog, effSchema, cleanName, parentTable, kind);
                case ALTER -> alter(c, type, catalog, effSchema, cleanName, parentTable, kind);
                case DROP -> drop(c, type, effSchema, cleanName, parentTable, kind);
                case DROP_AND_CREATE -> drop(c, type, effSchema, cleanName, parentTable, kind)
                        + (type == DatabaseType.SQLSERVER ? "\nGO\n\n" : "\n\n")
                        + create(c, type, catalog, effSchema, cleanName, parentTable, kind);
            };
        }
    }

    // ------------------------------------------------------------------ actions

    private String create(Connection c, DatabaseType type, String catalog, String schema, String name, String parent, Kind kind)
            throws Exception {
        if (kind == Kind.TABLE && type != DatabaseType.SQLITE) return createTable(c, type, catalog, schema, name);
        String def = definition(c, type, schema, name, kind);
        if (def == null || def.isBlank()) {
            return "-- Không đọc được định nghĩa của " + kind.name().toLowerCase(Locale.ROOT) + " "
                    + qualified(type, schema, name) + " (thiếu quyền VIEW DEFINITION hoặc đối tượng đã bị xóa).\n";
        }
        String sql = def.strip();
        if (type == DatabaseType.SQLSERVER) {
            if (LEADING_CREATE_OR_ALTER.matcher(sql).find()) sql = LEADING_CREATE_OR_ALTER.matcher(sql).replaceFirst("CREATE");
            else if (LEADING_ALTER.matcher(sql).find()) sql = LEADING_ALTER.matcher(sql).replaceFirst("CREATE");
        }
        if (!sql.endsWith(";") && type != DatabaseType.SQLSERVER) sql += ";";
        return sql + "\n";
    }

    private String alter(Connection c, DatabaseType type, String catalog, String schema, String name, String parent, Kind kind)
            throws Exception {
        if (kind == Kind.TABLE || kind == Kind.SEQUENCE || kind == Kind.USER_TYPE || kind == Kind.SYNONYM
                || (type == DatabaseType.POSTGRESQL && kind == Kind.MATERIALIZED_VIEW)
                || type == DatabaseType.SQLITE) {
            return "-- " + kind.name() + " không có ALTER tương đương định nghĩa; dùng DROP And CREATE.\n"
                    + create(c, type, catalog, schema, name, parent, kind);
        }
        String sql = create(c, type, catalog, schema, name, parent, kind);
        if (type == DatabaseType.SQLSERVER) {
            return LEADING_CREATE.matcher(sql).replaceFirst("ALTER");
        }
        // PostgreSQL: views are already CREATE OR REPLACE; functions too (pg_get_functiondef)
        if (kind == Kind.TRIGGER) {
            return "-- PostgreSQL không có ALTER TRIGGER cho thân trigger: DROP rồi CREATE lại.\n"
                    + drop(c, type, schema, name, parent, kind) + "\n" + sql;
        }
        return sql;
    }

    private String drop(Connection c, DatabaseType type, String schema, String name, String parent, Kind kind) throws Exception {
        String q = qualified(type, schema, name);
        boolean pg = type == DatabaseType.POSTGRESQL;
        String cascade = pg ? " CASCADE" : "";
        return switch (kind) {
            case TABLE -> "DROP TABLE IF EXISTS " + q + cascade + ";";
            case VIEW -> "DROP VIEW IF EXISTS " + q + cascade + ";";
            case MATERIALIZED_VIEW -> pg ? "DROP MATERIALIZED VIEW IF EXISTS " + q + " CASCADE;" : "DROP VIEW IF EXISTS " + q + ";";
            case SEQUENCE -> "DROP SEQUENCE IF EXISTS " + q + cascade + ";";
            case USER_TYPE -> "DROP TYPE IF EXISTS " + q + cascade + ";";
            case SYNONYM -> "DROP SYNONYM IF EXISTS " + q + ";";
            case TRIGGER -> {
                if (!pg) yield "DROP TRIGGER IF EXISTS " + q + ";";
                String table = parent != null && !parent.isBlank() ? parent : pgTriggerTable(c, schema, name);
                yield "DROP TRIGGER IF EXISTS " + quote(type, name) + " ON " + qualified(type, schema, table) + ";";
            }
            case PROCEDURE, FUNCTION -> {
                String word = kind == Kind.PROCEDURE ? "PROCEDURE" : "FUNCTION";
                if (!pg) yield "DROP " + word + " IF EXISTS " + q + ";";
                // PostgreSQL routines are identified by their argument types: one DROP per overload
                List<String> drops = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT CASE p.prokind WHEN 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END, "
                                + "pg_catalog.pg_get_function_identity_arguments(p.oid) "
                                + "FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace "
                                + "WHERE n.nspname = ? AND p.proname = ? ORDER BY 2")) {
                    ps.setString(1, schema);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) drops.add("DROP " + rs.getString(1) + " IF EXISTS " + q + "(" + rs.getString(2) + ");");
                    }
                }
                yield drops.isEmpty() ? "DROP " + word + " IF EXISTS " + q + "();" : String.join("\n", drops);
            }
        };
    }

    // ------------------------------------------------------------------ definitions

    private String definition(Connection c, DatabaseType type, String schema, String name, Kind kind) throws Exception {
        return switch (type) {
            case SQLSERVER -> sqlServerDefinition(c, schema, name, kind);
            case POSTGRESQL -> postgresDefinition(c, schema, name, kind);
            default -> first(c, "SELECT sql FROM sqlite_master WHERE name = ?", name);
        };
    }

    private String sqlServerDefinition(Connection c, String schema, String name, Kind kind) throws Exception {
        if (kind == Kind.SYNONYM) { // same text as LLTool: CREATE SYNONYM [s].[n] FOR base_object_name;
            return first(c, """
                    SELECT 'CREATE SYNONYM ' + QUOTENAME(s.name) + '.' + QUOTENAME(sy.name) + CHAR(13) + CHAR(10)
                         + 'FOR ' + sy.base_object_name + ';'
                    FROM sys.synonyms sy JOIN sys.schemas s ON s.schema_id = sy.schema_id
                    WHERE s.name = ? AND sy.name = ?""", schema, name);
        }
        if (kind == Kind.SEQUENCE) {
            return first(c, """
                    SELECT 'CREATE SEQUENCE ' + QUOTENAME(s.name) + '.' + QUOTENAME(sq.name) + CHAR(13) + CHAR(10)
                         + '    AS ' + TYPE_NAME(sq.user_type_id) + CHAR(13) + CHAR(10)
                         + '    START WITH ' + CAST(sq.start_value AS VARCHAR(40)) + CHAR(13) + CHAR(10)
                         + '    INCREMENT BY ' + CAST(sq.increment AS VARCHAR(40)) + CHAR(13) + CHAR(10)
                         + '    MINVALUE ' + CAST(sq.minimum_value AS VARCHAR(40)) + CHAR(13) + CHAR(10)
                         + '    MAXVALUE ' + CAST(sq.maximum_value AS VARCHAR(40)) + CHAR(13) + CHAR(10)
                         + CASE WHEN sq.is_cycling = 1 THEN '    CYCLE' ELSE '    NO CYCLE' END + ';'
                    FROM sys.sequences sq JOIN sys.schemas s ON s.schema_id = sq.schema_id
                    WHERE s.name = ? AND sq.name = ?""", schema, name);
        }
        if (kind == Kind.USER_TYPE) {
            return first(c, """
                    SELECT 'CREATE TYPE ' + QUOTENAME(s.name) + '.' + QUOTENAME(t.name) + ' FROM ' + TYPE_NAME(t.system_type_id)
                         + CASE WHEN t.system_type_id IN (167, 175, 231, 239)
                                THEN '(' + CASE WHEN t.max_length = -1 THEN 'MAX'
                                                ELSE CAST(CASE WHEN t.system_type_id IN (231, 239) THEN t.max_length / 2 ELSE t.max_length END AS VARCHAR(10)) END + ')'
                                WHEN t.system_type_id IN (106, 108)
                                THEN '(' + CAST(t.precision AS VARCHAR(10)) + ',' + CAST(t.scale AS VARCHAR(10)) + ')'
                                ELSE '' END
                         + CASE WHEN t.is_nullable = 1 THEN ' NULL;' ELSE ' NOT NULL;' END
                    FROM sys.types t JOIN sys.schemas s ON s.schema_id = t.schema_id
                    WHERE t.is_user_defined = 1 AND s.name = ? AND t.name = ?""", schema, name);
        }
        String def = first(c, """
                SELECT sm.definition FROM sys.sql_modules sm
                JOIN sys.objects o ON o.object_id = sm.object_id
                JOIN sys.schemas s ON s.schema_id = o.schema_id
                WHERE s.name = ? AND o.name = ?""", schema, name);
        if (def == null) {   // triggers belong to their table's schema; fall back on the name alone
            def = first(c, "SELECT TOP (1) sm.definition FROM sys.sql_modules sm JOIN sys.objects o ON o.object_id = sm.object_id WHERE o.name = ?", name);
        }
        return def;
    }

    private String postgresDefinition(Connection c, String schema, String name, Kind kind) throws Exception {
        return switch (kind) {
            case VIEW, MATERIALIZED_VIEW -> first(c, """
                    SELECT CASE WHEN c.relkind = 'm' THEN 'CREATE MATERIALIZED VIEW ' ELSE 'CREATE OR REPLACE VIEW ' END
                           || quote_ident(n.nspname) || '.' || quote_ident(c.relname) || ' AS' || E'\\n'
                           || pg_catalog.pg_get_viewdef(c.oid, true)
                    FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = ? AND c.relname = ? AND c.relkind IN ('v', 'm')""", schema, name);
            case PROCEDURE, FUNCTION -> {
                List<String> defs = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT pg_catalog.pg_get_functiondef(p.oid)
                        FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
                        WHERE n.nspname = ? AND p.proname = ? AND p.prokind IN ('f', 'p', 'w')
                        ORDER BY pg_catalog.pg_get_function_identity_arguments(p.oid)""")) {
                    ps.setString(1, schema);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) defs.add(rs.getString(1).strip() + ";");
                    }
                }
                yield defs.isEmpty() ? null : String.join("\n\n", defs);
            }
            case TRIGGER -> first(c, """
                    SELECT pg_catalog.pg_get_triggerdef(t.oid, true) || ';'
                    FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = ? AND t.tgname = ? AND NOT t.tgisinternal""", schema, name);
            case SEQUENCE -> first(c, """
                    SELECT 'CREATE SEQUENCE ' || quote_ident(schemaname) || '.' || quote_ident(sequencename)
                           || E'\\n    AS ' || data_type::text || E'\\n    START WITH ' || start_value
                           || E'\\n    INCREMENT BY ' || increment_by || E'\\n    MINVALUE ' || min_value
                           || E'\\n    MAXVALUE ' || max_value || CASE WHEN cycle THEN E'\\n    CYCLE' ELSE E'\\n    NO CYCLE' END
                           || E'\\n    CACHE ' || cache_size || ';'
                    FROM pg_catalog.pg_sequences WHERE schemaname = ? AND sequencename = ?""", schema, name);
            default -> null;
        };
    }

    private static String pgTriggerTable(Connection c, String schema, String trigger) throws Exception {
        String t = first(c, """
                SELECT c.relname FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND t.tgname = ? AND NOT t.tgisinternal""", schema, trigger);
        return t != null ? t : "<table>";
    }

    /** CREATE TABLE from JDBC metadata (columns, defaults, identity / serial, primary key). */
    private String createTable(Connection c, DatabaseType type, String catalog, String schema, String table) throws Exception {
        DatabaseMetaData md = c.getMetaData();
        String cat = type == DatabaseType.SQLSERVER ? (catalog != null && !catalog.isBlank() ? catalog : c.getCatalog()) : null;
        List<String> lines = new ArrayList<>();
        try (ResultSet rs = md.getColumns(cat, schema, table, null)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                String typeName = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                int digits = rs.getInt("DECIMAL_DIGITS");
                boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                String def = rs.getString("COLUMN_DEF");
                boolean auto = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
                StringBuilder sb = new StringBuilder("    ").append(quote(type, col)).append(' ')
                        .append(formatType(type, typeName, size, digits, auto));
                if (def != null && !def.isBlank() && !(type == DatabaseType.POSTGRESQL && auto)) sb.append(" DEFAULT ").append(def.strip());
                sb.append(nullable ? " NULL" : " NOT NULL");
                lines.add(sb.toString());
            }
        }
        if (lines.isEmpty()) {
            return "-- Không đọc được cột của bảng " + qualified(type, schema, table) + ".\n";
        }
        Map<Short, String> pk = new java.util.TreeMap<>();
        String pkName = null;
        try (ResultSet rs = md.getPrimaryKeys(cat, schema, table)) {
            while (rs.next()) {
                pk.put(rs.getShort("KEY_SEQ"), quote(type, rs.getString("COLUMN_NAME")));
                pkName = rs.getString("PK_NAME");
            }
        }
        if (!pk.isEmpty()) {
            lines.add("    CONSTRAINT " + quote(type, pkName != null ? pkName : "PK_" + table)
                    + " PRIMARY KEY (" + String.join(", ", pk.values()) + ")");
        }
        return "CREATE TABLE " + qualified(type, schema, table) + " (\n" + String.join(",\n", lines) + "\n);\n";
    }

    private static String formatType(DatabaseType type, String typeName, int size, int digits, boolean auto) {
        String t = typeName != null ? typeName : "varchar";
        String lower = t.toLowerCase(Locale.ROOT);
        if (type == DatabaseType.SQLSERVER) {
            String base = lower.replace(" identity", "");
            boolean identity = auto || lower.endsWith(" identity");
            String sized = switch (base) {
                case "varchar", "nvarchar", "varbinary", "char", "nchar", "binary" ->
                        base + "(" + (size <= 0 || size > 8000 ? "MAX" : String.valueOf(size)) + ")";
                case "decimal", "numeric" -> base + "(" + size + ", " + digits + ")";
                case "datetime2", "time", "datetimeoffset" -> base + "(" + digits + ")";
                default -> base;
            };
            return sized + (identity ? " IDENTITY(1,1)" : "");
        }
        if (type == DatabaseType.POSTGRESQL) {
            if (auto && (lower.equals("int4") || lower.equals("serial"))) return "serial";
            if (auto && (lower.equals("int8") || lower.equals("bigserial"))) return "bigserial";
            return switch (lower) {
                case "varchar", "bpchar" -> (lower.equals("bpchar") ? "char" : "varchar") + (size > 0 && size < Integer.MAX_VALUE ? "(" + size + ")" : "");
                case "numeric" -> size > 0 && size < 1000 ? "numeric(" + size + ", " + digits + ")" : "numeric";
                default -> t;
            };
        }
        return t;
    }

    // ------------------------------------------------------------------ helpers

    private static String first(Connection c, String sql, String... params) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Dialect quoting (LLTool quote_ident): [x] for SQL Server, "x" when needed for PostgreSQL / SQLite. */
    public static String quote(DatabaseType type, String ident) {
        if (ident == null) return "";
        if (type == DatabaseType.SQLSERVER) return "[" + ident.replace("]", "]]") + "]";
        if (type == DatabaseType.POSTGRESQL && PLAIN_PG.matcher(ident).matches()) return ident;
        if (type == DatabaseType.SQLITE && ident.matches("[A-Za-z_][A-Za-z0-9_]*")) return ident;
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    public static String qualified(DatabaseType type, String schema, String name) {
        if (schema == null || schema.isBlank() || type == DatabaseType.SQLITE || "main".equalsIgnoreCase(schema)) {
            return quote(type, name);
        }
        return quote(type, schema) + "." + quote(type, name);
    }

    /** Labels of the "Script … as" entries, in menu order. */
    public static Map<String, Action> scriptActions(Kind kind) {
        Map<String, Action> m = new LinkedHashMap<>();
        m.put("CREATE To", Action.CREATE);
        if (kind != Kind.TABLE && kind != Kind.SYNONYM) m.put("ALTER To", Action.ALTER);
        m.put("DROP To", Action.DROP);
        m.put("DROP And CREATE To", Action.DROP_AND_CREATE);
        return m;
    }
}
