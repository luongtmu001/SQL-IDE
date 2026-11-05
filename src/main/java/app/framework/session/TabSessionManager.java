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
            if (current != null && sameProfile && current.isValid(2)) {
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

            Result<DatabaseConnection> res = ConnectionSessionManager.getInstance()
                    .openSession(profile, targetDb, CredentialResolver.resolve(profile, null));
            if (res.isFailure()) {
                String msg = res.getError().map(AppError::message).orElse("Không kết nối được.");
                s.failed(msg);
                changed();
                return res;
            }
            DatabaseConnection conn = res.getDataOrThrow();
            s.connected(profile, targetDb, conn, readServerPid(conn, type));
            changed();
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
        if (name == null || name.isBlank()) return null;
        try {
            var res = DesktopBootstrap.getInstance().getConnectionApi().getConnections();
            if (res.isSuccess()) {
                for (ConnectionProfileDto p : res.getDataOrThrow()) {
                    if (name.equalsIgnoreCase(p.name()) || name.equalsIgnoreCase(p.id())) return p;
                }
            }
        } catch (Exception e) {
            log.debug("Profile lookup failed: {}", e.getMessage());
        }
        return null;
    }
}
