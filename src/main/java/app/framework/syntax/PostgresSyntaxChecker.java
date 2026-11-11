package app.framework.syntax;

import app.framework.query.ScriptRunner;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax check for PostgreSQL using {@code EXPLAIN (VERBOSE) <sql>}:
 * PostgreSQL validates syntax, query grammar, and relations during plan generation
 * without actually executing data modification or transaction commits.
 *
 * <p>Statements are split into pieces using {@link ScriptRunner#pieces}.
 * Errors from PostgreSQL return exact 1-based character positions (relative to the
 * query executed) and token markers (e.g. {@code syntax error at or near "FROM"}),
 * which this checker maps to 1-based line and column coordinates for Monaco markers.
 */
public final class PostgresSyntaxChecker {

    private static final String EXPLAIN_PREFIX = "EXPLAIN (VERBOSE) ";
    private static final int PREFIX_LEN = EXPLAIN_PREFIX.length();

    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "Position:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern NEAR_PATTERN = Pattern.compile(
            "(?:at or near|near)\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private PostgresSyntaxChecker() {
    }

    /**
     * Checks the SQL script for syntax errors using an isolated connection.
     *
     * @param connection dedicated PostgreSQL JDBC connection
     * @param sql        full SQL script
     * @return list of syntax diagnostics
     * @throws SQLException if a connection failure occurs (SQLState 08xxx)
     */
    public static List<SyntaxDiagnostic> check(Connection connection, String sql) throws SQLException {
        List<SyntaxDiagnostic> out = new ArrayList<>();
        if (sql == null || sql.isBlank()) return out;

        List<ScriptRunner.Piece> pieces = ScriptRunner.pieces(sql, ScriptRunner.Dialect.POSTGRESQL);
        if (pieces.isEmpty()) return out;

        try (Statement st = connection.createStatement()) {
            for (ScriptRunner.Piece piece : pieces) {
                String pieceSql = piece.sql().strip();
                if (pieceSql.isEmpty()) continue;

                // Certain DDL and control statements are not supported by EXPLAIN
                if (isDdlOrUtility(pieceSql)) {
                    out.addAll(OfflineSqlSyntaxChecker.check(pieceSql));
                    continue;
                }

                try {
                    st.execute(EXPLAIN_PREFIX + pieceSql);
                } catch (SQLException e) {
                    if (isConnectionError(e)) {
                        throw e;
                    }
                    SyntaxDiagnostic diag = diagnosticFor(e, pieceSql, piece.startLine());
                    if (diag != null) {
                        out.add(diag);
                    }
                }
            }
        }

        return out;
    }

    static boolean isConnectionError(SQLException e) {
        String state = e.getSQLState();
        if (state == null) return false;
        return state.startsWith("08") || "57P01".equals(state) || "57P02".equals(state) || "57P03".equals(state);
    }

    private static boolean isDdlOrUtility(String sql) {
        String s = sql.toUpperCase(Locale.ROOT);
        return s.startsWith("CREATE ") || s.startsWith("ALTER ") || s.startsWith("DROP ")
                || s.startsWith("TRUNCATE ") || s.startsWith("GRANT ") || s.startsWith("REVOKE ")
                || s.startsWith("DO ") || s.startsWith("SET ") || s.startsWith("RESET ")
                || s.startsWith("SHOW ") || s.startsWith("BEGIN") || s.startsWith("COMMIT")
                || s.startsWith("ROLLBACK") || s.startsWith("SAVEPOINT");
    }

    /**
     * Maps a PostgreSQL SQLException to a line and column position in the script.
     */
    public static SyntaxDiagnostic diagnosticFor(SQLException e, String pieceSql, int startLine) {
        String msg = e.getMessage() != null ? e.getMessage().strip() : "Syntax error";
        int pos = extractPosition(e, msg);

        String token = null;
        Matcher nearMatcher = NEAR_PATTERN.matcher(msg);
        if (nearMatcher.find()) {
            token = nearMatcher.group(1);
        }

        // Clean user-friendly message (first line of error message)
        String cleanMsg = msg;
        int newlineIdx = cleanMsg.indexOf('\n');
        if (newlineIdx > 0) {
            cleanMsg = cleanMsg.substring(0, newlineIdx).strip();
        }
        if (cleanMsg.startsWith("ERROR: ")) {
            cleanMsg = cleanMsg.substring(7).strip();
        }

        if (pos > 0) {
            int charIdxInPiece = pos - 1 - PREFIX_LEN;
            if (charIdxInPiece >= 0 && charIdxInPiece < pieceSql.length()) {
                int tokenLen = token != null ? token.length() : 1;
                return at(pieceSql, startLine, charIdxInPiece, tokenLen, cleanMsg);
            }
        }

        // If no character position could be resolved from PostgreSQL error message,
        // search for the token itself in the piece SQL
        if (token != null && !token.isEmpty()) {
            int idx = pieceSql.toLowerCase(Locale.ROOT).indexOf(token.toLowerCase(Locale.ROOT));
            if (idx >= 0) {
                return at(pieceSql, startLine, idx, token.length(), cleanMsg);
            }
        }

        // Fallback to start of the statement
        int eol = pieceSql.indexOf('\n');
        int len = eol < 0 ? pieceSql.length() : eol;
        return at(pieceSql, startLine, 0, Math.max(1, len), cleanMsg);
    }

    private static int extractPosition(SQLException e, String message) {
        // Try getting position from PostgreSQL driver ServerErrorMessage via reflection/interface
        try {
            var method = e.getClass().getMethod("getServerErrorMessage");
            Object sem = method.invoke(e);
            if (sem != null) {
                var posMethod = sem.getClass().getMethod("getPosition");
                Object p = posMethod.invoke(sem);
                if (p instanceof Integer posInt && posInt > 0) {
                    return posInt;
                }
            }
        } catch (Exception ignored) {
            // Fallback to regex below
        }

        Matcher m = POSITION_PATTERN.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private static SyntaxDiagnostic at(String pieceSql, int startLine, int idx, int len, String msg) {
        int line = startLine;
        int lineStart = 0;
        int max = Math.min(pieceSql.length(), idx);
        for (int i = 0; i < max; i++) {
            if (pieceSql.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int col = Math.max(1, idx - lineStart + 1);

        int endLine = line;
        int endLineStart = lineStart;
        int end = Math.min(pieceSql.length(), idx + len);
        for (int i = idx; i < end; i++) {
            if (pieceSql.charAt(i) == '\n') {
                endLine++;
                endLineStart = i + 1;
            }
        }
        int endCol = Math.max(col + (endLine == line ? 1 : 0), end - endLineStart + 1);
        return new SyntaxDiagnostic(line, col, endLine, endCol, msg);
    }
}

