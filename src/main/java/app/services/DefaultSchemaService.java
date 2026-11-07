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
    private static final String IMPORT_FLAG = "__lltool_import_v1";
    /** "connection|catalog" pairs whose default schema was already applied as a filter in this session. */
    private static final Set<String> APPLIED = new HashSet<>();

    private DefaultSchemaService() { }

    private static String key(String id) {
        String k = id == null ? "" : id.replace('/', '_');
        return k.length() > Preferences.MAX_KEY_LENGTH ? k.substring(0, Preferences.MAX_KEY_LENGTH) : k;
    }

    /** The saved default schema, or null when none (blank counts as none). */
    public static String get(String connectionId) {
        if (connectionId == null) return null;
        try {
            String v = PREFS.get(key(connectionId), null);
            return v == null || v.isBlank() ? null : v.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** Saves the schema; blank removes it. */
    public static void set(String connectionId, String schema) {
        if (connectionId == null) return;
        try {
            if (schema == null || schema.isBlank()) PREFS.remove(key(connectionId));
            else PREFS.put(key(connectionId), schema.trim());
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
                String schema = c.path("schema").asText("");
                if (id.isEmpty() || schema.isBlank() || get(id) != null) continue;
                set(id, schema);
                n++;
            }
            log.info("Imported default schema of {} LLTool connection(s)", n);
        } catch (Exception e) {
            log.warn("Could not import LLTool default schemas: {}", e.getMessage());
        }
    }
}
