package app.framework.query;

import app.framework.bootstrap.DesktopBootstrap;
import app.framework.connection.ConnectionSessionManager;
import app.models.QueryResult;
import app.models.QueryResultColumn;
import app.models.QueryResultRow;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import com.lltool.infrastructure.database.api.DatabaseProvider;
import com.lltool.infrastructure.database.api.DatabaseType;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * DesktopQueryExecutionService — Asynchronous SQL Execution Engine using Java 21 Virtual Threads.
 * Features:
 * - Non-blocking background query execution.
 * - Live Statement cancellation via java.sql.Statement.cancel().
 * - Dynamic metadata extraction and mapping to QueryResult.
 * - Handles both Query ResultSets and DDL/DML update counts.
 * Phase 8 / D3 (part 2): one run per TAB — tabs run in parallel, each on its own session, and Stop cancels
 * only that tab. The script itself is run by {@link ScriptRunner} (GO batches / statements, stopOnError).
 */
public class DesktopQueryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DesktopQueryExecutionService.class);
    private static DesktopQueryExecutionService instance;
    private static final String NO_TAB = "default";

    /** A script running for one tab. */
    private static final class Run {
        final ScriptRunner.Handle handle = new ScriptRunner.Handle();
        volatile Thread worker;
    }

    /** Running tabs (key = tab document id, or "default"). Changed on the FX thread only. */
    private final ObservableSet<String> runningTabs = FXCollections.observableSet(new HashSet<>());
    private final BooleanProperty executing = new SimpleBooleanProperty(false);
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    /** Which tab "Stop" / the loading overlay's Cancel means; set by the MainController. */
    private volatile Supplier<String> activeTabSupplier;

    /** Test hook: gives the connection (and its database type) for a tab instead of the tab session. */
    public record TestConnection(Connection connection, String databaseType) {}
    private static volatile java.util.function.BiFunction<String, String, TestConnection> testConnections;

    public static void setTestConnectionProvider(java.util.function.BiFunction<String, String, TestConnection> provider) {
        testConnections = provider;
    }

    // Fallback in-memory database connection for sandbox query testing
    private Connection inMemorySqliteConn;

    public interface QueryCallback {
        void onStart(String sql, String connectionName);
        default void onSuccess(QueryResult result, int rowsAffected, String summaryMessage, long durationMs) {
            onSuccess(result, result != null ? List.of(result) : Collections.emptyList(), rowsAffected, summaryMessage, durationMs);
        }
        default void onSuccess(QueryResult result, List<QueryResult> allResults, int rowsAffected, String summaryMessage, long durationMs) {
            onSuccess(result, rowsAffected, summaryMessage, durationMs);
        }
        void onError(String errorMessage, Throwable cause);
        void onCancelled();

        /** Phase 8 / D3: severity of a server message (PRINT / RAISE NOTICE), an engine notice or a statement error. */
        enum MessageKind { INFO, WARNING, ERROR }

        /** Server / engine message. Called on the FX thread, before onSuccess / onError / onCancelled. */
        default void onMessage(String message, MessageKind kind) {
        }

        /**
         * D4 streaming: a result set starts (FX thread); its rows follow through {@link #onRows} while they
         * are read. The final onSuccess still carries every result.
         */
        default void onResultStarted(int index, String sql, List<QueryResultColumn> columns) {
        }

        /** D4 streaming: the next rows of result set {@code index} (FX thread, in order). */
        default void onRows(int index, List<QueryResultRow> rows) {
        }
    }

    private DesktopQueryExecutionService() {
        runningTabs.addListener((javafx.collections.SetChangeListener<String>) c -> executing.set(!runningTabs.isEmpty()));
    }

    public static synchronized DesktopQueryExecutionService getInstance() {
        if (instance == null) {
            instance = new DesktopQueryExecutionService();
        }
        return instance;
    }

    /** True while ANY tab runs. */
    public BooleanProperty executingProperty() {
        return executing;
    }

    public boolean isExecuting() {
        return !runs.isEmpty();
    }

    public boolean isExecuting(String tabId) {
        return runs.containsKey(key(tabId));
    }

    /** Tabs that are running (FX thread view). */
    /** True while the tab has a run (thread-safe, any thread). */
    public boolean isRunning(String tabId) {
        return runs.containsKey(key(tabId));
    }

    public ObservableSet<String> runningTabs() {
        return runningTabs;
    }

    public void setActiveTabSupplier(Supplier<String> supplier) {
        this.activeTabSupplier = supplier;
    }

    private static String key(String tabId) {
        return tabId != null ? tabId : NO_TAB;
    }

    private static void fx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            try {
                Platform.runLater(r);
            } catch (IllegalStateException e) {
                r.run(); // toolkit not running
            }
        }
    }

    /**
     * Executes the given SQL query asynchronously using a Java 21 Virtual Thread.
     */
    public void execute(String sql, String connectionName, String databaseName, String schemaName, QueryCallback callback) {
        execute(null, sql, connectionName, databaseName, schemaName, false, callback);
    }

    /** Same as before D3: row cap 10,000 and no timeout. */
    public void execute(String tabId, String sql, String connectionName, String databaseName, String schemaName,
                        boolean prodConfirmed, QueryCallback callback) {
        execute(tabId, sql, connectionName, databaseName, schemaName, prodConfirmed, QueryOptions.legacy(), callback);
    }

    /**
     * Phase 8 / D1: runs {@code sql} on the tab's own session ({@link app.framework.session.TabSessionManager}).
     * The statement is checked by {@link SqlStatementClassifier} first: blocked on read-only sessions,
     * and on PRODUCTION unless {@code prodConfirmed} (the caller showed DLG-007b).
     * Other tabs may be running at the same time; the same tab cannot run twice at once.
     */
    public void execute(String tabId, String sql, String connectionName, String databaseName, String schemaName,
                        boolean prodConfirmed, QueryOptions options, QueryCallback callback) {
        final QueryOptions opts = options != null ? options : QueryOptions.legacy();
        if (sql == null || sql.trim().isEmpty()) {
            if (callback != null) {
                callback.onError("No SQL statement provided to execute.", null);
            }
            return;
        }
        final String key = key(tabId);
        final Run run = new Run();
        if (runs.putIfAbsent(key, run) != null) {
            if (callback != null) {
                callback.onError("Tab này đang chạy một câu lệnh. Chờ xong hoặc bấm Dừng rồi chạy lại.", null);
            }
            return;
        }
        fx(() -> runningTabs.add(key));

        if (callback != null) {
            callback.onStart(sql, connectionName);
        }

        run.worker = Thread.ofVirtual().name("query-" + key).start(() -> {
            long startTime = System.currentTimeMillis();
            try {
                Connection rawConn;
                ScriptRunner.Dialect dialect;
                var provider = testConnections;
                if (provider != null) {
                    TestConnection tc = provider.apply(key, connectionName);
                    rawConn = tc.connection();
                    dialect = ScriptRunner.Dialect.of(tc.databaseType());
                } else if (app.framework.backend.BackendFlags.real(app.framework.backend.BackendFlags.Area.QUERY)) {
                    var profile = app.framework.session.TabSessionManager.profileByName(connectionName);
                    var tabSessions = app.framework.session.TabSessionManager.getInstance();
                    // a session that was open and died is not reopened silently (user decision 2026-11-06)
                    var opened = tabSessions.ensureConnected(key, profile, databaseName, false);
                    if (opened.isFailure()) {
                        String why = opened.getError().map(er -> er.message()).orElse("Không kết nối được.");
                        if (tabSessions.state(key) == app.framework.session.TabSession.State.LOST) {
                            throw new app.framework.session.ConnectionLostException(why);
                        }
                        throw new SQLException(why);
                    }
                    var session = app.framework.session.TabSessionManager.getInstance().get(key);
                    var decision = SqlStatementClassifier.check(sql, session.readOnly(), session.environment(), prodConfirmed);
                    if (!decision.allowed()) {
                        throw new SQLException(decision.message(), decision.errorCode());
                    }
                    rawConn = opened.getDataOrThrow().getRawJdbcConnection();
                    dialect = ScriptRunner.Dialect.of(profile.databaseType());
                    if (schemaName != null && !schemaName.isBlank() && dialect == ScriptRunner.Dialect.POSTGRESQL) {
                        try {
                            rawConn.setSchema(schemaName);
                        } catch (Exception ignored) {}
                    }
                } else {
                    // UI prototype: local in-memory SQLite sandbox only
                    rawConn = getOrCreateInMemorySqlite();
                    dialect = ScriptRunner.Dialect.SQLITE;
                }

                // D4: rows go to the grid in batches while they are read (FX thread, in order, before the end)
                ScriptRunner.RowSink sink = callback == null ? null : new ScriptRunner.RowSink() {
                    @Override
                    public void resultStarted(int index, String setSql, List<QueryResultColumn> columns) {
                        fx(() -> callback.onResultStarted(index, setSql, columns));
                    }

                    @Override
                    public void rows(int index, List<QueryResultRow> batch) {
                        fx(() -> callback.onRows(index, batch));
                    }
                };
                ScriptRunner.Outcome out = ScriptRunner.run(rawConn, sql, dialect, opts, run.handle, sink);
                if (provider == null && out.errorCount() > 0 && !out.cancelled()
                        && app.framework.backend.BackendFlags.real(app.framework.backend.BackendFlags.Area.QUERY)) {
                    // the error may be the connection breaking: the session (and its connection) are checked
                    app.framework.session.TabSessionManager.getInstance().checkAfterFailure(key);
                }
                long duration = System.currentTimeMillis() - startTime;
                finish(key, () -> deliver(callback, out, duration, opts));
            } catch (SQLException e) {
                if (!(e instanceof app.framework.session.ConnectionLostException) && testConnections == null && !run.handle.isCancelled()
                        && app.framework.backend.BackendFlags.real(app.framework.backend.BackendFlags.Area.QUERY)) {
                    app.framework.session.TabSessionManager.getInstance().checkAfterFailure(key);
                }
                String msg = formatSqlErrorMessage(e);
                finish(key, () -> {
                    if (callback == null) return;
                    if (run.handle.isCancelled()) callback.onCancelled();
                    else callback.onError(msg, e);
                });
            } catch (Throwable t) {
                log.warn("Query run failed: {}", t.toString());
                finish(key, () -> {
                    if (callback != null) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : t.toString(), t);
                    }
                });
            }
        });
    }

    /** Removes the run first (so the tab can run again from inside the callback), then calls back on the FX thread. */
    private void finish(String key, Runnable callbackWork) {
        runs.remove(key);
        fx(() -> {
            runningTabs.remove(key);
            callbackWork.run();
        });
    }

    private static void deliver(QueryCallback callback, ScriptRunner.Outcome out, long duration, QueryOptions opts) {
        if (callback == null) return;
        for (ScriptRunner.Message m : out.messages()) {
            callback.onMessage(m.text(), switch (m.kind()) {
                case WARNING -> QueryCallback.MessageKind.WARNING;
                case ERROR -> QueryCallback.MessageKind.ERROR;
                default -> QueryCallback.MessageKind.INFO;
            });
        }
        if (out.cancelled()) {
            callback.onCancelled();
            return;
        }
        if (out.timedOut()) {
            callback.onError(String.format("Hết thời gian chờ: câu lệnh bị dừng sau %d giây (Cài đặt › SQL › Timeout).",
                    opts.timeoutSeconds()), null);
            return;
        }
        List<QueryResult> all = out.results();
        String errPart = out.errorCount() > 0
                ? String.format(" · %d lỗi%s", out.errorCount(),
                    out.piecesRun() < out.piecesTotal() ? String.format(" (dừng ở %d/%d)", out.piecesRun(), out.piecesTotal()) : "")
                : "";
        if (!all.isEmpty()) {
            String msg = all.size() == 1
                    ? String.format("%d rows retrieved (%d ms)%s", out.totalRows(), duration, errPart)
                    : String.format("%d rows retrieved (%d ms) in %d result sets%s", out.totalRows(), duration, all.size(), errPart);
            callback.onSuccess(all.get(0), all, out.totalRows(), msg, duration);
        } else if (out.errorCount() > 0) {
            callback.onError(out.firstError(), null);
        } else {
            String msg = String.format("Query executed successfully. %d rows affected (%d ms)", out.lastUpdateCount(), duration);
            callback.onSuccess(null, Collections.emptyList(), out.lastUpdateCount(), msg, duration);
        }
    }

    /** Cancels the run of {@code tabId} only. */
    public boolean cancel(String tabId) {
        Run run = runs.get(key(tabId));
        if (run == null) return false;
        run.handle.cancel();
        log.info("Cancel sent for tab {}", key(tabId));
        return true;
    }

    /** Cancels the active tab's run (Stop button / loading overlay); every run when no active tab is known. */
    public boolean cancel() {
        Supplier<String> s = activeTabSupplier;
        if (s != null) {
            String active = s.get();
            if (runs.containsKey(key(active))) return cancel(active);
        }
        boolean any = false;
        for (String k : List.copyOf(runs.keySet())) any |= cancel(k);
        return any;
    }

    private synchronized Connection getOrCreateInMemorySqlite() throws SQLException {
        if (inMemorySqliteConn == null || inMemorySqliteConn.isClosed()) {
            inMemorySqliteConn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
            // Create sample schema for immediate testing
            try (Statement s = inMemorySqliteConn.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, username TEXT, email TEXT, status TEXT);");
                s.execute("INSERT INTO users (username, email, status) VALUES ('admin', 'admin@example.com', 'active'), ('guest', 'guest@example.com', 'inactive');");
            }
        }
        return inMemorySqliteConn;
    }

    private String formatSqlErrorMessage(SQLException e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage());
        if (e.getSQLState() != null && !e.getSQLState().isBlank()) {
            sb.append(" [SQLState: ").append(e.getSQLState()).append("]");
        }
        if (e.getErrorCode() != 0) {
            sb.append(" [ErrorCode: ").append(e.getErrorCode()).append("]");
        }
        return sb.toString();
    }
}
