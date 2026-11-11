package app.framework.syntax;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast offline/static structural checks for SQL statements:
 * <ul>
 *   <li>Unclosed string literals (') and block comments (/* *&#47;)</li>
 *   <li>Unbalanced parentheses and unclosed square brackets</li>
 *   <li>Consecutive commas and trailing commas before clauses</li>
 *   <li>Empty SELECT projections (SELECT FROM ...)</li>
 *   <li>PostgreSQL dollar-quoted blocks ($tag$...$tag$) and PL/pgSQL procedural blocks</li>
 * </ul>
 */
public final class StructuralSqlChecks {

    private record OpenPos(int line, int col, int endLine, int endCol, char ch) {}

    private static final Pattern EMPTY_SELECT_PATTERN = Pattern.compile(
            "\\bSELECT\\s+(?:DISTINCT\\s+|TOP\\s+\\d+\\s+)?(?=\\b(?:FROM|WHERE|GROUP|HAVING|ORDER|LIMIT|OFFSET)\\b|,|;|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DOLLAR_TAG_PATTERN = Pattern.compile("^\\$([a-zA-Z0-9_]*)\\$");

    private StructuralSqlChecks() {}

    public static List<SyntaxDiagnostic> check(String sql, String dialect) {
        List<SyntaxDiagnostic> out = new ArrayList<>();
        if (sql == null || sql.isBlank()) return out;

        String d = dialect != null ? dialect.toLowerCase(Locale.ROOT) : "sqlserver";
        boolean isPg = d.contains("postgres") || d.contains("pgsql");

        // 1. Basic structural delimiters (quotes, comments, parens, brackets, commas)
        out.addAll(checkBasicStructural(sql, isPg));

        // 2. Empty SELECT projection check
        out.addAll(checkEmptySelect(sql));

        // 3. PostgreSQL dollar quotes and procedural block balance
        if (isPg) {
            out.addAll(checkPostgresStructuralBlocks(sql));
        }

        // 4. Offline Grammar and clause validation
        out.addAll(checkOfflineGrammar(sql, d));

        return out;
    }

    public static List<SyntaxDiagnostic> checkBasicStructural(String sql, boolean isPg) {
        List<SyntaxDiagnostic> out = new ArrayList<>();
        int line = 1;
        int col = 1;
        int len = sql.length();

        Deque<OpenPos> parenStack = new ArrayDeque<>();
        Deque<OpenPos> bracketStack = new ArrayDeque<>();

        int stringStartLine = -1;
        int stringStartCol = -1;

        int commentStartLine = -1;
        int commentStartCol = -1;

        int lastCommaLine = -1;
        int lastCommaCol = -1;

        int i = 0;
        while (i < len) {
            char c = sql.charAt(i);

            // Inside string literal
            if (stringStartLine > 0) {
                if (c == '\'') {
                    if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                        i += 2;
                        col += 2;
                        continue;
                    } else {
                        stringStartLine = -1;
                        stringStartCol = -1;
                        i++;
                        col++;
                        continue;
                    }
                } else if (c == '\n') {
                    line++;
                    col = 1;
                    i++;
                    continue;
                } else {
                    i++;
                    col++;
                    continue;
                }
            }

            // Inside block comment
            if (commentStartLine > 0) {
                if (c == '*' && i + 1 < len && sql.charAt(i + 1) == '/') {
                    commentStartLine = -1;
                    commentStartCol = -1;
                    i += 2;
                    col += 2;
                    continue;
                } else if (c == '\n') {
                    line++;
                    col = 1;
                    i++;
                    continue;
                } else {
                    i++;
                    col++;
                    continue;
                }
            }

            // Single line comment
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                i += 2;
                col += 2;
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                    col++;
                }
                continue;
            }

            // Block comment start
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                commentStartLine = line;
                commentStartCol = col;
                i += 2;
                col += 2;
                continue;
            }

            // String start
            if (c == '\'') {
                stringStartLine = line;
                stringStartCol = col;
                i++;
                col++;
                continue;
            }

            // Parentheses
            if (c == '(') {
                parenStack.push(new OpenPos(line, col, line, col + 1, '('));
                lastCommaLine = -1;
            } else if (c == ')') {
                if (parenStack.isEmpty()) {
                    out.add(new SyntaxDiagnostic(SyntaxDiagnostic.Severity.ERROR, "UNBALANCED_PAREN", "offline", ")",
                            line, col, line, col + 1, "Unmatched closing parenthesis ')'"));
                } else {
                    parenStack.pop();
                }
                lastCommaLine = -1;
            }

            // Square brackets (T-SQL only)
            if (!isPg) {
                if (c == '[') {
                    bracketStack.push(new OpenPos(line, col, line, col + 1, '['));
                } else if (c == ']') {
                    if (bracketStack.isEmpty()) {
                        out.add(new SyntaxDiagnostic(SyntaxDiagnostic.Severity.ERROR, "UNBALANCED_BRACKET", "offline", "]",
                                line, col, line, col + 1, "Unmatched closing bracket ']'"));
                    } else {
                        bracketStack.pop();
                    }
                }
            }

            // Comma checks
            if (c == ',') {
                if (lastCommaLine > 0) {
                    out.add(new SyntaxDiagnostic(SyntaxDiagnostic.Severity.ERROR, "CONSECUTIVE_COMMAS", "offline", ",",
                            line, col, line, col + 1, "Invalid syntax: consecutive commas (',,')"));
                }
                lastCommaLine = line;
                lastCommaCol = col;
            } else if (Character.isWhitespace(c)) {
                if (c == '\n') {
                    line++;
                    col = 0;
                }
            } else if (c != ';') {
                if (lastCommaLine > 0) {
                    String sub = sql.substring(i);
                    String clause = matchClause(sub);
                    if (clause != null) {
                        out.add(new SyntaxDiagnostic(SyntaxDiagnostic.Severity.ERROR, "TRAILING_COMMA", "offline", ",",
                                lastCommaLine, lastCommaCol, lastCommaLine, lastCommaCol + 1,
                                "Invalid syntax: trailing comma before keyword '" + clause + "'"));
                    }
                    lastCommaLine = -1;
                }
            }

            i++;
            col++;
        }

        if (stringStartLine > 0) {
            out.add(new SyntaxDiagnostic(SyntaxDiagnostic.Severity.ERROR, "UNCLOSED_STRING", "offline", "'",
                    stringStartLine, stringStartCol, stringStartLine, stringStartCol + 1, "Unclosed quotation mark (')"));
        }

        if (commentStartLine > 0) {
            out.add(new SyntaxDiagnostic(SyntaxDiagnostic.Severity.ERROR, "UNCLOSED_COMMENT", "offline", "/*",
                    commentStartLine, commentStartCol, commentStartLine, commentStartCol + 2, "Unclosed block comment /* ... */"));
        }

        while (!parenStack.isEmpty()) {
            OpenPos p = parenStack.pop();
            out.add(new SyntaxDiagnostic(SyntaxDiagnostic.Severity.ERROR, "UNCLOSED_PAREN", "offline", "(",
                    p.line, p.col, p.endLine, p.endCol, "Unclosed parenthesis '('"));
        }

        while (!bracketStack.isEmpty()) {
            OpenPos p = bracketStack.pop();
            out.add(new SyntaxDiagnostic(SyntaxDiagnostic.Severity.ERROR, "UNCLOSED_BRACKET", "offline", "[",
                    p.line, p.col, p.endLine, p.endCol, "Unclosed bracket '['"));
        }

        return out;
    }

    public static List<SyntaxDiagnostic> checkEmptySelect(String sql) {
        List<SyntaxDiagnostic> out = new ArrayList<>();
        if (sql == null || sql.isBlank()) return out;

        String[] lines = sql.split("\r?\n", -1);
        int currentOffset = 0;

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx];
            Matcher m = EMPTY_SELECT_PATTERN.matcher(line);
            while (m.find()) {
                int col = m.start() + 1;
                out.add(new SyntaxDiagnostic(
                        SyntaxDiagnostic.Severity.ERROR,
                        "EMPTY_SELECT",
                        "offline",
                        "SELECT",
                        lineIdx + 1,
                        col,
                        lineIdx + 1,
                        col + 6,
                        "Mệnh đề SELECT thiếu danh sách cột hoặc biểu thức chiếu."
                ));
            }
            currentOffset += line.length() + 1;
        }

        return out;
    }

    public static List<SyntaxDiagnostic> checkPostgresStructuralBlocks(String sql) {
        List<SyntaxDiagnostic> diags = new ArrayList<>();
        if (sql == null || sql.isBlank()) return diags;

        int n = sql.length();
        int i = 0;

        record DollarBlock(String tag, int contentStart, int contentEnd, int tagStart, int tagLine, int tagCol) {}
        List<DollarBlock> blocks = new ArrayList<>();

        while (i < n) {
            // 1. Single line comment
            if (i + 1 < n && sql.charAt(i) == '-' && sql.charAt(i + 1) == '-') {
                i += 2;
                while (i < n && sql.charAt(i) != '\n') i++;
                continue;
            }

            // 2. Multi line comment
            if (i + 1 < n && sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') {
                i += 2;
                int depth = 1;
                while (i < n && depth > 0) {
                    if (i + 1 < n && sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') {
                        depth++;
                        i += 2;
                    } else if (i + 1 < n && sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
                        depth--;
                        i += 2;
                    } else {
                        i++;
                    }
                }
                continue;
            }

            // 3. String literal
            if (sql.charAt(i) == '\'') {
                i++;
                while (i < n) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                            i += 2;
                        } else {
                            i++;
                            break;
                        }
                    } else {
                        i++;
                    }
                }
                continue;
            }

            // 4. Dollar quote $[a-zA-Z0-9_]*\$
            if (sql.charAt(i) == '$') {
                Matcher tm = DOLLAR_TAG_PATTERN.matcher(sql.substring(i));
                if (tm.find()) {
                    String tag = tm.group(0);
                    int tagStart = i;
                    int contentStart = i + tag.length();

                    int tagLine = countLines(sql, tagStart) + 1;
                    int lastNl = sql.lastIndexOf('\n', tagStart);
                    int tagCol = (lastNl == -1) ? (tagStart + 1) : (tagStart - lastNl);

                    int closingPos = sql.indexOf(tag, contentStart);
                    if (closingPos == -1) {
                        diags.add(new SyntaxDiagnostic(
                                SyntaxDiagnostic.Severity.ERROR,
                                "SQL_SYNTAX_ERROR",
                                "postgresql",
                                tag,
                                tagLine,
                                tagCol,
                                tagLine,
                                tagCol + tag.length(),
                                "Unclosed dollar-quoted block '" + tag + "'"
                        ));
                        return diags;
                    }

                    blocks.add(new DollarBlock(tag, contentStart, closingPos, tagStart, tagLine, tagCol));
                    i = closingPos + tag.length();
                    continue;
                }
            }

            i++;
        }

        // Procedural blocks check inside each dollar-quoted block
        for (DollarBlock block : blocks) {
            String blockText = sql.substring(block.contentStart, block.contentEnd);
            List<SyntaxDiagnostic> blockDiags = checkProceduralTokens(blockText, block.contentStart, sql);
            diags.addAll(blockDiags);
        }

        return diags;
    }

    private static List<SyntaxDiagnostic> checkProceduralTokens(String blockText, int absOffset, String fullSql) {
        List<SyntaxDiagnostic> diags = new ArrayList<>();
        int len = blockText.length();
        int j = 0;

        record Tok(String text, int offset) {}
        List<Tok> tokens = new ArrayList<>();

        while (j < len) {
            // skip comments & strings inside block
            if (j + 1 < len && blockText.charAt(j) == '-' && blockText.charAt(j + 1) == '-') {
                j += 2;
                while (j < len && blockText.charAt(j) != '\n') j++;
                continue;
            }
            if (j + 1 < len && blockText.charAt(j) == '/' && blockText.charAt(j + 1) == '*') {
                j += 2;
                while (j + 1 < len && !(blockText.charAt(j) == '*' && blockText.charAt(j + 1) == '/')) j++;
                j += 2;
                continue;
            }
            if (blockText.charAt(j) == '\'') {
                j++;
                while (j < len) {
                    if (blockText.charAt(j) == '\'') {
                        if (j + 1 < len && blockText.charAt(j + 1) == '\'') j += 2;
                        else { j++; break; }
                    } else j++;
                }
                continue;
            }

            char c = blockText.charAt(j);
            if (Character.isLetter(c)) {
                int start = j;
                while (j < len && (Character.isLetterOrDigit(blockText.charAt(j)) || blockText.charAt(j) == '_')) j++;
                String word = blockText.substring(start, j).toUpperCase(Locale.ROOT);
                if (word.equals("BEGIN") || word.equals("END") || word.equals("IF") || word.equals("LOOP") || word.equals("CASE")) {
                    tokens.add(new Tok(word, absOffset + start));
                }
                continue;
            }
            j++;
        }

        // Stack-based block balancing (BEGIN...END, IF...END IF, LOOP...END LOOP, CASE...END CASE)
        Deque<Tok> stack = new ArrayDeque<>();
        for (int k = 0; k < tokens.size(); k++) {
            Tok t = tokens.get(k);
            if (t.text.equals("BEGIN") || t.text.equals("LOOP") || t.text.equals("CASE")) {
                stack.push(t);
            } else if (t.text.equals("IF")) {
                if (k > 0 && tokens.get(k - 1).text.equals("END")) {
                    // END IF, already handled
                } else {
                    stack.push(t);
                }
            } else if (t.text.equals("END")) {
                String qualifier = "";
                if (k + 1 < tokens.size()) {
                    String next = tokens.get(k + 1).text;
                    if (next.equals("IF") || next.equals("LOOP") || next.equals("CASE")) {
                        qualifier = next;
                        k++;
                    }
                }
                if (!stack.isEmpty()) {
                    Tok top = stack.peek();
                    if (!qualifier.isEmpty()) {
                        if (top.text.equals(qualifier)) {
                            stack.pop();
                        }
                    } else {
                        if (top.text.equals("BEGIN") || top.text.equals("CASE")) {
                            stack.pop();
                        }
                    }
                }
            }
        }

        // If stack not empty, reported on the topmost unclosed token
        if (!stack.isEmpty()) {
            Tok unclosed = stack.peekLast(); // earliest unclosed token
            int line = countLines(fullSql, unclosed.offset) + 1;
            int lastNl = fullSql.lastIndexOf('\n', unclosed.offset);
            int col = (lastNl == -1) ? (unclosed.offset + 1) : (unclosed.offset - lastNl);
            diags.add(new SyntaxDiagnostic(
                    SyntaxDiagnostic.Severity.ERROR,
                    "SQL_SYNTAX_ERROR",
                    "postgresql",
                    unclosed.text,
                    line,
                    col,
                    line,
                    col + unclosed.text.length(),
                    "Unclosed procedural block beginning at '" + unclosed.text + "'"
            ));
        }

        return diags;
    }

    private static final Pattern CLAUSE_PATTERN = Pattern.compile("^(FROM|WHERE|ORDER\\s+BY|GROUP\\s+BY|HAVING)\\b", Pattern.CASE_INSENSITIVE);

    private static String matchClause(String s) {
        Matcher m = CLAUSE_PATTERN.matcher(s);
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private static final java.util.Map<String, String> CLAUSE_TYPOS = java.util.Map.ofEntries(
            java.util.Map.entry("FORM", "FROM"),
            java.util.Map.entry("WHER", "WHERE"),
            java.util.Map.entry("WEHRE", "WHERE"),
            java.util.Map.entry("GROP", "GROUP"),
            java.util.Map.entry("GROPU", "GROUP"),
            java.util.Map.entry("HAVNG", "HAVING"),
            java.util.Map.entry("HAIVNG", "HAVING"),
            java.util.Map.entry("ORDR", "ORDER"),
            java.util.Map.entry("ODER", "ORDER"),
            java.util.Map.entry("JION", "JOIN"),
            java.util.Map.entry("JON", "JOIN"),
            java.util.Map.entry("INSER", "INSERT"),
            java.util.Map.entry("UPDAT", "UPDATE"),
            java.util.Map.entry("DELTE", "DELETE"),
            java.util.Map.entry("SELETC", "SELECT"),
            java.util.Map.entry("SELCET", "SELECT"),
            java.util.Map.entry("SLECT", "SELECT")
    );

    public record SqlToken(
            String text,
            String upper,
            int line,
            int col,
            int endLine,
            int endCol,
            TokenType type
    ) {
        public enum TokenType {
            KEYWORD, IDENTIFIER, SYMBOL, NUMBER, STRING
        }
    }

    public static List<SqlToken> tokenize(String sql) {
        List<SqlToken> tokens = new ArrayList<>();
        if (sql == null || sql.isEmpty()) return tokens;

        int len = sql.length();
        int line = 1;
        int col = 1;
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            // 1. Whitespace
            if (Character.isWhitespace(c)) {
                if (c == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
                i++;
                continue;
            }

            // 2. Line comment --
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                i += 2;
                col += 2;
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                    col++;
                }
                continue;
            }

            // 3. Block comment /* ... */
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                i += 2;
                col += 2;
                while (i + 1 < len && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    if (sql.charAt(i) == '\n') {
                        line++;
                        col = 1;
                    } else {
                        col++;
                    }
                    i++;
                }
                if (i + 1 < len) {
                    i += 2;
                    col += 2;
                }
                continue;
            }

            // 4. String literal '...'
            if (c == '\'') {
                int startLine = line;
                int startCol = col;
                int startOffset = i;
                i++;
                col++;
                while (i < len) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                            i += 2;
                            col += 2;
                        } else {
                            i++;
                            col++;
                            break;
                        }
                    } else if (sql.charAt(i) == '\n') {
                        line++;
                        col = 1;
                        i++;
                    } else {
                        i++;
                        col++;
                    }
                }
                String text = sql.substring(startOffset, i);
                tokens.add(new SqlToken(text, text, startLine, startCol, line, col, SqlToken.TokenType.STRING));
                continue;
            }

            // 5. Square bracket identifier [...]
            if (c == '[') {
                int startLine = line;
                int startCol = col;
                int startOffset = i;
                i++;
                col++;
                while (i < len && sql.charAt(i) != ']') {
                    if (sql.charAt(i) == '\n') {
                        line++;
                        col = 1;
                    } else {
                        col++;
                    }
                    i++;
                }
                if (i < len && sql.charAt(i) == ']') {
                    i++;
                    col++;
                }
                String text = sql.substring(startOffset, i);
                tokens.add(new SqlToken(text, text, startLine, startCol, line, col, SqlToken.TokenType.IDENTIFIER));
                continue;
            }

            // 6. Quoted identifier "..."
            if (c == '"') {
                int startLine = line;
                int startCol = col;
                int startOffset = i;
                i++;
                col++;
                while (i < len && sql.charAt(i) != '"') {
                    if (sql.charAt(i) == '\n') {
                        line++;
                        col = 1;
                    } else {
                        col++;
                    }
                    i++;
                }
                if (i < len && sql.charAt(i) == '"') {
                    i++;
                    col++;
                }
                String text = sql.substring(startOffset, i);
                tokens.add(new SqlToken(text, text, startLine, startCol, line, col, SqlToken.TokenType.IDENTIFIER));
                continue;
            }

            // 7. Numbers
            if (Character.isDigit(c)) {
                int startLine = line;
                int startCol = col;
                int startOffset = i;
                while (i < len && (Character.isDigit(sql.charAt(i)) || sql.charAt(i) == '.' || sql.charAt(i) == 'e' || sql.charAt(i) == 'E' || sql.charAt(i) == 'x' || sql.charAt(i) == 'X')) {
                    i++;
                    col++;
                }
                String text = sql.substring(startOffset, i);
                tokens.add(new SqlToken(text, text, startLine, startCol, line, col, SqlToken.TokenType.NUMBER));
                continue;
            }

            // 8. Words / Identifiers / Keywords (starts with letter, _, @, #)
            if (Character.isLetter(c) || c == '_' || c == '@' || c == '#') {
                int startLine = line;
                int startCol = col;
                int startOffset = i;
                while (i < len) {
                    char ch = sql.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '@' || ch == '#' || ch == '$') {
                        i++;
                        col++;
                    } else {
                        break;
                    }
                }
                String text = sql.substring(startOffset, i);
                tokens.add(new SqlToken(text, text.toUpperCase(Locale.ROOT), startLine, startCol, line, col, SqlToken.TokenType.KEYWORD));
                continue;
            }

            // 9. Symbols: () , ; . * = < > ! + - / %
            int startLine = line;
            int startCol = col;
            String symText = String.valueOf(c);
            i++;
            col++;
            tokens.add(new SqlToken(symText, symText, startLine, startCol, line, col, SqlToken.TokenType.SYMBOL));
        }

        return tokens;
    }

    public static List<SyntaxDiagnostic> checkOfflineGrammar(String sql, String dialect) {
        List<SyntaxDiagnostic> diags = new ArrayList<>();
        if (sql == null || sql.isBlank()) return diags;

        List<SqlToken> tokens = tokenize(sql);
        if (tokens.isEmpty()) return diags;

        String dLower = (dialect != null ? dialect : "sqlserver").toLowerCase(Locale.ROOT);
        int n = tokens.size();

        for (int i = 0; i < n; i++) {
            SqlToken t = tokens.get(i);

            // 1. Check for standalone misspelled clause keywords (typos)
            if (CLAUSE_TYPOS.containsKey(t.upper())) {
                boolean isQuoted = t.text().startsWith("[") || t.text().startsWith("\"") || t.type() == SqlToken.TokenType.STRING;
                boolean isAlias = (i > 0 && tokens.get(i - 1).upper().equals("AS"));
                boolean isTableRef = (i > 0 && (tokens.get(i - 1).upper().equals("FROM") || tokens.get(i - 1).upper().equals("JOIN") || tokens.get(i - 1).upper().equals("INTO") || tokens.get(i - 1).upper().equals("TABLE")));
                if (!isQuoted && !isAlias && !isTableRef) {
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            t.text(),
                            t.line(),
                            t.col(),
                            t.endLine(),
                            t.endCol(),
                            "Incorrect syntax near '" + t.text() + "'."
                    ));
                    continue;
                }
            }

            // 2. SELECT statement grammar
            if (t.upper().equals("SELECT")) {
                int j = i + 1;
                // Skip DISTINCT / ALL
                if (j < n && (tokens.get(j).upper().equals("DISTINCT") || tokens.get(j).upper().equals("ALL"))) {
                    j++;
                }
                // Skip TOP (<expr>) or TOP <num> [PERCENT] [WITH TIES]
                if (j < n && tokens.get(j).upper().equals("TOP")) {
                    j++;
                    if (j < n && tokens.get(j).text().equals("(")) {
                        int depth = 1;
                        j++;
                        while (j < n && depth > 0) {
                            if (tokens.get(j).text().equals("(")) depth++;
                            else if (tokens.get(j).text().equals(")")) depth--;
                            j++;
                        }
                    } else if (j < n && tokens.get(j).type() == SqlToken.TokenType.NUMBER) {
                        j++;
                    }
                    if (j < n && tokens.get(j).upper().equals("PERCENT")) j++;
                    if (j + 1 < n && tokens.get(j).upper().equals("WITH") && tokens.get(j + 1).upper().equals("TIES")) j += 2;
                }

                // Check first projection token
                if (j < n && tokens.get(j).text().equals("*")) {
                    SqlToken starTok = tokens.get(j);
                    int afterStar = j + 1;
                    if (afterStar >= n || tokens.get(afterStar).text().equals(";") || tokens.get(afterStar).upper().equals("GO")) {
                        diags.add(new SyntaxDiagnostic(
                                SyntaxDiagnostic.Severity.ERROR,
                                "SQL_SYNTAX_ERROR",
                                dLower,
                                "*",
                                starTok.line(),
                                starTok.col(),
                                starTok.endLine(),
                                starTok.endCol(),
                                "Incorrect syntax near '*'. Mệnh đề 'SELECT *' yêu cầu có 'FROM'."
                        ));
                    } else {
                        SqlToken next = tokens.get(afterStar);
                        // What can follow *? Either a comma (,), or FROM, or INTO.
                        if (!next.text().equals(",") && !next.upper().equals("FROM") && !next.upper().equals("INTO")) {
                            diags.add(new SyntaxDiagnostic(
                                    SyntaxDiagnostic.Severity.ERROR,
                                    "SQL_SYNTAX_ERROR",
                                    dLower,
                                    next.text(),
                                    next.line(),
                                    next.col(),
                                    next.endLine(),
                                    next.endCol(),
                                    "Incorrect syntax near '" + next.text() + "'."
                            ));
                        }
                    }
                }
            }

            // 3. Clause keywords followed immediately by EOF, ';', or another clause keyword
            if (t.upper().equals("FROM")) {
                if (i + 1 >= n || isClauseTerminator(tokens.get(i + 1))) {
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            "FROM",
                            t.line(),
                            t.col(),
                            t.endLine(),
                            t.endCol(),
                            "Incorrect syntax near the keyword 'FROM'."
                    ));
                }
            } else if (t.upper().equals("WHERE")) {
                if (i + 1 >= n || isClauseTerminator(tokens.get(i + 1))) {
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            "WHERE",
                            t.line(),
                            t.col(),
                            t.endLine(),
                            t.endCol(),
                            "Incorrect syntax near the keyword 'WHERE'."
                    ));
                }
            } else if (t.upper().equals("GROUP")) {
                if (i + 1 >= n || !tokens.get(i + 1).upper().equals("BY")) {
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            "GROUP",
                            t.line(),
                            t.col(),
                            t.endLine(),
                            t.endCol(),
                            "Incorrect syntax near 'GROUP'. Expected 'BY'."
                    ));
                } else if (i + 2 >= n || isClauseTerminator(tokens.get(i + 2))) {
                    SqlToken byTok = tokens.get(i + 1);
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            "BY",
                            byTok.line(),
                            byTok.col(),
                            byTok.endLine(),
                            byTok.endCol(),
                            "Incorrect syntax near the keyword 'BY'."
                    ));
                }
            } else if (t.upper().equals("ORDER")) {
                if (i + 1 >= n || !tokens.get(i + 1).upper().equals("BY")) {
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            "ORDER",
                            t.line(),
                            t.col(),
                            t.endLine(),
                            t.endCol(),
                            "Incorrect syntax near 'ORDER'. Expected 'BY'."
                    ));
                } else if (i + 2 >= n || isClauseTerminator(tokens.get(i + 2))) {
                    SqlToken byTok = tokens.get(i + 1);
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            "BY",
                            byTok.line(),
                            byTok.col(),
                            byTok.endLine(),
                            byTok.endCol(),
                            "Incorrect syntax near the keyword 'BY'."
                    ));
                }
            } else if (t.upper().equals("HAVING")) {
                if (i + 1 >= n || isClauseTerminator(tokens.get(i + 1))) {
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            "HAVING",
                            t.line(),
                            t.col(),
                            t.endLine(),
                            t.endCol(),
                            "Incorrect syntax near the keyword 'HAVING'."
                    ));
                }
            } else if (t.upper().equals("JOIN")) {
                if (i + 1 >= n || isClauseTerminator(tokens.get(i + 1)) || tokens.get(i + 1).upper().equals("ON")) {
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            "JOIN",
                            t.line(),
                            t.col(),
                            t.endLine(),
                            t.endCol(),
                            "Incorrect syntax near the keyword 'JOIN'."
                    ));
                }
            } else if (t.upper().equals("ON")) {
                if (i + 1 >= n || isClauseTerminator(tokens.get(i + 1)) || tokens.get(i + 1).upper().equals("JOIN")) {
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_SYNTAX_ERROR",
                            dLower,
                            "ON",
                            t.line(),
                            t.col(),
                            t.endLine(),
                            t.endCol(),
                            "Incorrect syntax near the keyword 'ON'."
                    ));
                }
            }
        }

        return diags;
    }

    private static boolean isClauseTerminator(SqlToken tok) {
        if (tok == null) return true;
        String u = tok.upper();
        return u.equals(";") || u.equals("GO") || u.equals("WHERE") || u.equals("GROUP")
                || u.equals("HAVING") || u.equals("ORDER") || u.equals("UNION")
                || u.equals("EXCEPT") || u.equals("INTERSECT") || u.equals("OPTION")
                || u.equals("FOR");
    }

    private static int countLines(String text, int endOffset) {
        int count = 0;
        int limit = Math.min(endOffset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }
}
