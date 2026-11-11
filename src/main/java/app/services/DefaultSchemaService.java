package app.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Default schema of a connection (the "Schema" field of the connection dialog, LLTool {@code profile.schema}).
 * Kept in the user preferences, keyed by connection id. A one-time import copies the schemas of the LLTool
 * {@code connections.json} (read only) so imported connections keep theirs.
 */
public final class DefaultSchemaService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSchemaService.class);
    /** Same node the connection dialog has always written to. */
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(app.controllers.ConnectionManagerController.class).node("default_schema");
    private static final String IMPORT_FLAG = "__lltool_import_v2";
    private static final java.util.Map<String, String> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** "connection|catalog" pairs whose default schema was already applied as a filter in this session. */
    private static final Set<String> APPLIED = new HashSet<>();

    private DefaultSchemaService() { }

    private static String key(String id) {
        String k = id == null ? "" : id.replace('/', '_');
        return k.length() > Preferences.MAX_KEY_LENGTH ? k.substring(0, Preferences.MAX_KEY_LENGTH) : k;
    }

    /** The saved default schema, or null when none (blank counts as none). Cached in memory to avoid repetitive registry reads. */
    public static String get(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) return null;
        String cached = CACHE.computeIfAbsent(connectionId, id -> {
            try {
                String v = PREFS.get(key(id), null);
                if (v != null && !v.isBlank()) return v.trim();
                var p = app.framework.connection.ProfileLookup.byId(id);
                if (p == null) p = app.framework.connection.ProfileLookup.byName(id);
                if (p != null && p.properties() != null) {
                    String fromProp = p.properties().get("defaultSchema");
                    if (fromProp == null || fromProp.isBlank()) fromProp = p.properties().get("schema");
                    if (fromProp != null && !fromProp.isBlank()) return fromProp.trim();
                }
                String direct = readLltoolSchema(id);
                if (direct != null && !direct.isBlank()) {
                    set(id, direct);
                    return direct.trim();
                }
                if (p != null && p.databaseType() != null) {
                    String dt = p.databaseType().toUpperCase(java.util.Locale.ROOT);
                    if (dt.contains("SQLSERVER")) return "dbo";
                    if (dt.contains("POSTGRE")) return "public";
                }
                return "";
            } catch (Exception e) {
                return "";
            }
        });
        return cached.isEmpty() ? null : cached;
    }

    private static String readLltoolSchema(String identifier) {
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null) return null;
            Path file = Path.of(appData, "LLTool", "connections.json");
            if (!Files.isRegularFile(file)) return null;
            JsonNode root = new ObjectMapper().readTree(Files.readString(file));
            for (JsonNode c : root) {
                String id = c.path("id").asText("");
                String name = c.path("name").asText("");
                if (identifier.equalsIgnoreCase(id) || identifier.equalsIgnoreCase(name)) {
                    String schema = c.path("schema").asText("");
                    if (!schema.isBlank()) return schema.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Saves the schema; blank removes it. */
    public static void set(String connectionId, String schema) {
        if (connectionId == null) return;
        String val = (schema == null || schema.isBlank()) ? "" : schema.trim();
        CACHE.put(connectionId, val);
        try {
            if (val.isEmpty()) PREFS.remove(key(connectionId));
            else PREFS.put(key(connectionId), val);
        } catch (Exception ignored) {
            // preferences unavailable: the default schema is simply not remembered
        }
    }

    /** True the first time it is asked for this connection + database in the session (then false). */
    public static synchronized boolean firstApply(String connectionId, String catalog) {
        return APPLIED.add(connectionId + "|" + catalog);
    }

    /** Forgets what was applied for a connection (Refresh / reconnect / profile edited). */
    public static synchronized void resetApplied(String connectionId) {
        APPLIED.removeIf(k -> k.startsWith(connectionId + "|"));
    }

    /** Once per user: copy {@code schema} of every LLTool connection that has no default schema here yet. */
    public static void importFromLltoolOnce() {
        try {
            if (PREFS.getBoolean(IMPORT_FLAG, false)) return;
            PREFS.putBoolean(IMPORT_FLAG, true);
            String appData = System.getenv("APPDATA");
            if (appData == null) return;
            Path file = Path.of(appData, "LLTool", "connections.json");
            if (!Files.isRegularFile(file)) return;
            JsonNode root = new ObjectMapper().readTree(Files.readString(file));
            int n = 0;
            for (JsonNode c : root) {
                String id = c.path("id").asText("");
                String name = c.path("name").asText("");
                String schema = c.path("schema").asText("");
                if (schema.isBlank()) continue;
                if (!id.isEmpty()) set(id, schema);
                if (!name.isEmpty()) set(name, schema);
                n++;
            }
            log.info("Imported default schema of {} LLTool connection(s)", n);
        } catch (Exception e) {
            log.warn("Could not import LLTool default schemas: {}", e.getMessage());
        }
    }
}
