package app.framework.intellisense;

import app.framework.async.AsyncTaskManager;
import app.framework.connection.ConnectionSessionManager;
import app.framework.connection.ProfileLookup;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import app.framework.metadata.ObjectScriptService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseType;
import com.lltool.infrastructure.database.api.metadata.ColumnMetadata;
import com.lltool.infrastructure.database.api.metadata.KeyMetadata;
import com.lltool.infrastructure.database.api.metadata.RoutineMetadata;
import com.lltool.infrastructure.database.api.metadata.TableMetadata;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Metadata for the JavaScript IntelliSense engine: the Java side of the "/api/metadata/...intellisense..." routes
 * of LLTool. A request comes from the WebView on the FX thread and never blocks it. SQL Server and PostgreSQL are
 * answered from {@link MetadataSnapshotStore} (read once per connection); the rest goes through
 * {@link DesktopMetadataService} (shared with the Explorer, so also its cache). The answer is sent back later.
 *
 * <p>Answer: {@code {"ok":true,"data":...}} or {@code {"ok":false,"error":"..."}}. Row fields are the ones the
 * engine reads (snake_case, as the Python API returned them).
 */
public final class IntellisenseMetadataBridge {

    /** Delivers the JSON answer of a request to the page. */
    @FunctionalInterface
    public interface Responder {
        void respond(String requestId, String json);
    }

