package app.framework.query;

import app.models.QueryResult;
import app.models.QueryResultColumn;
import app.models.QueryResultRow;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 8 / D3: runs a script on one JDBC connection the way LLTool does.
 * <ul>
 *   <li>SQL Server: the script is split into batches at lines holding only {@code GO}
 *       ({@code sqlserver.py}); a batch keeps its variables / temp tables, so it is never split at {@code ;}.</li>
 *   <li>PostgreSQL / SQLite: one statement at a time ({@code postgresql.py}), split by {@link SqlStatementSplitter}.</li>
 *   <li>{@code stopOnError}: the first failing batch / statement stops the script; otherwise the error is reported
 *       and the next one runs. Results read before an error are kept.</li>
 *   <li>Cancel through a {@link Handle} ({@code Statement.cancel()} on the running statement), timeout per statement.</li>
 * </ul>
 * No UI or FX dependency: it runs on the caller's thread, so it can be tested against real servers.
 */
public final class ScriptRunner {

    public enum Dialect {
        SQLSERVER, POSTGRESQL, SQLITE, OTHER;

        public static Dialect of(String databaseType) {
            if (databaseType == null) return OTHER;
            String s = databaseType.toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
            if (s.contains("SQLSERVER") || s.contains("MSSQL")) return SQLSERVER;
            if (s.contains("POSTGRE")) return POSTGRESQL;
            if (s.contains("SQLITE")) return SQLITE;
            return OTHER;
        }
    }

    public record Message(Kind kind, String text) {
        public enum Kind { INFO, WARNING, ERROR }
    }

    /** One batch (SQL Server) or statement (others) and the 1-based line it starts on in the script. */
    public record Piece(String sql, int startLine) {
    }

    /**
     * @param results         every result set, in order
     * @param errorCount      failed batches / statements
     * @param firstError      message of the first failure (null when none)
     * @param lastUpdateCount last update count reported (-1 when none)
     * @param piecesRun       batches / statements sent to the server
     * @param piecesTotal     batches / statements in the script
     */
    public record Outcome(List<QueryResult> results, List<Message> messages, int errorCount, String firstError,
                          int lastUpdateCount, int totalRows, boolean cancelled, boolean timedOut,
                          int piecesRun, int piecesTotal) {
    }

    /** Cancels the script from another thread. */
    public static final class Handle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<Statement> statement = new AtomicReference<>();

