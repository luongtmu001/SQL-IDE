package app.framework.intellisense;

import app.framework.connection.ConnectionSessionManager;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * Everything IntelliSense needs from a server, read once per connection + database and then served from memory:
 * no TTL, the server is only asked again after DDL run from the app, a manual refresh, or a reconnect.
 *
 * <p>A load runs in the background on one connection, in two steps: names first (databases, schemas, objects: what
 * the list needs), then the details (columns, parameters, foreign keys). A reload keeps answering from the old data
 * and swaps when the new data is complete. Rows are kept as ready-made JSON: compact, and nothing has to be
 * serialized when asked.
 *
 * <p>SQLite (a local file) and mock mode are not cached here. Objects of the system schemas (sys, pg_catalog ...)
 * are not in the bulk queries; {@link #isSystemSchema} sends those to the normal per-object reads.
 */
public final class MetadataSnapshotStore {

    /** Names of one database, and those of its server. */
    public record Names(List<String> databases, List<Map<String, Object>> linkedServers, List<Map<String, Object>> foreignServers,
                        List<String> schemas, Map<String, String> objectsJson, Map<String, String> userTypesJson) {
    }

    /** Columns / parameters / foreign keys as JSON by "schema|object". */
    public record Details(Map<String, String> columnsJson, Map<String, String> parametersJson, Map<String, String> foreignKeysJson) {
    }

    private static final Logger log = LoggerFactory.getLogger(MetadataSnapshotStore.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MetadataSnapshotStore INSTANCE = new MetadataSnapshotStore();
    private static final int NAMES_TIMEOUT_S = 30;
    private static final int DETAILS_TIMEOUT_S = 120;

    private static final Pattern OBJECT_DDL = Pattern.compile(
            "\\b(?:CREATE|ALTER|DROP)\\s+(?:OR\\s+(?:ALTER|REPLACE)\\s+)?(?:MATERIALIZED\\s+|FOREIGN\\s+|UNLOGGED\\s+)?"
                    + "(?:TABLE|VIEW|PROC|PROCEDURE|FUNCTION|TRIGGER|TYPE|SYNONYM|SEQUENCE|SCHEMA|DATABASE)\\b"
                    + "(?!\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?[#@])"
                    + "|\\bsp_rename\\b",
            Pattern.CASE_INSENSITIVE);

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, String>> listeners = new CopyOnWriteArrayList<>();

    private MetadataSnapshotStore() {
        // Explorer refresh and disconnect: this data goes with it
        DesktopMetadataService.getInstance().addInvalidationListener(this::drop);
    }

    public static MetadataSnapshotStore getInstance() {
        return INSTANCE;
    }

    /** Called (on a background thread) with connection id + database when new data can be served. */
    public void addListener(BiConsumer<String, String> listener) {
        listeners.add(listener);
    }

    public static boolean supports(ConnectionProfileDto p) {
        if (MockMetadataProvider.isEnabled()) return false;
        DatabaseType t = ConnectionSessionManager.mapDatabaseType(p.databaseType());
        return t == DatabaseType.SQLSERVER || t == DatabaseType.POSTGRESQL;
    }

    public static boolean isSystemSchema(String schema) {
        if (schema == null) return false;
        String s = schema.toLowerCase(Locale.ROOT);
        return s.equals("sys") || s.equals("information_schema") || s.equals("pg_catalog");
    }

    /** True when {@code sql} creates, changes or removes an object the suggestions list (temp objects don't count). */
    public static boolean changesObjects(String sql) {
        if (sql == null) return false;
        String noComments = sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\n]*", " ");
        return OBJECT_DDL.matcher(noComments).find();
    }

    public static String objectKey(String schema, String name) {
        return (schema == null ? "" : schema.toLowerCase(Locale.ROOT)) + "|" + (name == null ? "" : name.toLowerCase(Locale.ROOT));
    }

    private static String entryKey(ConnectionProfileDto p, String db) {
        return p.id() + "|" + (db == null ? "" : db.toLowerCase(Locale.ROOT));
    }

    private static final class Entry {
        final ConnectionProfileDto profile;
        final String db;
        volatile Names names;
        volatile boolean failed;
        volatile CompletableFuture<Details> details = new CompletableFuture<>();
        volatile boolean loading;
        volatile boolean dropped;

        Entry(ConnectionProfileDto profile, String db) {
            this.profile = profile;
            this.db = db;
        }
    }

    private Entry entry(ConnectionProfileDto p, String db) {
        return entries.computeIfAbsent(entryKey(p, db), k -> {
            Entry e = new Entry(p, db);
            start(e, false);
            return e;
        });
    }

    /** Starts the load if there is none yet. Never blocks. */
    public void preload(ConnectionProfileDto p, String db) {
        if (supports(p)) entry(p, db);
    }

    /** The names, or null while they are loading (the load is started if needed). */
    public Names names(ConnectionProfileDto p, String db) {
        return entry(p, db).names;
    }

    public boolean failed(ConnectionProfileDto p, String db) {
        return entry(p, db).failed;
    }

    /** Completes with the details, or with null when they could not be read. */
    public CompletableFuture<Details> details(ConnectionProfileDto p, String db) {
        return entry(p, db).details;
    }

    /** Reads everything of the connection again; suggestions keep working from the old data meanwhile. */
    public void reload(ConnectionProfileDto p, String db, boolean announce) {
        if (!supports(p)) return;
        DesktopMetadataService.getInstance().getCache().invalidateConnection(p.id());
        String prefix = p.id() + "|";
        boolean current = false;
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            current |= e.getKey().equals(entryKey(p, db));
            start(e.getValue(), announce);
        }
        if (!current) {
            entries.computeIfAbsent(entryKey(p, db), k -> {
                Entry e = new Entry(p, db);
                start(e, announce);
                return e;
            });
        }
    }

    /** Disconnected or refreshed from the Explorer. */
    public void drop(String connectionId) {
        if (connectionId == null) return;
        String prefix = connectionId + "|";
        entries.entrySet().removeIf(e -> {
            if (!e.getKey().startsWith(prefix)) return false;
            e.getValue().dropped = true;
            e.getValue().details.complete(null);
            return true;
        });
    }

    // ------------------------------------------------------------------ loading

    private void start(Entry e, boolean announce) {
        synchronized (e) {
            if (e.loading) return;
            e.loading = true;
        }
        Thread.ofVirtual().name("intellisense-load-" + e.db).start(() -> load(e, announce));
    }

    private void load(Entry e, boolean announce) {
        long t0 = System.currentTimeMillis();
        boolean first = e.names == null;
        DatabaseType type = ConnectionSessionManager.mapDatabaseType(e.profile.databaseType());
        try (DatabaseConnection dc = DesktopMetadataService.getInstance().explorerConnection(e.profile, e.db)) {
            Connection c = dc.getRawJdbcConnection();
            IntellisensePerf.log("metadata connection ready after " + (System.currentTimeMillis() - t0) + " ms");
            Names names = type == DatabaseType.SQLSERVER ? sqlServerNames(c) : postgresNames(c);
            long t1 = System.currentTimeMillis();
            IntellisensePerf.log("metadata names read: total " + (t1 - t0) + " ms, " + names.objectsJson().size() + " schemas with objects");
            if (first) {
                // the list works from here on; the details follow on the same connection
                e.names = names;
                e.failed = false;
                fire(e);
            }
            Details details = type == DatabaseType.SQLSERVER ? sqlServerDetails(c) : postgresDetails(c);
            log.info("IntelliSense metadata of {}/{}: names {} ms, details {} ms ({} objects' columns)",
                    e.profile.name(), e.db, t1 - t0, System.currentTimeMillis() - t1, details.columnsJson().size());
            IntellisensePerf.log("metadata details read: " + (System.currentTimeMillis() - t1) + " ms, columns of " + details.columnsJson().size() + " objects");
            e.names = names;
            e.failed = false;
            if (!e.details.complete(details)) {
                e.details = CompletableFuture.completedFuture(details);
            }
            if (!first) fire(e);
            if (announce) {
                app.services.NotificationService.getInstance().showInfo("IntelliSense",
                        "Đã làm mới gợi ý của " + e.profile.name() + (e.db != null ? " · " + e.db : "") + ".");
            }
        } catch (Exception ex) {
            log.warn("IntelliSense metadata of {}/{} not read in one go: {}", e.profile.name(), e.db, ex.getMessage());
            if (first) {
                e.failed = true;
                fire(e);
            }
            e.details.complete(null);
            if (announce) {
                app.services.NotificationService.getInstance().showWarning("IntelliSense", "Không làm mới được gợi ý: " + ex.getMessage());
            }
        } finally {
            synchronized (e) {
                e.loading = false;
            }
        }
    }

    private void fire(Entry e) {
        if (e.dropped) return;
        for (BiConsumer<String, String> l : listeners) {
            try {
                l.accept(e.profile.id(), e.db);
            } catch (RuntimeException ex) {
                log.warn("IntelliSense listener failed: {}", ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ SQL Server

    private static Names sqlServerNames(Connection c) throws Exception {
        Map<String, List<Map<String, Object>>> objects = new LinkedHashMap<>();
        List<Map<String, Object>> allSynonyms = new ArrayList<>();
        query(c, NAMES_TIMEOUT_S, "SELECT s.name, o.name, o.type, sy.base_object_name FROM sys.objects o "
                + "JOIN sys.schemas s ON s.schema_id = o.schema_id LEFT JOIN sys.synonyms sy ON sy.object_id = o.object_id "
                + "WHERE (o.is_ms_shipped = 0 OR s.name NOT IN ('sys', 'information_schema')) "
                + "AND o.type IN ('U','V','P','PC','FN','IF','TF','FS','FT','SO','SN','TR','TA','X','AF') "
                + "ORDER BY s.name, o.name", rs -> {
            // columns in order: the SQL Server driver reads the row as a stream
            String schema = rs.getString(1), name = rs.getString(2), type = rs.getString(3), target = rs.getString(4);
            Map<String, Object> obj = IntellisenseMetadataBridge.object(name, schema, sqlServerKind(type), target);
            objects.computeIfAbsent(lower(schema), k -> new ArrayList<>()).add(obj);
            if ("SN".equalsIgnoreCase(type != null ? type.trim() : "")) {
                allSynonyms.add(obj);
            }
        });
        // Database-level triggers
        optional(c, "SELECT tr.name FROM sys.triggers tr WHERE tr.parent_id = 0 ORDER BY tr.name", rs -> {
            objects.computeIfAbsent("dbo", k -> new ArrayList<>())
                    .add(IntellisenseMetadataBridge.object(rs.getString(1), "dbo", "trigger", null));
        });
        // Global temporary tables in tempdb
        optional(c, "SELECT t.name FROM tempdb.sys.tables t WHERE t.name LIKE '##%' ORDER BY t.name", rs -> {
            objects.computeIfAbsent("dbo", k -> new ArrayList<>())
                    .add(IntellisenseMetadataBridge.object(rs.getString(1), "dbo", "table", null));
        });
        // Ensure synonyms from other schemas are also discoverable in dbo (parity with Python OR o.type = 'SN')
        if (!allSynonyms.isEmpty()) {
            List<Map<String, Object>> dboList = objects.computeIfAbsent("dbo", k -> new ArrayList<>());
            Set<String> existingNames = new HashSet<>();
            for (Map<String, Object> item : dboList) {
                existingNames.add(lower((String) item.get("name")));
            }
            for (Map<String, Object> syn : allSynonyms) {
                if (existingNames.add(lower((String) syn.get("name")))) {
                    dboList.add(syn);
                }
            }
        }
        List<String> schemas = strings(c, "SELECT name FROM sys.schemas WHERE schema_id < 16384 ORDER BY name");
        Map<String, List<String>> types = new LinkedHashMap<>();
        optional(c, "SELECT s.name, t.name FROM sys.types t JOIN sys.schemas s ON s.schema_id = t.schema_id "
                + "WHERE t.is_user_defined = 1 ORDER BY t.name", rs ->
                types.computeIfAbsent(lower(rs.getString(1)), k -> new ArrayList<>()).add(rs.getString(2)));
        List<Map<String, Object>> linked = new ArrayList<>();
        optional(c, "SELECT name, data_source FROM sys.servers WHERE is_linked = 1 ORDER BY name", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", rs.getString(1));
            m.put("detail", rs.getString(2));
            linked.add(m);
        });
        List<String> databases = new ArrayList<>();
        optional(c, "SELECT name FROM sys.databases WHERE HAS_DBACCESS(name) = 1 ORDER BY name", rs -> databases.add(rs.getString(1)));
        return new Names(databases, linked, List.of(), schemas, toJson(objects), toJson(types));
    }

    static String sqlServerKind(String type) {
        return switch (type == null ? "" : type.trim()) {
            case "U" -> "table";
            case "V" -> "view";
            case "P", "PC", "X" -> "procedure";
            case "SO" -> "sequence";
            case "SN" -> "synonym";
            case "TR", "TA" -> "trigger";
            case "AF" -> "function";
            default -> "function";
        };
    }

    private static Details sqlServerDetails(Connection c) throws Exception {
        Map<String, List<Map<String, Object>>> columns = new LinkedHashMap<>();
        query(c, DETAILS_TIMEOUT_S, "SELECT s.name, o.name, col.name, t.name, col.is_nullable, "
                + "CASE WHEN pk.column_id IS NULL THEN 0 ELSE 1 END FROM sys.columns col "
                + "JOIN sys.objects o ON o.object_id = col.object_id JOIN sys.schemas s ON s.schema_id = o.schema_id "
                + "JOIN sys.types t ON t.user_type_id = col.user_type_id "
                + "LEFT JOIN (SELECT ic.object_id, ic.column_id FROM sys.index_columns ic "
                + "  JOIN sys.indexes i ON i.object_id = ic.object_id AND i.index_id = ic.index_id WHERE i.is_primary_key = 1) pk "
                + "  ON pk.object_id = col.object_id AND pk.column_id = col.column_id "
                + "WHERE (o.is_ms_shipped = 0 OR s.name NOT IN ('sys', 'information_schema')) "
                + "AND o.type IN ('U','V','IF','TF','FT') ORDER BY s.name, o.name, col.column_id", rs ->
                columns.computeIfAbsent(objectKey(rs.getString(1), rs.getString(2)), k -> new ArrayList<>())
                        .add(columnRow(rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6))));

        Map<String, List<Map<String, Object>>> params = new LinkedHashMap<>();
        optional(c, "SELECT s.name, o.name, p.name, TYPE_NAME(p.user_type_id), p.is_output FROM sys.parameters p "
                + "JOIN sys.objects o ON o.object_id = p.object_id JOIN sys.schemas s ON s.schema_id = o.schema_id "
                + "WHERE (o.is_ms_shipped = 0 OR s.name NOT IN ('sys', 'information_schema')) "
                + "AND p.parameter_id > 0 ORDER BY s.name, o.name, p.parameter_id", rs -> {
            String key = objectKey(rs.getString(1), rs.getString(2));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", rs.getString(3));
            m.put("data_type", rs.getString(4));
            boolean output = rs.getBoolean(5);
            m.put("is_output", output);
            m.put("mode", output ? "OUT" : "IN");
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        });

        Map<String, ForeignKey> keys = new LinkedHashMap<>();
        optional(c, "SELECT fk.name, s.name, tp.name, cp.name, tr.name, cr.name FROM sys.foreign_keys fk "
                + "JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id "
                + "JOIN sys.tables tp ON tp.object_id = fk.parent_object_id JOIN sys.schemas s ON s.schema_id = tp.schema_id "
                + "JOIN sys.columns cp ON cp.object_id = fkc.parent_object_id AND cp.column_id = fkc.parent_column_id "
                + "JOIN sys.tables tr ON tr.object_id = fk.referenced_object_id "
                + "JOIN sys.columns cr ON cr.object_id = fkc.referenced_object_id AND cr.column_id = fkc.referenced_column_id "
                + "ORDER BY fk.object_id, fkc.constraint_column_id", rs -> addKeyColumn(keys, rs));
        return new Details(toJson(columns), toJson(params), foreignKeysJson(keys));
    }

    // ------------------------------------------------------------------ PostgreSQL

    private static final String PG_USER_SCHEMAS = "n.nspname NOT IN ('pg_catalog','information_schema') "
            + "AND n.nspname NOT LIKE 'pg_toast%' AND n.nspname NOT LIKE 'pg_temp%'";

    private static Names postgresNames(Connection c) throws Exception {
        Map<String, List<Map<String, Object>>> objects = new LinkedHashMap<>();
        query(c, NAMES_TIMEOUT_S, "SELECT n.nspname, cl.relname, cl.relkind FROM pg_catalog.pg_class cl "
                + "JOIN pg_catalog.pg_namespace n ON n.oid = cl.relnamespace "
                + "WHERE cl.relkind IN ('r','p','f','v','m','S') AND " + PG_USER_SCHEMAS + " ORDER BY 1, 2", rs -> {
            String schema = rs.getString(1), name = rs.getString(2);
            String kind = switch (rs.getString(3)) {
                case "v", "m" -> "view";
                case "S" -> "sequence";
                case "f" -> "foreign_table";
                default -> "table";
            };
            objects.computeIfAbsent(lower(schema), k -> new ArrayList<>())
                    .add(IntellisenseMetadataBridge.object(name, schema, kind, null));
        });
        // overloads share a name; the suggestion is the name, once
        Set<String> seen = new LinkedHashSet<>();
        optional(c, "SELECT n.nspname, p.proname, p.prokind FROM pg_catalog.pg_proc p "
                + "JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace "
                + "WHERE p.prokind IN ('f','p') AND " + PG_USER_SCHEMAS + " ORDER BY 1, 2", rs -> {
            String schema = rs.getString(1), name = rs.getString(2);
            String kind = "p".equals(rs.getString(3)) ? "procedure" : "function";
            if (seen.add(objectKey(schema, name) + "/" + kind)) {
                objects.computeIfAbsent(lower(schema), k -> new ArrayList<>())
                        .add(IntellisenseMetadataBridge.object(name, schema, kind, null));
            }
        });
        optional(c, "SELECT n.nspname, tr.tgname FROM pg_catalog.pg_trigger tr "
                + "JOIN pg_catalog.pg_class cl ON cl.oid = tr.tgrelid "
                + "JOIN pg_catalog.pg_namespace n ON n.oid = cl.relnamespace "
                + "WHERE NOT tr.tgisinternal AND " + PG_USER_SCHEMAS + " ORDER BY 1, 2", rs -> {
            String schema = rs.getString(1), name = rs.getString(2);
            objects.computeIfAbsent(lower(schema), k -> new ArrayList<>())
                    .add(IntellisenseMetadataBridge.object(name, schema, "trigger", null));
        });
        List<String> schemas = strings(c, "SELECT n.nspname FROM pg_catalog.pg_namespace n "
                + "WHERE n.nspname NOT LIKE 'pg_toast%' AND n.nspname NOT LIKE 'pg_temp%' ORDER BY 1");
        Map<String, List<String>> types = new LinkedHashMap<>();
        optional(c, "SELECT n.nspname, t.typname FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace "
                + "LEFT JOIN pg_catalog.pg_class cl ON cl.oid = t.typrelid "
                + "WHERE (t.typtype IN ('e','d') OR (t.typtype = 'c' AND cl.relkind = 'c')) AND " + PG_USER_SCHEMAS + " ORDER BY 2", rs ->
                types.computeIfAbsent(lower(rs.getString(1)), k -> new ArrayList<>()).add(rs.getString(2)));
        List<Map<String, Object>> foreign = new ArrayList<>();
        optional(c, "SELECT srvname FROM pg_catalog.pg_foreign_server ORDER BY srvname", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", rs.getString(1));
            foreign.add(m);
        });
        List<String> databases = new ArrayList<>();
        optional(c, "SELECT datname FROM pg_catalog.pg_database WHERE datallowconn ORDER BY datname", rs -> databases.add(rs.getString(1)));
        return new Names(databases, List.of(), foreign, schemas, toJson(objects), toJson(types));
    }

    /** Parameters are not read in bulk here: a routine asks for its own the first time (overloads need their signature). */
    private static Details postgresDetails(Connection c) throws Exception {
        Map<String, List<Map<String, Object>>> columns = new LinkedHashMap<>();
        query(c, DETAILS_TIMEOUT_S, "SELECT n.nspname, cl.relname, a.attname, pg_catalog.format_type(a.atttypid, a.atttypmod), "
                + "NOT a.attnotnull, i.indrelid IS NOT NULL FROM pg_catalog.pg_attribute a "
                + "JOIN pg_catalog.pg_class cl ON cl.oid = a.attrelid JOIN pg_catalog.pg_namespace n ON n.oid = cl.relnamespace "
                + "LEFT JOIN pg_catalog.pg_index i ON i.indrelid = cl.oid AND i.indisprimary AND a.attnum = ANY(i.indkey) "
                + "WHERE a.attnum > 0 AND NOT a.attisdropped AND cl.relkind IN ('r','p','f','v','m') AND " + PG_USER_SCHEMAS
                + " ORDER BY 1, 2, a.attnum", rs ->
                columns.computeIfAbsent(objectKey(rs.getString(1), rs.getString(2)), k -> new ArrayList<>())
                        .add(columnRow(rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6))));

        Map<String, ForeignKey> keys = new LinkedHashMap<>();
        optional(c, "SELECT con.conname, n.nspname, t.relname, a.attname, rt.relname, ra.attname FROM pg_catalog.pg_constraint con "
                + "JOIN pg_catalog.pg_class t ON t.oid = con.conrelid JOIN pg_catalog.pg_namespace n ON n.oid = t.relnamespace "
                + "JOIN pg_catalog.pg_class rt ON rt.oid = con.confrelid "
                + "CROSS JOIN LATERAL unnest(con.conkey, con.confkey) WITH ORDINALITY AS k(attnum, refnum, ord) "
                + "JOIN pg_catalog.pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum "
                + "JOIN pg_catalog.pg_attribute ra ON ra.attrelid = con.confrelid AND ra.attnum = k.refnum "
                + "WHERE con.contype = 'f' ORDER BY con.oid, k.ord", rs -> addKeyColumn(keys, rs));
        return new Details(toJson(columns), Map.of(), foreignKeysJson(keys));
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> columnRow(String name, String type, boolean nullable, boolean pk) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("data_type", type);
        m.put("is_nullable", nullable);
        m.put("is_pk", pk);
        return m;
    }

    private record ForeignKey(String schema, String table, String name, String toTable, List<String> from, List<String> to) {
    }

    /** Row: constraint, schema, table, column, referenced table, referenced column. */
    private static void addKeyColumn(Map<String, ForeignKey> keys, ResultSet rs) throws SQLException {
        String name = rs.getString(1), schema = rs.getString(2), table = rs.getString(3);
        String column = rs.getString(4), toTable = rs.getString(5), toColumn = rs.getString(6);
        ForeignKey fk = keys.computeIfAbsent(objectKey(schema, table) + "|" + name,
                k -> new ForeignKey(schema, table, name, toTable, new ArrayList<>(), new ArrayList<>()));
        fk.from().add(column);
        fk.to().add(toColumn);
    }

    private static Map<String, String> foreignKeysJson(Map<String, ForeignKey> keys) throws Exception {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        for (ForeignKey k : keys.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", k.name());
            m.put("from_table", k.table());
            m.put("from_columns", k.from());
            m.put("to_table", k.toTable());
            m.put("to_columns", k.to());
            rows.computeIfAbsent(objectKey(k.schema(), k.table()), x -> new ArrayList<>()).add(m);
        }
        return toJson(rows);
    }

    private static <T> Map<String, String> toJson(Map<String, List<T>> rows) throws Exception {
        Map<String, String> out = new ConcurrentHashMap<>(Math.max(16, rows.size() * 2));
        for (Map.Entry<String, List<T>> e : rows.entrySet()) {
            out.put(e.getKey(), JSON.writeValueAsString(e.getValue()));
        }
        return out;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface RowReader {
        void read(ResultSet rs) throws SQLException;
    }

    private static void query(Connection c, int timeoutSeconds, String sql, RowReader reader) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(timeoutSeconds);
            ps.setFetchSize(2000);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) reader.read(rs);
            }
        }
    }

    /** Parts a login may not be allowed to read, or an older server doesn't have: the rest still works. */
    private static void optional(Connection c, String sql, RowReader reader) {
        try {
            query(c, NAMES_TIMEOUT_S, sql, reader);
        } catch (SQLException e) {
            log.debug("IntelliSense metadata query skipped: {}", e.getMessage());
            try {
                if (!c.getAutoCommit()) c.rollback();   // PostgreSQL: an error aborts the open transaction
            } catch (SQLException ignored) {
                // the next query reports it
            }
        }
    }

    private static List<String> strings(Connection c, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        query(c, NAMES_TIMEOUT_S, sql, rs -> out.add(rs.getString(1)));
        return out;
    }
}
