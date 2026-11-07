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
 * Syntax check for SQL Server with {@code SET PARSEONLY ON}: the server parses every batch and reports syntax
 * errors without compiling or running anything (it also does not check that tables / columns exist).
 *
 * <p>The script is split at {@code GO} lines like a run ({@link ScriptRunner#pieces}). The JDBC driver gives the
 * message but not the error's line number, so the position is found from the token the message names
 * ("Incorrect syntax near 'x'"): exact when the token occurs once in the batch, otherwise its first occurrence.
 * When the message names no token the first line of the batch is marked.
 *
 * <p>Use a connection of its own: while {@code PARSEONLY} is on, nothing sent on it is executed.
 */
public final class SqlServerSyntaxChecker {

    private static final Pattern NEAR = Pattern.compile(
            "(?:near (?:the keyword )?|after the character string )'((?:[^']|'')*)'", Pattern.CASE_INSENSITIVE);

    private SqlServerSyntaxChecker() {
    }

    /** @throws SQLException when the connection itself fails (SQLState 08xxx or the SET statements fail) */
    public static List<SyntaxDiagnostic> check(Connection connection, String sql) throws SQLException {
        List<SyntaxDiagnostic> out = new ArrayList<>();
        List<ScriptRunner.Piece> pieces = ScriptRunner.pieces(sql, ScriptRunner.Dialect.SQLSERVER);
        if (pieces.isEmpty()) return out;
        String[] lines = sql.split("\n", -1);
        try (Statement st = connection.createStatement()) {
            st.execute("SET PARSEONLY ON");
            try {
                for (ScriptRunner.Piece piece : pieces) {
                    try {
                        st.execute(piece.sql());
                    } catch (SQLException e) {
                        if (isConnectionError(e)) throw e;
                        int colOffset = 0;
                        if (piece.startLine() >= 1 && piece.startLine() <= lines.length) {
                            colOffset = firstNonBlank(lines[piece.startLine() - 1]);
                        }
                        for (SQLException x = e; x != null; x = x.getNextException()) {
                            if (x.getMessage() == null || isNoise(x.getMessage())) continue;
                            out.add(diagnosticFor(x.getMessage(), piece.sql(), piece.startLine(), colOffset));
                        }
                    }
                }
            } finally {
                try {
                    st.execute("SET PARSEONLY OFF");
                } catch (SQLException e) {
                    throw new SQLException("SET PARSEONLY OFF failed", "08S01", e);   // caller drops the connection
                }
            }
        }
        return out;
    }

    static boolean isConnectionError(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("08");
    }

    private static boolean isNoise(String message) {
        return message.toLowerCase(Locale.ROOT).startsWith("the statement did not return a result set");
    }

    private static int firstNonBlank(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        return i;
    }

    /**
     * Maps a server message to a position in the script.
     *
     * @param pieceSql  the batch text as sent (stripped)
     * @param startLine script line (1-based) the batch's first character is on
     * @param colOffset 0-based column of that first character in its line
     */
    public static SyntaxDiagnostic diagnosticFor(String message, String pieceSql, int startLine, int colOffset) {
        String msg = message.strip();
        Matcher m = NEAR.matcher(msg);
        if (m.find()) {
            String token = m.group(1).replace("''", "'");
            int idx = token.isEmpty() ? -1 : indexOfToken(pieceSql, token);
            if (idx >= 0) {
                return at(pieceSql, startLine, colOffset, idx, token.length(), msg);
            }
        }
        int eol = pieceSql.indexOf('\n');
        int len = eol < 0 ? pieceSql.length() : eol;
        return at(pieceSql, startLine, colOffset, 0, Math.max(1, len), msg);
    }

    /** First occurrence of {@code token} (case-insensitive); a word token must not sit inside a longer word. */
    private static int indexOfToken(String text, String token) {
        String t = text.toLowerCase(Locale.ROOT);
        String k = token.toLowerCase(Locale.ROOT);
        boolean word = Character.isLetterOrDigit(k.charAt(0)) || k.charAt(0) == '_';
        int from = 0;
        while (true) {
            int i = t.indexOf(k, from);
            if (i < 0) return -1;
            if (!word) return i;
            boolean leftOk = i == 0 || !isWordChar(t.charAt(i - 1));
            int end = i + k.length();
            boolean rightOk = end >= t.length() || !isWordChar(t.charAt(end)) || !isWordChar(k.charAt(k.length() - 1));
            if (leftOk && rightOk) return i;
            from = i + 1;
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '#' || c == '$';
    }

    private static SyntaxDiagnostic at(String pieceSql, int startLine, int colOffset, int idx, int len, String msg) {
        int line = startLine;
        int lineStart = 0;
        for (int i = 0; i < idx; i++) {
            if (pieceSql.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int col = idx - lineStart + 1 + (line == startLine ? colOffset : 0);
        // the marked text may span lines: end at the token's last line
        int endLine = line;
        int endLineStart = lineStart;
        int end = Math.min(pieceSql.length(), idx + len);
        for (int i = idx; i < end; i++) {
            if (pieceSql.charAt(i) == '\n') {
                endLine++;
                endLineStart = i + 1;
            }
        }
        int endCol = end - endLineStart + 1 + (endLine == startLine ? colOffset : 0);
        return new SyntaxDiagnostic(line, col, endLine, Math.max(endCol, col + (endLine == line ? 1 : 0)), msg);
    }
}