    private static final Logger log = LoggerFactory.getLogger(IntellisenseMetadataBridge.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Responder responder;
    /** Measuring only: request id -> method@start. */
    private final Map<String, String> requestStart = new java.util.concurrent.ConcurrentHashMap<>();

    public IntellisenseMetadataBridge(Responder responder) {
        this.responder = responder;
    }

    /** @param argsJson JSON array of the arguments; the first one is the connection (profile) id */
    public void handle(String requestId, String method, String argsJson) {
        JsonNode a;
        try {
            a = JSON.readTree(argsJson);
        } catch (Exception e) {
            responder.respond(requestId, "{\"ok\":false,\"error\":\"Yêu cầu không hợp lệ\"}");
            return;
        }
        if (IntellisensePerf.ENABLED) requestStart.put(requestId, method + "@" + System.currentTimeMillis());
        // Called from the page, on the FX thread: looking up the profile can read files, nothing of it happens here
        Thread.ofVirtual().name("intellisense-request").start(() -> {
            try {
                ConnectionProfileDto profile = profile(text(a, 0));
                if (profile == null) {
                    fail(requestId, "Không tìm thấy kết nối.");
                    return;
                }
                if (!fromSnapshot(requestId, method, a, profile)) {
                    Platform.runLater(() -> {
                        try {
                            dispatch(requestId, method, a, profile);
                        } catch (Exception e) {
                            log.warn("IntelliSense request {} failed: {}", method, e.getMessage());
                            fail(requestId, e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("IntelliSense request {} failed: {}", method, e.getMessage());
                fail(requestId, e.getMessage());
            }
        });
    }

    /**
     * Answers from the memory of this connection. The names never make the list wait: while they load the answer
     * says so, the list shows what it has and is filled in when they arrive. Details are asked for when they are
     * really needed, so that answer simply follows when ready.
     *
     * @return false: not something the snapshot holds, read it the normal way
     */
    private boolean fromSnapshot(String id, String method, JsonNode a, ConnectionProfileDto p) throws Exception {
        if (!MetadataSnapshotStore.supports(p)) return false;
        MetadataSnapshotStore store = MetadataSnapshotStore.getInstance();
        String own = currentDatabase(p, p.databaseName());
        String db = text(a, 1) != null ? text(a, 1) : own;
        switch (method) {
            case "databases", "linkedServers", "foreignServers" -> {
                // server level: these have no database argument, the one of the tab answers for its server
                MetadataSnapshotStore.Names n = store.names(p, own);
                if (n == null) return loading(id, store, p, own);
                answerNames(id, method, n);
                return true;
            }
            case "schemas" -> {
                MetadataSnapshotStore.Names n = store.names(p, db);
                if (n == null) return loading(id, store, p, db);
                answer(id, n.schemas());
                return true;
            }
            case "objects" -> {
                String schema = defaultSchema(p, text(a, 2));
                if (MetadataSnapshotStore.isSystemSchema(schema)) return false;
                MetadataSnapshotStore.Names n = store.names(p, db);
                if (n == null) return loading(id, store, p, db);
                answerRaw(id, n.objectsJson().getOrDefault(lower(schema), "[]"));
                return true;
            }
            case "userTypes" -> {
                MetadataSnapshotStore.Names n = store.names(p, db);
                if (n == null) return loading(id, store, p, db);
                answerRaw(id, n.userTypesJson().getOrDefault(lower(defaultSchema(p, text(a, 2))), "[]"));
                return true;
            }
            case "columns", "parameters", "foreignKeys" -> {
                String schema = defaultSchema(p, text(a, 2));
                if (MetadataSnapshotStore.isSystemSchema(schema)) return false;
                if (method.equals("parameters") && ConnectionSessionManager.mapDatabaseType(p.databaseType()) != DatabaseType.SQLSERVER) return false;
                String key = MetadataSnapshotStore.objectKey(schema, text(a, 3));
                store.details(p, db).whenComplete((d, err) -> {
                    if (d == null) {
                        Platform.runLater(() -> dispatch(id, method, a, p));   // could not be read in one go
                        return;
                    }
                    Map<String, String> rows = method.equals("columns") ? d.columnsJson()
                            : method.equals("parameters") ? d.parametersJson() : d.foreignKeysJson();
                    answerRaw(id, rows.getOrDefault(key, "[]"));
                });
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** @return false when the load failed: then the normal reads are tried, which report the error */
    private boolean loading(String id, MetadataSnapshotStore store, ConnectionProfileDto p, String db) {
        if (store.failed(p, db)) return false;
        deliver(id, "{\"ok\":true,\"loading\":true,\"data\":[]}");
        return true;
    }

    private String currentDatabase(ConnectionProfileDto p, String fallback) {
        String active = activeDatabase.get(p.id());
        return active != null ? active : fallback;
    }

    /** The database of the active tab per connection: its snapshot also holds the server's lists. */
    private final Map<String, String> activeDatabase = new java.util.concurrent.ConcurrentHashMap<>();

    public void setActiveDatabase(String connectionId, String database) {
        if (connectionId != null && database != null) activeDatabase.put(connectionId, database);
    }

    private void answerNames(String id, String method, MetadataSnapshotStore.Names n) {
        switch (method) {
            case "databases" -> answer(id, n.databases());
            case "linkedServers" -> answer(id, n.linkedServers());
            default -> answer(id, n.foreignServers());
        }
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private void dispatch(String id, String method, JsonNode a, ConnectionProfileDto p) {
        DesktopMetadataService meta = DesktopMetadataService.getInstance();
        String db = text(a, 1);
        switch (method) {
            case "databases" -> databases(id, p);
            case "schemas" -> meta.fetchSchemas(p, db, r -> done(id, p, r));
            case "objects" -> objects(id, p, db, defaultSchema(p, text(a, 2)));
            case "columns" -> meta.fetchColumns(p, db, defaultSchema(p, text(a, 2)), text(a, 3),
                    r -> done(id, p, columnRows(r)));
            case "parameters" -> meta.fetchRoutineParameters(p, db, defaultSchema(p, text(a, 2)), text(a, 3),
                    r -> done(id, p, parameterRows(r)));
            case "definition" -> definition(id, p, db, defaultSchema(p, text(a, 2)), text(a, 3), text(a, 4));
            case "userTypes" -> meta.fetchUserDefinedTypes(p, db, defaultSchema(p, text(a, 2)),
                    r -> done(id, p, r.stream().map(t -> t.name()).toList()));
            case "foreignKeys" -> meta.fetchKeys(p, db, defaultSchema(p, text(a, 2)), text(a, 3),
                    r -> done(id, p, foreignKeyRows(r, text(a, 3))));
            case "linkedServers" -> linkedServers(id, p);
            case "remoteDatabases" -> meta.fetchRemoteCatalogs(p, text(a, 1), r -> done(id, p, r));
            case "remoteSchemas" -> meta.fetchRemoteObjects(p, text(a, 1), text(a, 2), r -> {
                Set<String> schemas = new LinkedHashSet<>();
                r.forEach(o -> { if (o.schema() != null) schemas.add(o.schema()); });
                done(id, p, new ArrayList<>(schemas));
            });
            case "remoteObjects" -> meta.fetchRemoteObjects(p, text(a, 1), text(a, 2), r -> {
                String schema = text(a, 3);
                done(id, p, r.stream().filter(o -> schema == null || schema.equalsIgnoreCase(o.schema()))
                        .map(o -> object(o.name(), o.schema(), o.view() ? "view" : "table", null)).toList());
            });
            case "remoteColumns" -> remoteColumns(id, p, text(a, 1), text(a, 2), text(a, 3), text(a, 4));
            case "foreignServers" -> foreignServers(id, p);
            case "foreignTables" -> foreignTables(id, p, text(a, 1));
            default -> fail(id, "Không hỗ trợ: " + method);
        }
    }

    // ------------------------------------------------------------------ objects

    /** Tables, views, routines, sequences and synonyms of one schema, loaded together. */
    private void objects(String id, ConnectionProfileDto p, String db, String schema) {
        DesktopMetadataService meta = DesktopMetadataService.getInstance();
        DatabaseType type = ConnectionSessionManager.mapDatabaseType(p.databaseType());
        List<Map<String, Object>> out = new ArrayList<>();
        // the callbacks all run on the FX thread: a plain counter is enough
        int[] left = {type == DatabaseType.SQLSERVER ? 5 : type == DatabaseType.POSTGRESQL ? 5 : 2};
        Runnable oneDone = () -> {
            if (--left[0] == 0) done(id, p, out);
        };
        Consumer<List<TableMetadata>> tables = r -> {
            r.forEach(t -> out.add(object(t.name(), t.schema() != null ? t.schema() : schema, t.isView() ? "view" : "table", null)));
            oneDone.run();
        };
        meta.fetchTables(p, db, schema, tables);
        meta.fetchViews(p, db, schema, tables);
        if (type == DatabaseType.SQLSERVER || type == DatabaseType.POSTGRESQL) {
            meta.fetchRoutines(p, db, schema, r -> {
                out.addAll(routineRows(r, schema));
                oneDone.run();
            });
            meta.fetchSequences(p, db, schema, r -> {
                r.forEach(s -> out.add(object(s, schema, "sequence", null)));
                oneDone.run();
            });
        }
        if (type == DatabaseType.SQLSERVER) {
            meta.fetchSynonyms(p, db, schema, r -> {
                r.forEach(s -> out.add(object(s.name(), schema, "synonym", s.target())));
                oneDone.run();
            });
        } else if (type == DatabaseType.POSTGRESQL) {
            meta.fetchMaterializedViews(p, db, schema, r -> {
                r.forEach(t -> out.add(object(t.name(), schema, "view", null)));
                oneDone.run();
            });
        }
    }

    /** PostgreSQL lists overloads as "name(args)": the suggestion is the name, once. */
    static List<Map<String, Object>> routineRows(List<RoutineMetadata> routines, String schema) {
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RoutineMetadata r : routines) {
            String name = r.name() == null ? "" : r.name();
            int paren = name.indexOf('(');
            String pure = paren > 0 ? name.substring(0, paren).trim() : name;
            String kind = r.isProcedure() ? "procedure" : "function";
            if (seen.add(kind + "/" + pure.toLowerCase(Locale.ROOT))) {
                rows.add(object(pure, r.schema() != null ? r.schema() : schema, kind, null));
            }
        }
        return rows;
    }

    static Map<String, Object> object(String name, String schema, String type, String target) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("schema", schema);
        m.put("type", type);
        if (target != null) m.put("target_object", target);
        return m;
    }

    static List<Map<String, Object>> columnRows(List<ColumnMetadata> columns) {
        List<Map<String, Object>> rows = new ArrayList<>(columns.size());
        for (ColumnMetadata c : columns) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name());
            m.put("data_type", c.dataType());
            m.put("is_nullable", c.nullable());
            m.put("is_pk", c.primaryKey());
            rows.add(m);
        }
        return rows;
    }

    /** The RETURN "parameter" of a SQL Server function is not something to pass. */
    static List<Map<String, Object>> parameterRows(List<DesktopMetadataService.RoutineParam> params) {
        List<Map<String, Object>> rows = new ArrayList<>(params.size());
        for (DesktopMetadataService.RoutineParam r : params) {
            if ("RETURN".equalsIgnoreCase(r.mode())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.name());
            m.put("data_type", r.type());
            m.put("is_output", r.mode() != null && r.mode().toUpperCase(Locale.ROOT).contains("OUT"));
            m.put("mode", r.mode());
            rows.add(m);
        }
        return rows;
    }

    static List<Map<String, Object>> foreignKeyRows(List<KeyMetadata> keys, String table) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (KeyMetadata k : keys) {
            if (k.referencedTable() == null || k.referencedTable().isBlank()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", k.name());
            m.put("from_table", k.tableName() != null ? k.tableName() : table);
            m.put("from_columns", k.columns());
            m.put("to_table", k.referencedTable());
            m.put("to_columns", k.referencedColumns());
            rows.add(m);
        }
        return rows;
    }

    private void definition(String id, ConnectionProfileDto p, String db, String schema, String name, String type) {
        ObjectScriptService.Kind kind = kindOf(type);
        if (kind == null) {
            answer(id, null);
            return;
        }
        ObjectScriptService.getInstance().scriptAsync(p, db, schema, name, null, kind, ObjectScriptService.Action.CREATE,
                sql -> answer(id, sql), err -> fail(id, err));
    }

    static ObjectScriptService.Kind kindOf(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "table" -> ObjectScriptService.Kind.TABLE;
            case "view" -> ObjectScriptService.Kind.VIEW;
            case "procedure" -> ObjectScriptService.Kind.PROCEDURE;
            case "function" -> ObjectScriptService.Kind.FUNCTION;
            case "trigger" -> ObjectScriptService.Kind.TRIGGER;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ server level

    /**
     * Not {@code fetchCatalogs}: that one also drives the Explorer's connection state (and its error dialog),
     * which suggestions must not touch.
     */
    private void databases(String id, ConnectionProfileDto p) {
        DesktopMetadataService meta = DesktopMetadataService.getInstance();
        if (MockMetadataProvider.isEnabled()) {
            meta.fetchCatalogs(p, r -> answer(id, r));
            return;
        }
        List<String> cached = meta.getCache().get(p.id(), "CATALOGS", "");
        if (cached != null) {
            answer(id, cached);
            return;
        }
        DatabaseType type = ConnectionSessionManager.mapDatabaseType(p.databaseType());
        String sql = type == DatabaseType.SQLSERVER ? "SELECT name FROM sys.databases WHERE HAS_DBACCESS(name) = 1 ORDER BY name"
                : type == DatabaseType.POSTGRESQL ? "SELECT datname FROM pg_catalog.pg_database WHERE datallowconn ORDER BY datname"
                : null;
        if (sql == null) {
            answer(id, List.of());
            return;
        }
        queryStrings(id, p, null, sql);
    }

    private void linkedServers(String id, ConnectionProfileDto p) {
        if (ConnectionSessionManager.mapDatabaseType(p.databaseType()) != DatabaseType.SQLSERVER) {
            answer(id, List.of());
            return;
        }
        DesktopMetadataService.getInstance().fetchLinkedServers(p, r -> done(id, p, r.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("detail", s.dataSource());
            return m;
        }).toList()));
    }

    private void remoteColumns(String id, ConnectionProfileDto p, String server, String db, String schema, String table) {
        if (MockMetadataProvider.isEnabled() || ConnectionSessionManager.mapDatabaseType(p.databaseType()) != DatabaseType.SQLSERVER) {
            answer(id, List.of());
            return;
        }
        run(id, p, null, c -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("EXEC sp_columns_ex @table_server = ?, @table_name = ?, @table_schema = ?, @table_catalog = ?")) {
                ps.setString(1, server);
                ps.setString(2, table);
                ps.setString(3, schema);
                ps.setString(4, db);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", rs.getString("COLUMN_NAME"));
                        m.put("data_type", rs.getString("TYPE_NAME"));
                        m.put("is_nullable", rs.getInt("NULLABLE") != 0);
                        m.put("is_pk", false);
                        rows.add(m);
                    }
                }
            }
            return rows;
        });
    }

    private void foreignServers(String id, ConnectionProfileDto p) {
        if (MockMetadataProvider.isEnabled() || ConnectionSessionManager.mapDatabaseType(p.databaseType()) != DatabaseType.POSTGRESQL) {
            answer(id, List.of());
            return;
        }
        run(id, p, null, c -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT srvname FROM pg_catalog.pg_foreign_server ORDER BY srvname");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", rs.getString(1));
                    rows.add(m);
                }
            }
            return rows;
        });
    }

    private void foreignTables(String id, ConnectionProfileDto p, String schema) {
        if (MockMetadataProvider.isEnabled() || ConnectionSessionManager.mapDatabaseType(p.databaseType()) != DatabaseType.POSTGRESQL) {
            answer(id, List.of());
            return;
        }
        run(id, p, null, c -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            String sql = "SELECT foreign_table_schema, foreign_table_name FROM information_schema.foreign_tables"
                    + (schema != null ? " WHERE foreign_table_schema = ?" : "") + " ORDER BY 1, 2";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (schema != null) ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add(object(rs.getString(2), rs.getString(1), "table", null));
                }
            }
            return rows;
        });
    }

    private void queryStrings(String id, ConnectionProfileDto p, String db, String sql) {
        run(id, p, db, c -> {
            List<String> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(rs.getString(1));
            }
            return rows;
        });
    }

    @FunctionalInterface
    private interface JdbcQuery {
        Object run(java.sql.Connection c) throws Exception;
    }

    /** Read-only query on the Explorer's pooled connection, in the background. */
    private void run(String id, ConnectionProfileDto p, String db, JdbcQuery q) {
        AsyncTaskManager.getInstance().executeAsync(() -> {
            try (DatabaseConnection dc = DesktopMetadataService.getInstance().explorerConnection(p, db)) {
                return q.run(dc.getRawJdbcConnection());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }, r -> answer(id, r), err -> fail(id, err.getMessage()));
    }

    // ------------------------------------------------------------------ helpers

    /** Ctrl+Shift+R in the editor. */
    public void reload(String connectionId, String database) {
        Thread.ofVirtual().name("intellisense-reload").start(() -> {
            ConnectionProfileDto p = profile(connectionId);
            if (p != null) MetadataSnapshotStore.getInstance().reload(p, database, true);
        });
    }

    private static ConnectionProfileDto profile(String id) {
        if (id == null) return null;
        for (ConnectionProfileDto p : ProfileLookup.all()) {
            if (id.equals(p.id())) return p;
        }
        return null;
    }

    static String defaultSchema(ConnectionProfileDto p, String schema) {
        return defaultSchema(ConnectionSessionManager.mapDatabaseType(p.databaseType()), schema);
    }

    static String defaultSchema(DatabaseType type, String schema) {
        if (schema != null && !schema.isBlank()) return schema;
        return type == DatabaseType.SQLSERVER ? "dbo" : type == DatabaseType.POSTGRESQL ? "public" : schema;
    }

    private static String text(JsonNode a, int i) {
        JsonNode n = a.get(i);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        return s.isEmpty() ? null : s;
    }

    /** The service reports a failed read as an empty list and remembers the error. */
    private void done(String id, ConnectionProfileDto p, List<?> rows) {
        String error = rows.isEmpty() ? DesktopMetadataService.getInstance().takeError(p.id()) : null;
        if (error != null) fail(id, error);
        else answer(id, rows);
    }

    /** Large lists are serialized away from the FX thread. */
    private void answer(String id, Object data) {
        Runnable send = () -> {
            try {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ok", true);
                m.put("data", data);
                deliver(id, JSON.writeValueAsString(m));
            } catch (Exception e) {
                fail(id, e.getMessage());
            }
        };
        if (Platform.isFxApplicationThread()) Thread.ofVirtual().name("intellisense-answer").start(send);
        else send.run();
    }

    private void answerRaw(String id, String rowsJson) {
        deliver(id, "{\"ok\":true,\"data\":" + rowsJson + "}");
    }

    private void fail(String id, String message) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", false);
            m.put("error", message != null ? message : "Không tải được metadata");
            deliver(id, JSON.writeValueAsString(m));
        } catch (Exception e) {
            log.warn("IntelliSense answer failed: {}", e.getMessage());
        }
    }

    /** The page lives on the FX thread. */
    private void deliver(String id, String json) {
        if (IntellisensePerf.ENABLED) {
            String started = requestStart.remove(id);
            if (started != null) {
                int at = started.lastIndexOf('@');
                IntellisensePerf.log("answer " + started.substring(0, at) + " after " + (System.currentTimeMillis() - Long.parseLong(started.substring(at + 1)))
                        + " ms, " + json.length() + " chars" + (json.startsWith("{\"ok\":true,\"loading\"") ? ", still loading" : ""));
            }
        }
        if (Platform.isFxApplicationThread()) {
            responder.respond(id, json);
            return;
        }
        try {
            Platform.runLater(() -> responder.respond(id, json));
        } catch (IllegalStateException toolkitNotRunning) {
            responder.respond(id, json);
        }
    }
}
