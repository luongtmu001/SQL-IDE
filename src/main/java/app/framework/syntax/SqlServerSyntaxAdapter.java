package app.framework.syntax;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL Server (T-SQL) Syntax Adapter:
 * <ul>
 *   <li>Splits scripts into batches by standalone GO statements (conforming to batches_go.json).</li>
 *   <li>Runs SET PARSEONLY ON over dedicated read-only connection.</li>
 *   <li>Extracts exact line numbers from server error messages (Line &lt;num&gt;).</li>
 *   <li>Maps token positions using word-boundary matching and line coordinates.</li>
 * </ul>
 */
public final class SqlServerSyntaxAdapter implements SyntaxAdapter {

    public record SqlBatch(String text, int startLine, int lineCount) {}

    public static final SqlServerSyntaxAdapter INSTANCE = new SqlServerSyntaxAdapter();

    private static final Pattern GO_PATTERN = Pattern.compile(
            "^\\s*GO(?:\\s+\\d+|\\s+--.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern NEAR_PATTERN = Pattern.compile(
            "(?:near (?:the keyword )?|after the character string )'((?:[^']|'')*)'", Pattern.CASE_INSENSITIVE);

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "(?:Line|line)\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROC_LINE_PATTERN = Pattern.compile(
            "Procedure\\s+([^,\\s]+),\\s*Line\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    public SqlServerSyntaxAdapter() {}

    @Override
    public String dialect() {
        return "sqlserver";
    }

    @Override
    public List<SyntaxDiagnostic> validate(String sql, Connection connection, String database, String schema, boolean isDebug) throws SQLException {
        List<SyntaxDiagnostic> out = new ArrayList<>();
        if (sql == null || sql.isBlank()) return out;

        List<SqlBatch> batches = splitSqlServerBatches(sql);
        if (batches.isEmpty()) return out;

        String[] allLines = sql.split("\r?\n", -1);

        try (Statement st = connection.createStatement()) {
            st.execute("SET PARSEONLY ON");
            try {
                for (SqlBatch batch : batches) {
                    try {
                        st.execute(batch.text());
                    } catch (SQLException e) {
                        if (isConnectionError(e)) throw e;

                        for (SQLException x = e; x != null; x = x.getNextException()) {
                            String msg = x.getMessage();
                            if (msg == null || isNoise(msg)) continue;

                            SyntaxDiagnostic diag = parseErrorDiagnostic(msg, batch, allLines, sql);
                            if (diag != null) {
                                out.add(diag);
                            }
                        }
                    }
                }
            } finally {
                try {
                    st.execute("SET PARSEONLY OFF");
                } catch (SQLException e) {
                    throw new SQLException("SET PARSEONLY OFF failed", "08S01", e);
                }
            }
        }

        return out;
    }

    public static List<SqlBatch> splitSqlServerBatches(String sql) {
        List<SqlBatch> batches = new ArrayList<>();
        if (sql == null || sql.isEmpty()) return batches;

        String[] lines = sql.split("\n", -1);
        List<String> currentLines = new ArrayList<>();
        int currentStartLine = 1;

        boolean inBlockComment = false;
        boolean inSingleQuote = false;

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            int lineNo = lineIdx + 1;
            String line = lines[lineIdx];
            String stripped = line.strip();

            boolean startsInState = inBlockComment || inSingleQuote;

            // Character scan to update comment/quote states
            int i = 0;
            int n = line.length();
            while (i < n) {
                char ch = line.charAt(i);
                char nextCh = (i + 1 < n) ? line.charAt(i + 1) : '\0';

                if (inBlockComment) {
                    if (ch == '*' && nextCh == '/') {
                        inBlockComment = false;
                        i += 2;
                        continue;
                    }
                    i++;
                } else if (inSingleQuote) {
                    if (ch == '\'') {
                        if (nextCh == '\'') {
                            i += 2; // escaped quote ''
                            continue;
                        }
                        inSingleQuote = false;
                        i++;
                        continue;
                    }
                    i++;
                } else {
                    if (ch == '-' && nextCh == '-') {
                        break; // rest of line is line comment
                    } else if (ch == '/' && nextCh == '*') {
                        inBlockComment = true;
                        i += 2;
                        continue;
                    } else if (ch == '\'') {
                        inSingleQuote = true;
                        i++;
                        continue;
                    }
                    i++;
                }
            }

            // Check standalone GO
            if (!startsInState && !inBlockComment && !inSingleQuote && GO_PATTERN.matcher(stripped).matches()) {
                if (!currentLines.isEmpty()) {
                    String batchText = String.join("\n", currentLines);
                    batches.add(new SqlBatch(batchText, currentStartLine, currentLines.size()));
                    currentLines.clear();
                }
                currentStartLine = lineNo + 1;
            } else {
                if (currentLines.isEmpty() && stripped.isEmpty()) {
                    currentStartLine = lineNo + 1;
                } else {
                    currentLines.add(line);
                }
            }
        }

