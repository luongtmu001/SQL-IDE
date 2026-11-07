package app.framework.session;

import app.framework.bootstrap.DesktopBootstrap;
import app.framework.connection.ConnectionSessionManager;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;
import com.lltool.core.common.error.AppError;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseType;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Phase 8 / D1: one database session per editor tab (user decision 2026-11-05; LLTool
 * {@code ConnectionManager.get_or_create_for_tab}, SSMS model).
 *
 * <p>Each tab owns its JDBC connection, current database, state and error, so tabs never share
 * a transaction, temp tables, {@code SET} options or a running statement. The Explorer uses its
 * own metadata connections, never a tab's session.</p>
 *
 * <p>Every session is opened read-only when the profile is read-only or {@link
 * app.framework.backend.BackendFlags#readOnlyAll()} is on (JDBC {@code setReadOnly(true)} and,
 * for PostgreSQL, {@code SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY}); statements are
 * also checked by {@link app.framework.query.SqlStatementClassifier} before they run.</p>
 */
public final class TabSessionManager {

    private static final Logger log = LoggerFactory.getLogger(TabSessionManager.class);
    private static final TabSessionManager INSTANCE = new TabSessionManager();

    private final Map<String, TabSession> sessions = new ConcurrentHashMap<>();
    private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper(0);

    private TabSessionManager() {
    }

    public static TabSessionManager getInstance() {
        return INSTANCE;
    }

    /** Bumped (on the FX thread) whenever a session changes; tab headers / status bar refresh on it. */
    public ReadOnlyIntegerProperty revisionProperty() {
        return revision.getReadOnlyProperty();
    }

    public TabSession get(String tabId) {
        return tabId == null ? null : sessions.get(tabId);
    }

    public TabSession.State state(String tabId) {
        TabSession s = get(tabId);
        return s == null ? TabSession.State.NONE : s.state();
    }

    /**
     * Opens (or reuses) the tab's connection to {@code profile} / {@code database}. Blocking: call
     * from a background thread. Reuses a live session of the same profile; switches database with
     * {@code setCatalog} (SQL Server) or reconnects (PostgreSQL, where a session is bound to one DB).
     */
    public Result<DatabaseConnection> ensureConnected(String tabId, ConnectionProfileDto profile, String database) {
        return ensureConnected(tabId, profile, database, true);
    }

