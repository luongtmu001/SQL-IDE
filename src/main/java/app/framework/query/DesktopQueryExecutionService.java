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
import java.util.concurrent.atomic.AtomicReference;

/**
 * DesktopQueryExecutionService — Asynchronous SQL Execution Engine using Java 21 Virtual Threads.
 * Features:
 * - Non-blocking background query execution.
 * - Live Statement cancellation via java.sql.Statement.cancel().
 * - Dynamic metadata extraction and mapping to QueryResult.
 * - Handles both Query ResultSets and DDL/DML update counts.
 */
public class DesktopQueryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DesktopQueryExecutionService.class);
    private static DesktopQueryExecutionService instance;

    private final BooleanProperty executing = new SimpleBooleanProperty(false);
    private final AtomicReference<Statement> currentStatement = new AtomicReference<>(null);
    private final AtomicReference<Thread> currentWorkerThread = new AtomicReference<>(null);

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
    }

    private DesktopQueryExecutionService() {}

    public static synchronized DesktopQueryExecutionService getInstance() {
        if (instance == null) {
            instance = new DesktopQueryExecutionService();
        }
        return instance;
    }

    public BooleanProperty executingProperty() {
        return executing;
    }

    public boolean isExecuting() {
        return executing.get();
    }

    /**
     * Executes the given SQL query asynchronously using a Java 21 Virtual Thread.
     */
    public synchronized void execute(String sql, String connectionName, String databaseName, String schemaName, QueryCallback callback) {
        if (sql == null || sql.trim().isEmpty()) {
            if (callback != null) {
                callback.onError("No SQL statement provided to execute.", null);
            }
            return;
        }

        if (isExecuting()) {
            if (callback != null) {
                callback.onError("A query is already currently executing. Please wait or cancel it.", null);
            }
            return;
        }

        executing.set(true);

        if (callback != null) {
            callback.onStart(sql, connectionName);
        }

        Thread worker = Thread.ofVirtual().name("query-executor-" + System.currentTimeMillis()).start(() -> {
            long startTime = System.currentTimeMillis();
            Statement stmt = null;

            try {
                Connection rawConn = resolveJdbcConnection(connectionName, databaseName);

                if (rawConn == null) {
                    throw new SQLException("Could not establish a valid connection for: " + (connectionName != null ? connectionName : "default"));
                }

                // Apply target catalog / schema if applicable
                if (databaseName != null && !databaseName.isBlank()) {
                    try {
                        rawConn.setCatalog(databaseName);
                    } catch (Exception ignored) {}
                }
                if (schemaName != null && !schemaName.isBlank()) {
                    try {
                        rawConn.setSchema(schemaName);
                    } catch (Exception ignored) {}
                }

                stmt = rawConn.createStatement();
                currentStatement.set(stmt);

                List<QueryResult> allResultSets = new ArrayList<>();
                int totalRows = 0;
                int lastUpdateCount = -1;

                boolean isResultSet = stmt.execute(sql);
                while (true) {
                    if (isResultSet) {
                        try (ResultSet rs = stmt.getResultSet()) {
                            ResultSetMetaData meta = rs.getMetaData();
                            int colCount = meta.getColumnCount();

                            List<QueryResultColumn> columns = new ArrayList<>();
                            for (int i = 1; i <= colCount; i++) {
                                String label = meta.getColumnLabel(i);
                                String type = meta.getColumnTypeName(i);
                                columns.add(new QueryResultColumn(label != null ? label : ("Col" + i), type));
                            }

                            List<QueryResultRow> rows = new ArrayList<>();
                            int rowNum = 1;
                            while (rs.next()) {
                                List<Object> values = new ArrayList<>();
                                for (int i = 1; i <= colCount; i++) {
                                    values.add(rs.getObject(i));
                                }
                                rows.add(new QueryResultRow(rowNum++, values));

                                if (rows.size() >= 10000) {
                                    break;
                                }
                            }

                            totalRows += rows.size();
                            long durationSoFar = System.currentTimeMillis() - startTime;
                            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                            allResultSets.add(new QueryResult(sql, columns, rows, durationSoFar, ts));
                        }
                    } else {
                        int updateCount = stmt.getUpdateCount();
                        if (updateCount == -1) {
                            break;
                        }
                        lastUpdateCount = updateCount;
                    }

                    try {
                        isResultSet = stmt.getMoreResults();
                    } catch (SQLException e) {
                        break;
                    }
                }

                long duration = System.currentTimeMillis() - startTime;
                final int finalTotalRows = totalRows;
                final int finalUpdateCount = lastUpdateCount;
                QueryResult primaryResult = allResultSets.isEmpty() ? null : allResultSets.get(0);

                Platform.runLater(() -> {
                    executing.set(false);
                    if (callback != null) {
                        if (!allResultSets.isEmpty()) {
                            String msg = allResultSets.size() == 1
                                    ? String.format("%d rows retrieved (%d ms)", finalTotalRows, duration)
                                    : String.format("%d rows retrieved (%d ms) in %d result sets", finalTotalRows, duration, allResultSets.size());
                            callback.onSuccess(primaryResult, allResultSets, finalTotalRows, msg, duration);
                        } else {
                            String msg = String.format("Query executed successfully. %d rows affected (%d ms)", finalUpdateCount, duration);
                            callback.onSuccess(null, Collections.emptyList(), finalUpdateCount, msg, duration);
                        }
                    }
                });

            } catch (SQLException e) {
                long duration = System.currentTimeMillis() - startTime;
                boolean cancelled = isCancellationException(e);

                Platform.runLater(() -> {
                    executing.set(false);
                    if (callback != null) {
                        if (cancelled) {
                            callback.onCancelled();
                        } else {
                            callback.onError(formatSqlErrorMessage(e), e);
                        }
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    executing.set(false);
                    if (callback != null) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : t.toString(), t);
                    }
                });
            } finally {
                currentStatement.set(null);
                currentWorkerThread.set(null);
                executing.set(false);
            }
        });

        currentWorkerThread.set(worker);
    }

    /**
     * Cancels the currently active query statement if running.
     */
    public synchronized boolean cancel() {
        Statement stmt = currentStatement.get();
        if (stmt != null) {
            try {
                stmt.cancel();
                log.info("Sent cancel signal to active query statement.");
                return true;
            } catch (SQLException e) {
                log.warn("Failed to cancel active statement: {}", e.getMessage());
            }
        }

        Thread worker = currentWorkerThread.get();
        if (worker != null && worker.isAlive()) {
            worker.interrupt();
            return true;
        }

        return false;
    }

    private Connection resolveJdbcConnection(String connectionName, String targetDatabase) throws SQLException {
        ConnectionSessionManager sessionMgr = ConnectionSessionManager.getInstance();

        // Phase 7 safety: while the UI prototype runs on mock metadata, statements only ever run
        // in the local in-memory SQLite sandbox, never on a saved (possibly production) profile.
        // Real execution is part of Gate D.
        if (app.framework.metadata.MockMetadataProvider.isEnabled()) {
            return getOrCreateInMemorySqlite();
        }

        // 1. Check active session
        DatabaseConnection activeConn = sessionMgr.getActiveConnection();
        if (activeConn != null && activeConn.isValid(2)) {
            return activeConn.getRawJdbcConnection();
        }

        // 2. Look up profile by connectionName
        if (connectionName != null && !connectionName.isBlank()) {
            var allProfilesRes = DesktopBootstrap.getInstance().getConnectionApi().getConnections();
            if (allProfilesRes.isSuccess()) {
                for (ConnectionProfileDto profile : allProfilesRes.getDataOrThrow()) {
                    if (profile.name().equalsIgnoreCase(connectionName) || profile.id().equalsIgnoreCase(connectionName)) {
                        var connectRes = sessionMgr.connect(profile, null);
                        if (connectRes.isSuccess()) {
                            return connectRes.getDataOrThrow().getRawJdbcConnection();
                        }
                    }
                }
            }
        }

        // 3. Sandbox fallback: In-memory SQLite connection
        return getOrCreateInMemorySqlite();
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

    private boolean isCancellationException(SQLException e) {
        if (e == null) return false;
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String state = e.getSQLState() != null ? e.getSQLState() : "";
        return msg.contains("cancel") || msg.contains("interrupted") || "08003".equals(state) || "57014".equals(state);
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

