package app.framework.syntax;

import app.framework.connection.ConnectionSessionManager;
import app.framework.query.ScriptRunner;
import app.framework.session.CredentialResolver;
import app.framework.session.TabSession;
import app.framework.session.TabSessionManager;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Background syntax check of the editor tabs (Phase 8 / D5, S1a: SQL Server with {@code SET PARSEONLY ON}).
 *
 * <ul>
 *   <li>Debounce {@value #DEBOUNCE_MS} ms per tab; only the newest text of a tab is checked and delivered.</li>
 *   <li>Own connection per connection + database, never the tab's query connection ({@code PARSEONLY} would
 *       stop that tab's queries from running). One worker thread serializes all checks; idle connections are
 *       closed after {@value #IDLE_MS} ms; they are closed too when the connection is disconnected / on exit.</li>
 *   <li>A tab that is not connected, a dialect that is not supported yet, or a check that cannot run gives
 *       {@code checked == false}: the editor shows no markers instead of guessing.</li>
 * </ul>
 */
public final class SyntaxCheckService {

    private static final Logger log = LoggerFactory.getLogger(SyntaxCheckService.class);
    static final long DEBOUNCE_MS = 500;
    static final long IDLE_MS = 180_000;
    static final int MAX_CHARS = 1_000_000;

    /** @param checked false = nothing was checked (no connection / dialect not supported / disabled / failed) */
    public record Outcome(String tabId, boolean checked, List<SyntaxDiagnostic> diagnostics) {
        public int errorsCount() {
            if (diagnostics == null) return 0;
            int count = 0;
            for (var d : diagnostics) {
                if (d.severity() == SyntaxDiagnostic.Severity.ERROR) count++;
            }
            return count;
        }

        public int warningsCount() {
            if (diagnostics == null) return 0;
            int count = 0;
            for (var d : diagnostics) {
                if (d.severity() == SyntaxDiagnostic.Severity.WARNING || d.severity() == SyntaxDiagnostic.Severity.HINT) count++;
            }
            return count;
        }
    }

    private static final SyntaxCheckService INSTANCE = new SyntaxCheckService();

    public static SyntaxCheckService getInstance() {
        return INSTANCE;
    }

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "syntax-timer"));
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "syntax-check"));
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> versions = new ConcurrentHashMap<>();
    /** worker thread only */
    private final Map<String, Entry> connections = new HashMap<>();

    private static final class Entry {
        final DatabaseConnection connection;
        long lastUse = System.currentTimeMillis();

        Entry(DatabaseConnection connection) {
            this.connection = connection;
        }
    }

    private SyntaxCheckService() {
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    /**
     * Checks {@code sql} of the tab after the debounce; {@code onResult} runs on the FX thread unless the text
     * was replaced by a newer {@code schedule} call in the meantime.
     */
    public void schedule(String tabId, String sql, boolean enabled, Consumer<Outcome> onResult) {
        int version = versions.computeIfAbsent(tabId, k -> new AtomicInteger()).incrementAndGet();
        ScheduledFuture<?> old = pending.remove(tabId);
        if (old != null) old.cancel(false);
        if (!enabled) {
            deliver(tabId, version, new Outcome(tabId, false, List.of()), onResult);
            return;
        }
        pending.put(tabId, timer.schedule(() -> worker.execute(() -> {
            Outcome o;
            try {
                if (versions.get(tabId).get() != version) return;   // a newer text is waiting
                o = run(tabId, sql);
            } catch (Throwable t) {
                log.warn("Syntax check failed for tab {}: {}", tabId, t.toString());
                o = new Outcome(tabId, false, List.of());
            }
            deliver(tabId, version, o, onResult);
        }), DEBOUNCE_MS, TimeUnit.MILLISECONDS));
    }

    /** The tab was closed. */
    public void forget(String tabId) {
        ScheduledFuture<?> f = pending.remove(tabId);
        if (f != null) f.cancel(false);
        versions.remove(tabId);
    }

    /** A connection was disconnected: drop its syntax connections. */
    public void closeFor(String profileId) {
        SyntaxCoordinator.getInstance().invalidateCache(profileId);
        worker.execute(() -> {
            connections.entrySet().removeIf(e -> {
                if (e.getKey().startsWith(profileId + "|")) {
                    closeQuietly(e.getValue());
                    return true;
                }
                return false;
            });
        });
    }

    public void closeAll() {
        SyntaxCoordinator.getInstance().invalidateCache(null);
        worker.execute(() -> {
            connections.values().forEach(SyntaxCheckService::closeQuietly);
            connections.clear();
        });
    }

    private void deliver(String tabId, int version, Outcome o, Consumer<Outcome> onResult) {
        AtomicInteger v = versions.get(tabId);
        if (v == null || v.get() != version || onResult == null) return;
        Platform.runLater(() -> {
            AtomicInteger cur = versions.get(tabId);
            if (cur != null && cur.get() == version) onResult.accept(o);
        });
    }

    private Outcome run(String tabId, String sql) {
        Outcome none = new Outcome(tabId, false, List.of());
        if (sql == null || sql.length() > MAX_CHARS) return none;
        if (sql.isBlank()) return new Outcome(tabId, true, List.of());

        TabSession session = TabSessionManager.getInstance().get(tabId);
        if (session == null || session.state() != TabSession.State.CONNECTED || session.profile() == null) {
            List<SyntaxDiagnostic> offline = SyntaxCoordinator.getInstance().validate(
                    sql, "sqlserver", null, null, null, null, false, true, null);
            return new Outcome(tabId, true, offline);
        }

        ConnectionProfileDto profile = session.profile();
        ScriptRunner.Dialect dialect = ScriptRunner.Dialect.of(profile.databaseType());
        String dialectStr = (dialect == ScriptRunner.Dialect.POSTGRESQL) ? "postgresql" : "sqlserver";

        sweepIdle();
        String key = profile.id() + "|" + session.database();
        Entry entry = connections.get(key);
        if (entry == null) {
            long perfStart = System.currentTimeMillis();
            var res = ConnectionSessionManager.getInstance()
                    .openSession(profile, session.database(), CredentialResolver.resolve(profile, null));
            app.framework.intellisense.IntellisensePerf.log("syntax connection opened in " + (System.currentTimeMillis() - perfStart) + " ms");
            if (res.isFailure()) {
                List<SyntaxDiagnostic> offline = SyntaxCoordinator.getInstance().validate(
                        sql, dialectStr, null, profile.id(), session.database(), null, false, true, null);
                return new Outcome(tabId, true, offline);
            }
            entry = new Entry(res.getDataOrThrow());
            connections.put(key, entry);
        }
        try {
            long perfCheck = System.currentTimeMillis();

            Map<String, List<String>> cachedTables = null;
            try {
                var detailsFut = app.framework.intellisense.MetadataSnapshotStore.getInstance()
                        .details(profile, session.database());
                var details = (detailsFut != null && detailsFut.isDone()) ? detailsFut.getNow(null) : null;
                if (details != null && details.columnsJson() != null) {
                    cachedTables = parseColumnsFromJson(details.columnsJson());
                }
            } catch (Exception ignored) { }

            List<SyntaxDiagnostic> d = SyntaxCoordinator.getInstance().validate(
                    sql,
                    dialectStr,
                    entry.connection.getRawJdbcConnection(),
                    profile.id(),
                    session.database(),
                    null,
                    false,
                    true,
                    cachedTables
            );
            entry.lastUse = System.currentTimeMillis();
            app.framework.intellisense.IntellisensePerf.log("syntax check " + (entry.lastUse - perfCheck) + " ms, " + sql.length() + " chars, " + d.size() + " diagnostics");
            return new Outcome(tabId, true, d);
        } catch (Exception e) {
            log.debug("Syntax connection {} dropped: {}", key, e.getMessage());
            connections.remove(key);
            closeQuietly(entry);
            List<SyntaxDiagnostic> offline = SyntaxCoordinator.getInstance().validate(
                    sql, dialectStr, null, profile.id(), session.database(), null, false, true, null);
            return new Outcome(tabId, true, offline);
        }
    }

    private void sweepIdle() {
        long now = System.currentTimeMillis();
        connections.entrySet().removeIf(e -> {
            if (now - e.getValue().lastUse > IDLE_MS) {
                closeQuietly(e.getValue());
                return true;
            }
            return false;
        });
    }

    private static void closeQuietly(Entry e) {
        try {
            e.connection.close();
        } catch (Exception ignored) {
            // already closed
        }
    }

    private static Map<String, List<String>> parseColumnsFromJson(Map<String, String> columnsJson) {
        if (columnsJson == null || columnsJson.isEmpty()) return null;
        Map<String, List<String>> out = new HashMap<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (var e : columnsJson.entrySet()) {
            String key = e.getKey(); // "schema|table"
            String schema = key.contains("|") ? key.substring(0, key.indexOf('|')) : "";
            String table = key.contains("|") ? key.substring(key.indexOf('|') + 1) : key;
            try {
                com.fasterxml.jackson.databind.JsonNode arr = mapper.readTree(e.getValue());
                if (arr.isArray()) {
                    List<String> cols = new ArrayList<>();
                    for (var item : arr) {
                        String colName = item.path("name").asText(null);
                        if (colName != null) cols.add(colName);
                    }
                    if (!schema.isBlank()) {
                        out.put(schema + "." + table, cols);
                    }
                    out.put(table, cols);
                }
            } catch (Exception ignored) { }
        }
        return out;
    }
}