        public boolean cancel() {
            cancelled.set(true);
            Statement st = statement.get();
            if (st != null) {
                try {
                    st.cancel();
                } catch (SQLException ignored) {
                    // statement just finished / closed: the flag stops the next piece
                }
            }
            return true;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private static final Pattern GO_LINE = Pattern.compile("^[ \\t]*GO(?:[ \\t]+\\d+)?[ \\t]*;?[ \\t]*(?:--.*)?$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private ScriptRunner() {
    }

    /** Splits {@code sql} into what is sent to the server one by one. Blank / comment-only pieces are dropped. */
    public static List<Piece> pieces(String sql, Dialect dialect) {
        List<Piece> out = new ArrayList<>();
        if (sql == null || sql.isBlank()) return out;
        if (dialect == Dialect.SQLSERVER) {
            Matcher m = GO_LINE.matcher(sql);
            int start = 0;
            while (m.find()) {
                addPiece(out, sql, start, m.start());
                start = m.end();
            }
            addPiece(out, sql, start, sql.length());
        } else {
            for (SqlStatementSplitter.Part p : SqlStatementSplitter.split(sql)) {
                if (!isOnlyComments(p.text())) out.add(new Piece(p.text(), p.startLine()));
            }
        }
        return out;
    }

    private static void addPiece(List<Piece> out, String sql, int from, int to) {
        String text = sql.substring(from, to);
        if (isOnlyComments(text)) return;
        out.add(new Piece(text.strip(), lineOf(sql, from + leadingBlank(text))));
    }

    private static int leadingBlank(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static int lineOf(String sql, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < sql.length(); i++) {
            if (sql.charAt(i) == '\n') line++;
        }
        return line;
    }

    static boolean isOnlyComments(String s) {
        if (s == null) return true;
        String t = s.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ").replace(";", " ");
        return t.isBlank();
    }

    /**
     * D4: receives rows while they are read (LLTool streams 100 rows, then 500 at a time), so the grid shows
     * the first rows at once and fills up while the server is still sending. Called on the reading thread.
     */
    public interface RowSink {
        /** A new result set starts ({@code index} counts from 0 over the whole script). */
        void resultStarted(int index, String sql, List<QueryResultColumn> columns);

        /** The next rows of result set {@code index}, in order. */
        void rows(int index, List<QueryResultRow> batch);
    }

    /** First batch is small so something shows at once; later batches are larger (fewer UI updates). */
    static final int FIRST_BATCH = 200;
    static final int NEXT_BATCH = 2000;
    static final long BATCH_MAX_WAIT_MS = 250;

    public static Outcome run(Connection conn, String sql, Dialect dialect, QueryOptions options, Handle handle) {
        return run(conn, sql, dialect, options, handle, null);
    }

    /** PostgreSQL sends the whole result before the first row unless a cursor is used (fetch size + no autocommit). */
    private static boolean pgCursorFriendly(String sql) {
        String s = sql.stripLeading().toUpperCase(java.util.Locale.ROOT);
        while (s.startsWith("--") || s.startsWith("/*")) {
            int end = s.startsWith("--") ? s.indexOf('\n') : s.indexOf("*/");
            if (end < 0) return false;
            s = s.substring(end + (s.startsWith("--") ? 1 : 2)).stripLeading();
        }
        return s.startsWith("SELECT") || s.startsWith("WITH") || s.startsWith("VALUES") || s.startsWith("TABLE ");
    }

    public static Outcome run(Connection conn, String sql, Dialect dialect, QueryOptions options, Handle handle, RowSink sink) {
        QueryOptions opts = options != null ? options : QueryOptions.legacy();
        Handle h = handle != null ? handle : new Handle();
        List<Piece> pieces = pieces(sql, dialect);
        List<QueryResult> results = new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        int errors = 0;
        String firstError = null;
        int lastUpdate = -1;
        int totalRows = 0;
        boolean cancelled = false;
        boolean timedOut = false;
        int run = 0;
        long scriptStart = System.currentTimeMillis();
        final int cap = opts.effectiveRowCap();
        boolean many = pieces.size() > 1;

        for (Piece piece : pieces) {
            if (h.isCancelled()) {
                cancelled = true;
                break;
            }
            run++;
            Statement st = null;
            int setNo = 0;
            boolean restoreAutoCommit = false;
            boolean failed = false;
            try {
                if (sink != null && dialect == Dialect.POSTGRESQL && pgCursorFriendly(piece.sql()) && conn.getAutoCommit()) {
                    conn.setAutoCommit(false);
                    restoreAutoCommit = true;
                }
                st = conn.createStatement();
                h.statement.set(st);
                if (sink != null) {
                    try {
                        st.setFetchSize(1000);
                    } catch (SQLException ignored) {
                    }
                }
                try {
                    if (!opts.isUnlimited()) st.setMaxRows(cap + 1);
                    if (opts.timeoutSeconds() > 0) st.setQueryTimeout(opts.timeoutSeconds());
                } catch (SQLException ignored) {
                    // driver without setMaxRows / setQueryTimeout: the loop still enforces the cap
                }
                boolean isResultSet = st.execute(piece.sql());
                drain(st, messages);
                while (true) {
                    if (isResultSet) {
                        try (ResultSet rs = st.getResultSet()) {
                            ResultSetMetaData meta = rs.getMetaData();
                            int cols = meta.getColumnCount();
                            List<QueryResultColumn> columns = new ArrayList<>();
                            int[] sqlTypes = new int[cols + 1];
                            String[] typeNames = new String[cols + 1];
                            String detectedTableName = null;
                            boolean allSame = true;
                            for (int i = 1; i <= cols; i++) {
                                sqlTypes[i] = meta.getColumnType(i);
                                typeNames[i] = meta.getColumnTypeName(i);
                                String label = meta.getColumnLabel(i);
                                String colTable = null;
                                try {
                                    colTable = meta.getTableName(i);
                                } catch (Exception ignored) {}
                                columns.add(new QueryResultColumn(label != null && !label.isBlank() ? label : "(No column name)",
                                        meta.getColumnTypeName(i), colTable));
                                if (colTable != null && !colTable.isBlank()) {
                                    if (detectedTableName == null) {
                                        detectedTableName = colTable;
                                    } else if (!detectedTableName.equalsIgnoreCase(colTable)) {
                                        allSame = false;
                                    }
                                }
                            }
                            String sourceTable = (allSame && detectedTableName != null && !detectedTableName.isBlank()) ? detectedTableName : null;
                            List<QueryResultRow> rows = new ArrayList<>();
                            int rowNum = 1;
                            boolean truncated = false;
                            final int setIndex = results.size();
                            if (sink != null) sink.resultStarted(setIndex, piece.sql(), List.copyOf(columns));
                            List<QueryResultRow> batch = new ArrayList<>();
                            boolean firstSent = false;
                            long lastSent = System.currentTimeMillis();
                            while (rs.next()) {
                                if (rows.size() >= cap) {
                                    truncated = true;
                                    break;
                                }
                                List<Object> values = new ArrayList<>(cols);
                                for (int i = 1; i <= cols; i++) values.add(CellValues.read(rs, i, sqlTypes[i], typeNames[i], dialect));
                                QueryResultRow row = new QueryResultRow(rowNum++, values);
                                rows.add(row);
                                if (sink != null) {
                                    batch.add(row);
                                    long now = System.currentTimeMillis();
                                    if (batch.size() >= (firstSent ? NEXT_BATCH : FIRST_BATCH) || now - lastSent >= BATCH_MAX_WAIT_MS) {
                                        sink.rows(setIndex, List.copyOf(batch));
                                        batch.clear();
                                        firstSent = true;
                                        lastSent = now;
                                    }
                                }
                                if (h.isCancelled()) break;
                            }
                            if (sink != null && !batch.isEmpty()) sink.rows(setIndex, List.copyOf(batch));
                            setNo++;
                            totalRows += rows.size();
                            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                            results.add(new QueryResult(piece.sql(), columns, rows, System.currentTimeMillis() - scriptStart, ts, sourceTable));
                            messages.add(new Message(Message.Kind.INFO, "(" + rows.size() + " row(s) returned)"));
                            if (truncated) {
                                messages.add(new Message(Message.Kind.WARNING, String.format(
                                        "Kết quả #%d bị cắt ở %,d dòng (giới hạn dòng đang chọn). Chọn giới hạn lớn hơn ở thanh công cụ nếu cần thêm.",
                                        results.size(), cap)));
                            }
                        }
                    } else {
                        int uc = st.getUpdateCount();
                        if (uc == -1) break;
                        lastUpdate = uc;
                        messages.add(new Message(Message.Kind.INFO, "(" + uc + " row(s) affected)"));
                    }
                    drain(st, messages);
                    isResultSet = st.getMoreResults();
                    drain(st, messages);
                }
            } catch (SQLException e) {
                failed = true;
                if (st != null) drainQuietly(st, messages);
                if (h.isCancelled()) {
                    cancelled = true;
                    break;
                }
                if (isTimeout(e, opts)) {
                    timedOut = true;
                    break;
                }
                errors++;
                String msg = errorText(e, piece, many, dialect);
                if (firstError == null) firstError = msg;
                messages.add(new Message(Message.Kind.ERROR, msg));
                if (opts.stopOnError()) break;
            } finally {
                h.statement.set(null);
                if (st != null) {
                    try {
                        st.close();
                    } catch (Exception ignored) {
                    }
                }
                if (restoreAutoCommit) {
                    try {
                        if (failed) conn.rollback(); else conn.commit();
                    } catch (SQLException ignored) {
                    }
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException ignored) {
                    }
                }
            }
        }
        if (h.isCancelled()) cancelled = true;
        return new Outcome(results, messages, errors, firstError, lastUpdate, totalRows, cancelled, timedOut, run, pieces.size());
    }

