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
 * PostgreSQL Syntax Adapter:
 * <ul>
 *   <li>Runs EXPLAIN (VERBOSE) over dedicated read-only connection without executing data modifications.</li>
 *   <li>Bypasses DDL and procedural DO $$ blocks to structural checks to prevent execution side-effects.</li>
 *   <li>Extracts character positions (Position: &lt;num&gt;) and token markers (at or near "&lt;token&gt;").</li>
 * </ul>
 */
public final class PostgresSyntaxAdapter implements SyntaxAdapter {

    public static final PostgresSyntaxAdapter INSTANCE = new PostgresSyntaxAdapter();

    private static final String EXPLAIN_PREFIX = "EXPLAIN (VERBOSE) ";
    private static final int PREFIX_LEN = EXPLAIN_PREFIX.length();

    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "Position:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern NEAR_PATTERN = Pattern.compile(
            "(?:at or near|near)\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public PostgresSyntaxAdapter() {}

    @Override
    public String dialect() {
        return "postgresql";
    }

    @Override
    public List<SyntaxDiagnostic> validate(String sql, Connection connection, String database, String schema, boolean isDebug) throws SQLException {
        List<SyntaxDiagnostic> out = new ArrayList<>();
        if (sql == null || sql.isBlank()) return out;

        if (connection == null) {
            return StructuralSqlChecks.check(sql, "postgresql");
        }

        List<ScriptRunner.Piece> pieces = ScriptRunner.pieces(sql, ScriptRunner.Dialect.POSTGRESQL);
        if (pieces.isEmpty()) return out;

        try (Statement st = connection.createStatement()) {
            for (ScriptRunner.Piece piece : pieces) {
                String pieceSql = piece.sql().strip();
                if (pieceSql.isEmpty()) continue;

                if (isDdlOrUtility(pieceSql)) {
                    out.addAll(StructuralSqlChecks.check(pieceSql, "postgresql"));
                    continue;
                }

                try {
                    st.execute(EXPLAIN_PREFIX + pieceSql);
                } catch (SQLException e) {
                    if (isConnectionError(e)) {
                        throw e;
                    }
                    SyntaxDiagnostic diag = parseErrorDiagnostic(e, pieceSql, piece.startLine(), sql);
                    if (diag != null) {
                        out.add(diag);
                    }
                }
            }
        }

        return out;
    }

    private static boolean isDdlOrUtility(String sql) {
        String s = sql.toUpperCase(Locale.ROOT);
        return s.startsWith("CREATE ") || s.startsWith("ALTER ") || s.startsWith("DROP ")
                || s.startsWith("TRUNCATE ") || s.startsWith("GRANT ") || s.startsWith("REVOKE ")
                || s.startsWith("DO ") || s.startsWith("DO\n") || s.startsWith("DO\r")
                || s.startsWith("SET ") || s.startsWith("RESET ")
                || s.startsWith("SHOW ") || s.startsWith("BEGIN") || s.startsWith("COMMIT")
                || s.startsWith("ROLLBACK") || s.startsWith("SAVEPOINT");
    }

    private static boolean isConnectionError(SQLException e) {
        String state = e.getSQLState();
        if (state == null) return false;
        return state.startsWith("08") || "57P01".equals(state) || "57P02".equals(state) || "57P03".equals(state);
    }

    public static SyntaxDiagnostic parseErrorDiagnostic(SQLException e, String pieceSql, int startLine, String fullSql) {
        String msg = e.getMessage() != null ? e.getMessage().strip() : "Syntax error";
        String token = "";
        Matcher nearM = NEAR_PATTERN.matcher(msg);
        if (nearM.find()) {
            token = nearM.group(1);
        }

        int targetLine = startLine;
        int targetCol = 1;
        int targetEndCol = 2;

        Matcher posM = POSITION_PATTERN.matcher(msg);
        if (posM.find()) {
            try {
                int rawPos = Integer.parseInt(posM.group(1));
                int relPos = rawPos - PREFIX_LEN;
                if (relPos >= 1 && relPos <= pieceSql.length()) {
                    int offset = relPos - 1;
                    int subLine = 0;
                    int lastNl = -1;
                    for (int i = 0; i < offset; i++) {
                        if (pieceSql.charAt(i) == '\n') {
                            subLine++;
                            lastNl = i;
                        }
                    }
                    targetLine = startLine + subLine;
                    targetCol = (lastNl == -1) ? (offset + 1) : (offset - lastNl);
                    targetEndCol = targetCol + Math.max(1, token.length());
                }
            } catch (NumberFormatException ignored) {}
        }

        if (targetCol == 1 && !token.isEmpty() && pieceSql.contains(token)) {
            int idx = pieceSql.indexOf(token);
            int subLine = 0;
            int lastNl = -1;
            for (int i = 0; i < idx; i++) {
                if (pieceSql.charAt(i) == '\n') {
                    subLine++;
                    lastNl = i;
                }
            }
            targetLine = startLine + subLine;
            targetCol = (lastNl == -1) ? (idx + 1) : (idx - lastNl);
            targetEndCol = targetCol + token.length();
        }

        SyntaxDiagnostic rawDiag = new SyntaxDiagnostic(
                SyntaxDiagnostic.Severity.ERROR,
                "SQL_SYNTAX_ERROR",
                "postgresql",
                token,
                targetLine,
                targetCol,
                targetLine,
                targetEndCol,
                msg
        );

        return DiagnosticNormalizer.normalize(rawDiag, fullSql);
    }
}