        if (!currentLines.isEmpty()) {
            String batchText = String.join("\n", currentLines);
            if (!batchText.isBlank()) {
                batches.add(new SqlBatch(batchText, currentStartLine, currentLines.size()));
            }
        }

        return batches;
    }

    private static SyntaxDiagnostic parseErrorDiagnostic(String rawMsg, SqlBatch batch, String[] allLines, String fullSql) {
        String msg = rawMsg.strip();

        // 1. Extract error line in batch
        int batchLine = -1;
        Matcher procM = PROC_LINE_PATTERN.matcher(msg);
        if (procM.find()) {
            String procName = procM.group(1);
            int pLine = Integer.parseInt(procM.group(2));
            // Find procedure in routine detector
            var routines = ProcBodyDetector.extractRoutines(fullSql, "sqlserver");
            for (var r : routines) {
                if (r.cleanName().equalsIgnoreCase(procName) || r.name().equalsIgnoreCase(procName)) {
                    batchLine = (r.startLine() - batch.startLine()) + pLine;
                    break;
                }
            }
            if (batchLine == -1) batchLine = pLine;
        } else {
            Matcher lineM = LINE_PATTERN.matcher(msg);
            if (lineM.find()) {
                batchLine = Integer.parseInt(lineM.group(1));
            }
        }

        int targetLine = (batchLine > 0) ? (batch.startLine() + batchLine - 1) : batch.startLine();
        if (targetLine < 1) targetLine = 1;
        if (targetLine > allLines.length) targetLine = allLines.length;

        // 2. Extract token from message
        String token = "";
        Matcher nearM = NEAR_PATTERN.matcher(msg);
        if (nearM.find()) {
            token = nearM.group(1).replace("''", "'");
        }

        // 3. Compute column on targetLine
        String lineText = (targetLine <= allLines.length) ? allLines[targetLine - 1] : "";
        int[] pos = computeTokenPosition(lineText, token);
        int startCol = pos[0];
        int endCol = pos[1];

        SyntaxDiagnostic rawDiag = new SyntaxDiagnostic(
                SyntaxDiagnostic.Severity.ERROR,
                "SQL_SYNTAX_ERROR",
                "sqlserver",
                token,
                targetLine,
                startCol,
                targetLine,
                endCol,
                msg
        );

        return DiagnosticNormalizer.normalize(rawDiag, fullSql);
    }

    public static int[] computeTokenPosition(String lineText, String token) {
        if (lineText == null || lineText.isEmpty()) {
            return new int[]{1, 2};
        }

        if (token != null && !token.isEmpty() && lineText.contains(token)) {
            // Match exact word boundary if alphanumeric
            if (token.matches("^[a-zA-Z0-9_#$]+$")) {
                Pattern p = Pattern.compile("\\b" + Pattern.quote(token) + "\\b", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(lineText);
                if (m.find()) {
                    return new int[]{m.start() + 1, m.end() + 1};
                }
            }
            int idx = lineText.indexOf(token);
            if (idx >= 0) {
                return new int[]{idx + 1, idx + 1 + token.length()};
            }
        }

        // Fallback: first non-blank token on line
        int start = 0;
        while (start < lineText.length() && Character.isWhitespace(lineText.charAt(start))) {
            start++;
        }
        if (start < lineText.length()) {
            int end = start;
            while (end < lineText.length() && !Character.isWhitespace(lineText.charAt(end))) {
                end++;
            }
            return new int[]{start + 1, Math.max(start + 2, end + 1)};
        }

        return new int[]{1, Math.max(2, lineText.length() + 1)};
    }

    private static boolean isConnectionError(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("08");
    }

    private static boolean isNoise(String message) {
        return message.toLowerCase(Locale.ROOT).startsWith("the statement did not return a result set");
    }
}

