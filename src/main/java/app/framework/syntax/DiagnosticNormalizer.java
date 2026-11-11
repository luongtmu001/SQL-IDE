package app.framework.syntax;

import java.util.regex.Pattern;

/**
 * Diagnostic normalizer for SQL syntax check:
 * <ul>
 *   <li>Cleans noisy ODBC/JDBC prefixes and Msg headers from database drivers.</li>
 *   <li>Ensures coordinates are valid 1-based line and column numbers.</li>
 *   <li>Calculates non-zero token length and adjusts token boundaries on the line.</li>
 * </ul>
 */
public final class DiagnosticNormalizer {

    private static final Pattern ODBC_DRIVER_PREFIX = Pattern.compile(
            "\\[Microsoft\\]\\[[^\\]]+\\](?:\\[[^\\]]+\\])?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DRIVER_PREFIX = Pattern.compile(
            "(?:psycopg2|org\\.postgresql|com\\.microsoft\\.sqlserver\\.jdbc)[\\w\\.]*:\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERROR_CODES_PAREN = Pattern.compile("\\(\\d+\\)\\s*\\([^\\)]+\\)|\\(\\d+\\)");
    private static final Pattern MSG_LINE_HEADER = Pattern.compile(
            "Msg\\s+\\d+,\\s*Level\\s+\\d+,\\s*State\\s+\\d+,\\s*(?:Procedure\\s+[^\\s,]+,\\s*)?Line\\s+\\d+\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_HEADER = Pattern.compile("Line\\s+\\d+:\\s*", Pattern.CASE_INSENSITIVE);

    private DiagnosticNormalizer() {}

    public static String cleanMessage(String raw) {
        if (raw == null || raw.isBlank()) return "Syntax error";
        String msg = raw.strip();
        msg = ODBC_DRIVER_PREFIX.matcher(msg).replaceAll("");
        msg = DRIVER_PREFIX.matcher(msg).replaceAll("");
        msg = ERROR_CODES_PAREN.matcher(msg).replaceAll("");
        msg = MSG_LINE_HEADER.matcher(msg).replaceAll("");
        msg = LINE_HEADER.matcher(msg).replaceAll("");
        msg = msg.replaceAll(";\\s*$", "").replaceAll("^[:;\\s]+", "").strip();
        return msg.isEmpty() ? raw.strip() : msg;
    }

    public static SyntaxDiagnostic normalize(SyntaxDiagnostic diag, String sql) {
        if (diag == null) return null;
        int line = Math.max(1, diag.line());
        int col = Math.max(1, diag.column());
        String token = diag.token() != null ? diag.token().strip() : "";
        int endCol = diag.endColumn();
        if (endCol <= col) {
            endCol = col + Math.max(1, token.length());
        }

        // Align column if token appears elsewhere on line
        if (sql != null && !sql.isEmpty() && !token.isEmpty()) {
            String[] lines = sql.split("\r?\n", -1);
            if (line <= lines.length) {
                String lineStr = lines[line - 1];
                if (lineStr.contains(token)) {
                    int startIdx = col - 1;
                    if (startIdx < 0 || startIdx + token.length() > lineStr.length()
                            || !lineStr.substring(startIdx, startIdx + token.length()).equalsIgnoreCase(token)) {
                        int actualIdx = lineStr.indexOf(token);
                        if (actualIdx >= 0) {
                            col = actualIdx + 1;
                            endCol = col + token.length();
                        }
                    }
                }
            }
        }

        String cleanedMsg = cleanMessage(diag.message());
        return new SyntaxDiagnostic(
                diag.severity() != null ? diag.severity() : SyntaxDiagnostic.Severity.ERROR,
                diag.code() != null && !diag.code().isBlank() ? diag.code() : "SQL_SYNTAX_ERROR",
                diag.source() != null ? diag.source() : "sql",
                token,
                line,
                col,
                line,
                endCol,
                cleanedMsg
        );
    }
}