    /**
     * {@code reopenDead == false} (a run): a session that was open but is dead is NOT reopened silently; it is
     * marked {@link TabSession.State#LOST} and the failure says so, so the user confirms the reconnect
     * (user decision 2026-11-06). The connection-level check then decides whether every tab of it is lost.
     */
    public Result<DatabaseConnection> ensureConnected(String tabId, ConnectionProfileDto profile, String database,
                                                      boolean reopenDead) {
        Objects.requireNonNull(tabId, "tabId");
        if (profile == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED,
                    "Tab chưa chọn kết nối. Chọn kết nối trên thanh công cụ rồi chạy lại."));
        }
        TabSession s = sessions.computeIfAbsent(tabId, TabSession::new);
        synchronized (s) {
            DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
            String targetDb = database != null && !database.isBlank() ? database : profile.databaseName();
            DatabaseConnection current = s.connection();
            boolean sameProfile = s.profile() != null && Objects.equals(s.profile().id(), profile.id());
            boolean alive = current != null && sameProfile && isAlive(current);
            if (!reopenDead && current != null && sameProfile && !alive) {
                String msg = "Kết nối tới " + profile.name() + " đã bị ngắt.";
                closeQuietly(s);
                s.lost(msg);
                changed();
                log.info("Tab {}: session to '{}' is dead (found before a run)", tabId, profile.name());
                checkConnectionAfterLoss(profile, tabId);
                return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, msg));
            }
            if (alive) {
                if (targetDb == null || targetDb.isBlank() || targetDb.equalsIgnoreCase(s.database())
                        || type == DatabaseType.SQLITE) {
                    return Result.success(current);
                }
                if (type == DatabaseType.SQLSERVER) {
                    try {
                        current.getRawJdbcConnection().setCatalog(targetDb);
                        s.connected(profile, targetDb, current, s.serverPid());
                        changed();
                        return Result.success(current);
                    } catch (Exception e) {
                        log.warn("setCatalog({}) failed for tab {}: {}", targetDb, tabId, e.getMessage());
                    }
                }
            }
            closeQuietly(s);
            s.connecting(profile, targetDb);
            changed();

            long perfStart = System.currentTimeMillis();
            Result<DatabaseConnection> res = ConnectionSessionManager.getInstance()
                    .openSession(profile, targetDb, CredentialResolver.resolve(profile, null));
            app.framework.intellisense.IntellisensePerf.log("tab session opened in " + (System.currentTimeMillis() - perfStart) + " ms, ok=" + res.isSuccess());
            if (res.isFailure()) {
                String msg = res.getError().map(AppError::message).orElse("Không kết nối được.");
                s.failed(msg);
                app.services.PasswordPolicyService.getInstance().forgetSessionPasswordIfAuthError(profile.id(), msg);
                changed();
                return res;
            }
            DatabaseConnection conn = res.getDataOrThrow();
            s.connected(profile, targetDb, conn, readServerPid(conn, type));
            changed();
            // the connection is live: the Explorer shows it as connected (bold name, coloured icon) whichever
            // way it was opened (banner "Kết nối", a run, a new tab)
            app.services.ExplorerConnectionState.getInstance().set(profile.id(), app.services.ExplorerConnectionState.Status.CONNECTED);
            log.info("Tab {} connected to '{}' ({}) db={} readOnly={}", tabId, profile.name(), type, targetDb, s.readOnly());
            return res;
        }
    }

    /** Non-blocking connect for the banner / toolbar; {@code done} runs on the FX thread. */
    public void connectAsync(String tabId, ConnectionProfileDto profile, String database,
                             Consumer<Result<DatabaseConnection>> done) {
        Thread.ofVirtual().name("tab-connect-" + tabId).start(() -> {
            Result<DatabaseConnection> r = ensureConnected(tabId, profile, database);
            if (done != null) Platform.runLater(() -> done.accept(r));
        });
    }

    public void disconnect(String tabId) {
        TabSession s = tabId == null ? null : sessions.get(tabId);
        if (s == null) return;
        synchronized (s) {
            closeQuietly(s);
            s.reset();
        }
        changed();
    }

    // ------------------------------------------------------------------ connection hierarchy (2026-11-06)
    // Explorer connection (profile) > tab sessions > the rest. A disconnect or a loss at connection level reaches
    // every tab of that connection; a dead tab session is checked against the others before the whole
    // connection is called lost.

    /** The user disconnected the connection in the Explorer (or deleted it): every tab session of it is closed. */
    public void closeAllFor(String profileId) {
        if (profileId == null) return;
        app.framework.syntax.SyntaxCheckService.getInstance().closeFor(profileId);
        var queries = app.framework.query.DesktopQueryExecutionService.getInstance();
        boolean any = false;
        for (TabSession s : sessions.values()) {
            if (s.profile() == null || !profileId.equals(s.profile().id()) || s.state() == TabSession.State.NONE) continue;
            queries.cancel(s.tabId());
            DatabaseConnection c = s.connection();
            s.reset();
            closeInBackground(c);
            any = true;
        }
        if (any) changed();
    }

    /** The connection itself is gone: every idle tab session of it becomes LOST, the Explorer shows the error. */
    public void markConnectionLost(ConnectionProfileDto profile, String message) {
        if (profile == null) return;
        var queries = app.framework.query.DesktopQueryExecutionService.getInstance();
        for (TabSession s : sessions.values()) {
            if (s.profile() == null || !profile.id().equals(s.profile().id())) continue;
            if (s.state() != TabSession.State.CONNECTED || queries.isRunning(s.tabId())) continue; // a running tab finds out itself
            DatabaseConnection c = s.connection();
            s.lost(message);
            closeInBackground(c);
        }
        app.services.ExplorerConnectionState.getInstance().set(profile.id(),
                app.services.ExplorerConnectionState.Status.ERROR, "Mất kết nối" + (message != null ? ": " + message : ""));
        changed();
        log.info("Connection '{}' lost: {}", profile.name(), message);
    }

    /**
     * Checks the tab's session in the background (active tab: on tab switch, window focus and every 30 s).
     * Skipped while the tab runs a statement, or when it has no open session.
     */
    public void probeAsync(String tabId) {
        TabSession s = get(tabId);
        if (s == null || s.state() != TabSession.State.CONNECTED) return;
        if (app.framework.query.DesktopQueryExecutionService.getInstance().isRunning(tabId)) return;
        DatabaseConnection c = s.connection();
        if (c == null) return;
        Thread.ofVirtual().name("tab-probe-" + tabId).start(() -> {
            if (isAlive(c)) return;
            ConnectionProfileDto profile = s.profile();
            if (s.connection() != c || s.state() != TabSession.State.CONNECTED) return; // changed meanwhile
            s.lost("Kết nối tới " + (profile != null ? profile.name() : "server") + " đã bị ngắt.");
            closeQuietlyConn(c);
            changed();
            checkConnectionAfterLoss(profile, tabId);
        });
    }

    /**
     * A run failed: when its session is no longer alive it becomes LOST and the connection is checked.
     * Blocking (call from the run's worker thread). Returns true when the session was lost.
     */
    public boolean checkAfterFailure(String tabId) {
        TabSession s = get(tabId);
        if (s == null) return false;
        DatabaseConnection c = s.connection();
        if (c == null || isAlive(c)) return false;
        ConnectionProfileDto profile = s.profile();
        s.lost("Kết nối tới " + (profile != null ? profile.name() : "server") + " đã bị ngắt trong lúc chạy.");
        closeQuietlyConn(c);
        changed();
        checkConnectionAfterLoss(profile, tabId);
        return true;
    }

    /**
     * One session died: if another open session of the same connection is still alive, only that tab lost its
     * session (e.g. it was killed on the server); otherwise the connection is lost for every tab and the Explorer.
     */
    private void checkConnectionAfterLoss(ConnectionProfileDto profile, String deadTabId) {
        if (profile == null) return;
        Thread.ofVirtual().name("conn-check-" + profile.name()).start(() -> {
            var queries = app.framework.query.DesktopQueryExecutionService.getInstance();
            for (TabSession other : sessions.values()) {
                if (other.tabId().equals(deadTabId) || other.state() != TabSession.State.CONNECTED) continue;
                if (other.profile() == null || !profile.id().equals(other.profile().id())) continue;
                if (queries.isRunning(other.tabId())) return; // busy = in use = the server answers
                DatabaseConnection c = other.connection();
                if (c != null && isAlive(c)) return;
            }
            TabSession dead = get(deadTabId);
            markConnectionLost(profile, dead != null ? dead.error() : null);
        });
    }

    private static boolean isAlive(DatabaseConnection c) {
        try {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private static void closeQuietlyConn(DatabaseConnection c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            log.debug("Closing dead session failed: {}", e.getMessage());
        }
    }

    /** Closing a connection to a server that is gone can block: never on the FX thread. */
    private static void closeInBackground(DatabaseConnection c) {
        if (c != null) Thread.ofVirtual().name("tab-close").start(() -> closeQuietlyConn(c));
    }

    /** Tab closed: the session goes away with it. */
    public void release(String tabId) {
        TabSession s = tabId == null ? null : sessions.remove(tabId);
        if (s == null) return;
        Thread.ofVirtual().name("tab-close-" + tabId).start(() -> {
            synchronized (s) {
                closeQuietly(s);
            }
        });
        changed();
    }

    /** App exit. */
    public void closeAll() {
        app.framework.syntax.SyntaxCheckService.getInstance().closeAll();
        for (TabSession s : sessions.values()) {
            synchronized (s) {
                closeQuietly(s);
            }
        }
        sessions.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static void closeQuietly(TabSession s) {
        DatabaseConnection c = s.connection();
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            log.debug("Closing tab session failed: {}", e.getMessage());
        }
    }

    /** Server process id shown in the status bar (LLTool "PID"). */
    private static Integer readServerPid(DatabaseConnection conn, DatabaseType type) {
        String sql = switch (type) {
            case SQLSERVER -> "SELECT @@SPID";
            case POSTGRESQL -> "SELECT pg_backend_pid()";
            default -> null;
        };
        if (sql == null) return null;
        try {
            Connection raw = conn.getRawJdbcConnection();
            try (Statement st = raw.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void changed() {
        if (Platform.isFxApplicationThread()) {
            revision.set(revision.get() + 1);
        } else {
            try {
                Platform.runLater(() -> revision.set(revision.get() + 1));
            } catch (IllegalStateException ignored) {
                // FX toolkit not running (tests)
            }
        }
    }

    /** Saved profile by name (tabs store the connection name). */
    public static ConnectionProfileDto profileByName(String name) {
        // short cache: this is asked several times per run / tab switch (was a read of every profile file)
        return app.framework.connection.ProfileLookup.byName(name);
    }
}
