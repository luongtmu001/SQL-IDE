package app.framework.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Service for detecting fatal / dangerous SQL actions (DELETE/UPDATE without WHERE, TRUNCATE).
 * Fully compliant with SSMSBoost Fatal Actions Guard rules.
 * 
 * IMPORTANT: Recognizes and exempts temporary tables (#temp, ##temp) and
 * table variables (@tableVar) from warnings, ensuring developer productivity.
 */
public class SqlProtectionService {

    private static final SqlProtectionService INSTANCE = new SqlProtectionService();

    private static final Set<String> STATEMENT_STARTERS = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "CREATE", "ALTER", "DROP",
            "TRUNCATE", "DECLARE", "EXEC", "EXECUTE", "COMMIT", "ROLLBACK",
            "GRANT", "REVOKE", "USE", "RETURN", "IF", "WHILE", "PRINT"
    );

    private static final Set<String> PRECEDING_IGNORE = Set.of(
            "ON", "INSTEAD", "BEFORE", "AFTER", "OF", "FOR", "THEN"
    );

    public record DangerousActionIssue(
            String actionType,
            String targetTable,
            String message,
            int line,
            int col
    ) {}

    public static SqlProtectionService getInstance() {
        return INSTANCE;
    }

    private SqlProtectionService() {
    }

    /**
     * Analyzes SQL text and returns dangerous actions that target permanent tables.
     * Temporary tables (#, ##) and table variables (@) are explicitly exempt.
     */
    public List<DangerousActionIssue> detectDangerousActions(String sqlText) {
        if (sqlText == null || sqlText.isBlank()) {
            return List.of();
        }

        List<Token> allTokens = tokenize(sqlText);
        List<Token> tokens = allTokens.stream()
                .filter(t -> t.type != TokenType.COMMENT)
                .toList();

        List<DangerousActionIssue> issues = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token tok = tokens.get(i);
            if (tok.type != TokenType.WORD) continue;

            String word = tok.value.toUpperCase(Locale.ROOT);
            Token prevTok = i > 0 ? tokens.get(i - 1) : null;
            Token nextTok = i + 1 < tokens.size() ? tokens.get(i + 1) : null;

            // ─────────────────────────────────────────────────────────────
            // 1. TRUNCATE statement
            // ─────────────────────────────────────────────────────────────
            if ("TRUNCATE".equals(word)) {
                // Next word should be TABLE
                int tableIdx = i + 1;
                if (nextTok != null && "TABLE".equalsIgnoreCase(nextTok.value)) {
                    tableIdx = i + 2;
                }
                String targetTable = extractTargetTable(tokens, tableIdx);
                if (!isTemporaryOrVariableTable(targetTable)) {
                    issues.add(new DangerousActionIssue(
                            "TRUNCATE",
                            targetTable,
                            "Lệnh TRUNCATE TABLE trên bảng: " + (targetTable.isEmpty() ? "(không xác định)" : targetTable),
                            tok.line,
                            tok.col
                    ));
                }
                continue;
            }

            // ─────────────────────────────────────────────────────────────
            // 2. DELETE statement
            // ─────────────────────────────────────────────────────────────
            if ("DELETE".equals(word)) {
                // Ignore if preceded by ON (ON DELETE CASCADE), INSTEAD OF, BEFORE, AFTER
                if (prevTok != null && prevTok.type == TokenType.WORD
                        && PRECEDING_IGNORE.contains(prevTok.value.toUpperCase(Locale.ROOT))) {
                    continue;
                }
                // Ignore function-like DELETE(...)
                if (nextTok != null && "(".equals(nextTok.value)) {
                    continue;
                }

                // Extract target table
                int scanStart = i + 1;
                if (nextTok != null && "TOP".equalsIgnoreCase(nextTok.value)) {
                    // skip TOP (n)
                    scanStart = skipParentheses(tokens, i + 2);
                }
                if (scanStart < tokens.size() && "FROM".equalsIgnoreCase(tokens.get(scanStart).value)) {
                    scanStart++;
                }
                String targetTable = extractTargetTable(tokens, scanStart);

                // Check if target is temporary table or variable
                if (isTemporaryOrVariableTable(targetTable)) {
                    continue; // EXEMPT
                }

                // Scan forward to see if statement has a WHERE clause
                if (!hasWhereClauseInStatement(tokens, i + 1)) {
                    issues.add(new DangerousActionIssue(
                            "DELETE",
                            targetTable,
                            "Lệnh DELETE không có điều kiện WHERE trên bảng: " + (targetTable.isEmpty() ? "(toàn bộ bảng)" : targetTable),
                            tok.line,
                            tok.col
                    ));
                }
                continue;
            }

            // ─────────────────────────────────────────────────────────────
            // 3. UPDATE statement
            // ─────────────────────────────────────────────────────────────
            if ("UPDATE".equals(word)) {
                // Ignore if preceded by ON, OF, FOR, THEN
                if (prevTok != null && prevTok.type == TokenType.WORD
                        && PRECEDING_IGNORE.contains(prevTok.value.toUpperCase(Locale.ROOT))) {
                    continue;
                }
                // Ignore UPDATE STATISTICS
                if (nextTok != null && "STATISTICS".equalsIgnoreCase(nextTok.value)) {
                    continue;
                }
                // Ignore function-like UPDATE(col) in triggers
                if (nextTok != null && "(".equals(nextTok.value)) {
                    continue;
                }

                // Extract target table
                int scanStart = i + 1;
                if (nextTok != null && "TOP".equalsIgnoreCase(nextTok.value)) {
                    scanStart = skipParentheses(tokens, i + 2);
                }
                String targetTable = extractTargetTable(tokens, scanStart);

                // Check if target is temporary table or variable
                if (isTemporaryOrVariableTable(targetTable)) {
                    continue; // EXEMPT
                }

                // Scan forward to see if statement has a WHERE clause
                if (!hasWhereClauseInStatement(tokens, i + 1)) {
                    issues.add(new DangerousActionIssue(
                            "UPDATE",
                            targetTable,
                            "Lệnh UPDATE không có điều kiện WHERE trên bảng: " + (targetTable.isEmpty() ? "(toàn bộ bảng)" : targetTable),
                            tok.line,
                            tok.col
                    ));
                }
            }
        }

        return issues;
    }

    /**
     * Checks if a table name is a temporary table (#, ##) or table variable (@).
     */
    public boolean isTemporaryOrVariableTable(String tableName) {
        if (tableName == null || tableName.isBlank()) return false;
        String clean = tableName.trim();

        // Handle quoted identifiers: [#temp], [@table], "#temp", "@table"
        if ((clean.startsWith("[") && clean.endsWith("]")) || (clean.startsWith("\"") && clean.endsWith("\""))) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }

        // Check if starts with # (temp table) or @ (table variable)
        if (clean.startsWith("#") || clean.startsWith("@")) {
            return true;
        }

        // Handle schema qualification: dbo.#temp or tempdb..#temp
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.");
            String lastPart = parts[parts.length - 1].trim();
            if (lastPart.startsWith("[") && lastPart.endsWith("]")) {
                lastPart = lastPart.substring(1, lastPart.length() - 1).trim();
            }
            if (lastPart.startsWith("#") || lastPart.startsWith("@")) {
                return true;
            }
        }

        return false;
    }

    private String extractTargetTable(List<Token> tokens, int startIndex) {
        if (startIndex >= tokens.size()) return "";

        StringBuilder sb = new StringBuilder();
        int idx = startIndex;

        while (idx < tokens.size()) {
            Token t = tokens.get(idx);
            if (t.type == TokenType.WORD || t.type == TokenType.IDENTIFIER || ".".equals(t.value)) {
                sb.append(t.value);
            } else if ("#".equals(t.value) || "@".equals(t.value)) {
                sb.append(t.value);
            } else {
                break;
            }
            idx++;
        }

        return sb.toString().trim();
    }

    private boolean hasWhereClauseInStatement(List<Token> tokens, int startIndex) {
        int parenDepth = 0;
        int caseDepth = 0;

        for (int j = startIndex; j < tokens.size(); j++) {
            Token t = tokens.get(j);

            if ("(".equals(t.value)) {
                parenDepth++;
            } else if (")".equals(t.value)) {
                if (parenDepth > 0) parenDepth--;
            } else if (parenDepth == 0) {
                if ("CASE".equalsIgnoreCase(t.value)) {
                    caseDepth++;
                } else if ("END".equalsIgnoreCase(t.value)) {
                    if (caseDepth > 0) caseDepth--;
                    else break;
                } else if ("WHERE".equalsIgnoreCase(t.value) && caseDepth == 0) {
                    return true;
                } else if (";".equals(t.value) || "GO".equalsIgnoreCase(t.value)) {
                    break;
                } else if (caseDepth == 0 && t.type == TokenType.WORD
                        && STATEMENT_STARTERS.contains(t.value.toUpperCase(Locale.ROOT))) {
                    // New statement started without semicolon
                    break;
                }
            }
        }

        return false;
    }

    private int skipParentheses(List<Token> tokens, int startIndex) {
        int idx = startIndex;
        int depth = 0;
        while (idx < tokens.size()) {
            Token t = tokens.get(idx);
            if ("(".equals(t.value)) depth++;
            else if (")".equals(t.value)) {
                depth--;
                if (depth <= 0) return idx + 1;
            }
            idx++;
        }
        return idx;
    }

    // ─────────────────────────────────────────────────────────────
    // Tokenizer
    // ─────────────────────────────────────────────────────────────

    private enum TokenType { WORD, IDENTIFIER, SYMBOL, STRING, COMMENT }

    private record Token(TokenType type, String value, int line, int col) {}

    private List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        int len = sql.length();
        int i = 0;
        int line = 1;
        int col = 1;

        while (i < len) {
            char ch = sql.charAt(i);

            // 1. Whitespace
            if (Character.isWhitespace(ch)) {
                if (ch == '\n') { line++; col = 1; }
                else { col++; }
                i++;
                continue;
            }

            int startLine = line;
            int startCol = col;

            // 2. Single-line comment: --
            if (ch == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                StringBuilder comment = new StringBuilder("--");
                i += 2; col += 2;
                while (i < len && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
                    comment.append(sql.charAt(i++));
                    col++;
                }
                tokens.add(new Token(TokenType.COMMENT, comment.toString(), startLine, startCol));
                continue;
            }

            // 3. Multi-line comment: /* ... */
            if (ch == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                StringBuilder comment = new StringBuilder("/*");
                i += 2; col += 2;
                while (i < len) {
                    if (sql.charAt(i) == '*' && i + 1 < len && sql.charAt(i + 1) == '/') {
                        comment.append("*/");
                        i += 2; col += 2;
                        break;
                    }
                    char c = sql.charAt(i++);
                    comment.append(c);
                    if (c == '\n') { line++; col = 1; }
                    else { col++; }
                }
                tokens.add(new Token(TokenType.COMMENT, comment.toString(), startLine, startCol));
                continue;
            }

            // 4. String literal: '...' or N'...'
            if (ch == '\'' || ((ch == 'N' || ch == 'n') && i + 1 < len && sql.charAt(i + 1) == '\'')) {
                if (ch == 'N' || ch == 'n') { i++; col++; }
                i++; col++; // skip opening '
                StringBuilder str = new StringBuilder();
                while (i < len) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                            str.append('\'');
                            i += 2; col += 2;
                        } else {
                            i++; col++; // closing '
                            break;
                        }
                    } else {
                        char c = sql.charAt(i++);
                        str.append(c);
                        if (c == '\n') { line++; col = 1; }
                        else { col++; }
                    }
                }
                tokens.add(new Token(TokenType.STRING, str.toString(), startLine, startCol));
                continue;
            }

            // 5. Quoted Identifiers: [...] or "..."
            if (ch == '[' || ch == '"') {
                char closeCh = ch == '[' ? ']' : '"';
                StringBuilder id = new StringBuilder();
                id.append(ch);
                i++; col++;
                while (i < len) {
                    char c = sql.charAt(i++);
                    id.append(c);
                    if (c == '\n') { line++; col = 1; }
                    else { col++; }
                    if (c == closeCh) break;
                }
                tokens.add(new Token(TokenType.IDENTIFIER, id.toString(), startLine, startCol));
                continue;
            }

            // 6. Word: letters, digits, _, #, @
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '#' || ch == '@') {
                StringBuilder word = new StringBuilder();
                while (i < len) {
                    char c = sql.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '#' || c == '@') {
                        word.append(c);
                        i++; col++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(TokenType.WORD, word.toString(), startLine, startCol));
                continue;
            }

            // 7. Symbols: ; , ( ) . = < > etc.
            tokens.add(new Token(TokenType.SYMBOL, String.valueOf(ch), startLine, startCol));
            i++; col++;
        }

        return tokens;
    }
}