    private static boolean isTimeout(SQLException e, QueryOptions opts) {
        if (opts.timeoutSeconds() <= 0) return false;
        if (e instanceof SQLTimeoutException) return true;
        String m = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
        String state = e.getSQLState() != null ? e.getSQLState() : "";
        return m.contains("timed out") || m.contains("timeout") || m.contains("cancel")
                || "57014".equals(state) || "HYT00".equals(state) || "HY008".equals(state);
    }

    private static String errorText(SQLException e, Piece piece, boolean many, Dialect dialect) {
        StringBuilder sb = new StringBuilder();
        if (many) {
            sb.append(dialect == Dialect.SQLSERVER ? "Batch" : "Câu lệnh").append(" từ dòng ").append(piece.startLine()).append(": ");
        }
        sb.append(e.getMessage() != null ? e.getMessage().strip() : e.toString());
        if (e.getSQLState() != null && !e.getSQLState().isBlank()) sb.append(" [SQLState: ").append(e.getSQLState()).append("]");
        if (e.getErrorCode() != 0) sb.append(" [ErrorCode: ").append(e.getErrorCode()).append("]");
        return sb.toString();
    }

    private static void drain(Statement st, List<Message> out) {
        try {
            for (String m : SqlWarningMessages.collect(st.getWarnings())) out.add(new Message(Message.Kind.INFO, m));
            st.clearWarnings();
        } catch (SQLException ignored) {
        }
    }

    private static void drainQuietly(Statement st, List<Message> out) {
        try {
            drain(st, out);
        } catch (RuntimeException ignored) {
        }
    }
}
