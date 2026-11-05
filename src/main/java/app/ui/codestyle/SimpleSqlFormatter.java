package app.ui.codestyle;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preview formatter for DLG-009 (prototype). Applies the main style options so the preview
 * reacts to every change: indentation, casing, clause / list / AND-OR / JOIN-ON / CASE line breaks,
 * comma position, spaces around operators and inside parentheses, empty lines.
 * The production formatter is wired at Gate D.
 */
public final class SimpleSqlFormatter {

    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS",
            "ON", "GROUP", "BY", "ORDER", "HAVING", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE",
            "TABLE", "ALTER", "DROP", "AS", "DISTINCT", "TOP", "LIMIT", "OFFSET", "CASE", "WHEN", "THEN", "ELSE", "END",
            "IN", "BETWEEN", "IS", "NULL", "LIKE", "UNION", "ALL", "EXISTS", "BEGIN", "DECLARE", "IF", "WHILE", "WITH",
            "ASC", "DESC", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "DEFAULT", "CONSTRAINT", "INDEX", "VIEW",
            "PROCEDURE", "FUNCTION", "RETURN", "RETURNS", "EXEC", "EXECUTE", "GO", "TRUNCATE", "MERGE", "USING",
            "MATCHED", "OUTPUT", "IDENTITY", "UNIQUE", "CHECK", "FOR", "LOOP", "RAISERROR", "PRINT", "COMMIT",
            "ROLLBACK", "TRANSACTION", "TRAN", "ELSIF", "ELSEIF", "LANGUAGE", "REPLACE", "OVER", "PARTITION", "ROWS");
    private static final Set<String> DATATYPES = Set.of(
            "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "BIT", "DECIMAL", "NUMERIC", "MONEY", "FLOAT", "REAL",
            "DATE", "DATETIME", "DATETIME2", "TIME", "TIMESTAMP", "TIMESTAMPTZ", "CHAR", "VARCHAR", "NCHAR", "NVARCHAR",
            "TEXT", "NTEXT", "BOOLEAN", "BOOL", "UUID", "UNIQUEIDENTIFIER", "SERIAL", "JSON", "JSONB", "XML", "VARBINARY",
            "BINARY", "DOUBLE", "PRECISION");
    /** Clause starters that begin a new line (longest first). */
    private static final List<String> CLAUSES = List.of(
            "LEFT OUTER JOIN", "RIGHT OUTER JOIN", "FULL OUTER JOIN", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN",
            "FULL JOIN", "CROSS JOIN", "GROUP BY", "ORDER BY", "INSERT INTO", "DELETE FROM", "UNION ALL",
            "SELECT", "FROM", "WHERE", "HAVING", "JOIN", "VALUES", "SET", "UPDATE", "UNION", "WITH", "DECLARE",
            "BEGIN", "END", "IF", "ELSE", "WHILE", "RETURN", "CREATE", "ALTER", "DROP");
    /** Keywords that are also functions when followed by "(". */
    private static final Set<String> KEYWORD_FUNCS = Set.of("LEFT", "RIGHT", "REPLACE", "IF", "EXISTS", "IN", "VALUES");
    private static final Set<String> LIST_CLAUSES = Set.of("SELECT", "GROUP BY", "ORDER BY", "SET", "VALUES");

    private static final Pattern TOKEN = Pattern.compile(
            "(--[^\\n]*)|(/\\*[\\s\\S]*?\\*/)|('(?:''|[^'])*')|(\"[^\"]*\"|\\[[^\\]]*\\])"
                    + "|([A-Za-z_@#$][A-Za-z0-9_@#$]*)|(\\d+(?:\\.\\d+)?)|(<>|!=|<=|>=|::|\\|\\||[=<>+\\-*/%])"
                    + "|([(),;.])|(\\s+)|(.)");

    private record Tok(String text, String kind) {
    }

    private final Map<String, Object> o;

    public SimpleSqlFormatter(Map<String, Object> options) {
        this.o = options != null ? options : SqlStyleMetadata.get().defaults();
    }

    private boolean b(String k) {
        return Boolean.TRUE.equals(o.get(k));
    }

    private String s(String k, String def) {
        Object v = o.get(k);
        return v != null ? v.toString() : def;
    }

    private int n(String k, int def) {
        Object v = o.get(k);
        return v instanceof Number num ? num.intValue() : def;
    }

    private String indent(int level) {
        if (level <= 0) return "";
        return b("whitespace.useTabs") ? "\t".repeat(level) : " ".repeat(level * n("whitespace.indentSize", 4));
    }

    private static String caseOf(String word, String mode) {
        return switch (mode) {
            case "upper" -> word.toUpperCase(Locale.ROOT);
            case "lower" -> word.toLowerCase(Locale.ROOT);
            default -> word;
        };
    }

    public String format(String sql) {
        if (sql == null || sql.isBlank()) return "";
        List<Tok> toks = tokenize(sql);
        StringBuilder out = new StringBuilder();
        int depth = 0;
        String clause = "";
        boolean inCase = false;
        int andOrCount = 0;
        for (Tok t : toks) if (t.kind.equals("word") && (t.text.equalsIgnoreCase("AND") || t.text.equalsIgnoreCase("OR"))) andOrCount++;
        String andOrMode = s("operators.andOrPlaceOnNewLine", "always");
        boolean andOrBreak = "always".equals(andOrMode) || ("when_multiple".equals(andOrMode) && andOrCount > 1);
        boolean betweenOpen = false;

        boolean unary = false;
        for (int i = 0; i < toks.size(); i++) {
            Tok t = toks.get(i);
            if (unary && !t.kind.equals("ws")) {
                unary = false;
                if (t.kind.equals("number") || t.kind.equals("word")) {
                    out.append(t.kind.equals("word") ? t.text : t.text);
                    continue;
                }
            }
            String up = t.text.toUpperCase(Locale.ROOT);
            if (t.kind.equals("ws")) continue;

            if (t.kind.equals("word")) {
                String clauseHit = matchClause(toks, i);
                if (clauseHit != null && depth == 0 && !(clauseHit.equals("END") && inCase) && !(clauseHit.equals("ELSE") && inCase)) {
                    if (b("statements.majorClausesNewLine")) newLine(out, 0); else space(out);
                    int words = clauseHit.split(" ").length;
                    StringBuilder kw = new StringBuilder();
                    for (int w = 0, j = i; w < words; j++) {
                        if (toks.get(j).kind.equals("ws")) continue;
                        if (kw.length() > 0) kw.append(' ');
                        kw.append(caseOf(toks.get(j).text, s("casing.keywords", "upper")));
                        w++;
                        i = j;
                    }
                    out.append(kw);
                    clause = clauseHit;
                    if (LIST_CLAUSES.contains(clause) && b("lists.itemsOnNewLine") && b("statements.majorClausesNewLine")) {
                        newLine(out, b("statements.indentClauseBody") ? 1 : 0);
                    } else {
                        out.append(' ');
                    }
                    continue;
                }
                if (up.equals("CASE")) inCase = true;
                if (up.equals("END") && inCase) inCase = false;
                if (inCase && up.equals("WHEN") && b("caseExpression.whenOnNewLine")) {
                    newLine(out, (b("statements.indentClauseBody") ? 1 : 0) + (b("caseExpression.indentWhen") ? 1 : 0));
                }
                if (inCase && up.equals("THEN") && !b("caseExpression.thenOnSameLine")) {
                    newLine(out, 2 + (b("caseExpression.indentWhen") ? 1 : 0));
                }
                if (up.equals("BETWEEN")) betweenOpen = true;
                if ((up.equals("AND") || up.equals("OR")) && depth == 0 && !betweenOpen && andOrBreak) {
                    newLine(out, "left".equals(s("operators.andOrAlignment", "rightToStatement")) ? 0 : 1);
                } else if (up.equals("AND") && betweenOpen) {
                    betweenOpen = false;
                    if (b("operators.betweenPlaceOnNewLine")) newLine(out, 2);
                }
                if (up.equals("ON") && clause.contains("JOIN") && b("clauses.joinOnNewLine")) {
                    newLine(out, b("clauses.indentJoinOn") ? 1 : 0);
                }
                String w;
                Tok next = nextNonWs(toks, i);
                boolean isFunc = next != null && next.text.equals("(")
                        && (!KEYWORDS.contains(up) || up.equals("LEFT") || up.equals("RIGHT") || up.equals("REPLACE"));
                if (KEYWORDS.contains(up)) w = caseOf(t.text, s("casing.keywords", "upper"));
                else if (DATATYPES.contains(up)) w = caseOf(t.text, s("casing.datatypes", "upper"));
                else if (isFunc) w = caseOf(t.text, s("casing.functions", "upper"));
                else w = t.text;
                space(out);
                out.append(w);
                if (isFunc && b("expressions.functionSpaceBeforeArgs")) out.append(' ');
                continue;
            }

            switch (t.text) {
                case "(" -> {
                    if (out.length() > 0 && !Character.isWhitespace(out.charAt(out.length() - 1))
                            && !isFuncCallBefore(toks, i)) out.append(' ');
                    out.append('(');
                    if (b("parentheses.spacesInside")) out.append(' ');
                    depth++;
                }
                case ")" -> {
                    depth = Math.max(0, depth - 1);
                    trimEnd(out);
                    if (b("parentheses.spacesInside")) out.append(' ');
                    out.append(')');
                }
                case "," -> {
                    trimEnd(out);
                    boolean listBreak = depth == 0 && LIST_CLAUSES.contains(clause) && b("lists.itemsOnNewLine");
                    if (listBreak && "before".equals(s("lists.commaPosition", "after"))) {
                        newLine(out, b("statements.indentClauseBody") ? 1 : 0);
                        out.append(", ");
                    } else if (listBreak) {
                        out.append(',');
                        newLine(out, b("statements.indentClauseBody") ? 1 : 0);
                    } else {
                        out.append(", ");
                    }
                }
                case ";" -> {
                    trimEnd(out);
                    out.append(';');
                    clause = "";
                }
                case "." -> {
                    trimEnd(out);
                    out.append('.');
                }
                default -> {
                    if (t.kind.equals("op") && (t.text.equals("-") || t.text.equals("+")) && isUnary(toks, i)) {
                        space(out);
                        out.append(t.text);
                        unary = true;
                    } else if (t.kind.equals("op")) {
                        if (b("operators.spacesAroundOperators") || t.text.equals("::")) {
                            if (t.text.equals("::")) { trimEnd(out); out.append("::"); }
                            else { space(out); out.append(t.text).append(' '); }
                        } else {
                            trimEnd(out);
                            out.append(t.text);
                        }
                    } else if (t.kind.equals("lineComment")) {
                        space(out);
                        out.append(t.text);
                        newLine(out, 0);
                    } else {
                        space(out);
                        out.append(t.text);
                    }
                }
            }
        }
        return tidy(out.toString());
    }

    private String tidy(String s) {
        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int empty = 0;
        int maxEmpty = n("whitespace.maxEmptyLines", 1);
        boolean first = true;
        for (String l : lines) {
            String line = b("whitespace.trimTrailingWhitespace") ? l.replaceAll("[ \\t]+$", "") : l;
            if (line.isBlank()) {
                if (first || ++empty > maxEmpty) continue;
            } else {
                empty = 0;
            }
            if (!first) sb.append('\n');
            sb.append(line);
            first = false;
        }
        return sb.toString().strip();
    }

    private static void trimEnd(StringBuilder sb) {
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') sb.setLength(sb.length() - 1);
    }

    private static void space(StringBuilder sb) {
        if (sb.length() == 0) return;
        char c = sb.charAt(sb.length() - 1);
        if (c != ' ' && c != '\n' && c != '\t' && c != '(' && c != '.') sb.append(' ');
    }

    private void newLine(StringBuilder sb, int level) {
        trimEnd(sb);
        if (sb.length() > 0) sb.append('\n');
        sb.append(indent(level));
    }

    private static boolean isUnary(List<Tok> toks, int i) {
        for (int j = i - 1; j >= 0; j--) {
            Tok t = toks.get(j);
            if (t.kind.equals("ws")) continue;
            return t.kind.equals("op") || t.text.equals("(") || t.text.equals(",")
                    || (t.kind.equals("word") && KEYWORDS.contains(t.text.toUpperCase(Locale.ROOT)));
        }
        return true;
    }

    private static Tok nextNonWs(List<Tok> toks, int i) {
        for (int j = i + 1; j < toks.size(); j++) if (!toks.get(j).kind.equals("ws")) return toks.get(j);
        return null;
    }

    private static boolean isFuncCallBefore(List<Tok> toks, int i) {
        for (int j = i - 1; j >= 0; j--) {
            Tok t = toks.get(j);
            if (t.kind.equals("ws")) continue;
            String u = t.text.toUpperCase(Locale.ROOT);
            return t.kind.equals("word") && (!KEYWORDS.contains(u) || u.equals("LEFT") || u.equals("RIGHT") || u.equals("REPLACE"));
        }
        return false;
    }

    private static String matchClause(List<Tok> toks, int i) {
        for (String c : CLAUSES) {
            String[] parts = c.split(" ");
            int j = i, k = 0;
            while (k < parts.length && j < toks.size()) {
                Tok t = toks.get(j);
                if (t.kind.equals("ws")) { j++; continue; }
                if (!t.kind.equals("word") || !t.text.equalsIgnoreCase(parts[k])) break;
                k++;
                j++;
            }
            if (k == parts.length) return c;
        }
        return null;
    }

    private static List<Tok> tokenize(String sql) {
        List<Tok> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(sql);
        while (m.find()) {
            String kind = m.group(1) != null ? "lineComment" : m.group(2) != null ? "blockComment"
                    : m.group(3) != null ? "string" : m.group(4) != null ? "quoted" : m.group(5) != null ? "word"
                    : m.group(6) != null ? "number" : m.group(7) != null ? "op" : m.group(8) != null ? "punct"
                    : m.group(9) != null ? "ws" : "other";
            out.add(new Tok(m.group(), kind));
        }
        return out;
    }
}
