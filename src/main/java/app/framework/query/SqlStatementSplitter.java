package app.framework.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase 8 / D3: splits a SQL script into statements and finds the one under the caret.
 *
 * <p>Separators: {@code ;} and, for SQL Server scripts, a line that holds only {@code GO}
 * (optionally followed by a repeat count). Separators inside strings ({@code '…'}),
 * quoted identifiers ({@code "…"}, {@code […]}), comments ({@code -- …}, nested block comments) and
 * PostgreSQL dollar-quoted bodies ({@code $tag$ … $tag$}) are ignored.
 *
 * <p>Pure Java, no UI or JDBC dependency, so it can be unit tested.
 */
public final class SqlStatementSplitter {

    /** One statement. Offsets are 0-based; {@code end} is exclusive. Lines are 1-based. */
    public record Part(int start, int end, int startLine, int endLine, String text) {
    }

    private SqlStatementSplitter() {
    }

    public static List<Part> split(String sql) {
        List<Part> out = new ArrayList<>();
        if (sql == null || sql.isBlank()) {
            return out;
        }
        int n = sql.length();
        int[] lineStarts = lineStarts(sql);
        int segStart = 0;
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuoted(sql, i, c);
            } else if (c == '[') {
                i = skipBracket(sql, i);
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i = skipLineComment(sql, i);
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i = skipBlockComment(sql, i);
            } else if (c == '$') {
                i = skipDollarQuoted(sql, i);
            } else if (c == ';') {
                add(out, sql, lineStarts, segStart, i + 1);
                segStart = i + 1;
                i++;
            } else if ((c == 'g' || c == 'G') && isGoLine(sql, i)) {
                int lineEnd = endOfLine(sql, i);
                add(out, sql, lineStarts, segStart, i);
                segStart = lineEnd;
                i = lineEnd;
            } else {
                i++;
            }
        }
        add(out, sql, lineStarts, segStart, n);
        return out;
    }

    /**
     * The statement the caret is in. A caret in blank space between statements picks the previous one
     * (the next one if there is no previous). Empty when the script has no statement.
     *
     * @param line 1-based
     * @param col  1-based
     */
    public static Optional<Part> atCursor(String sql, int line, int col) {
        List<Part> parts = split(sql);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        int offset = offsetOf(sql, line, col);
        Part prev = null;
        for (Part p : parts) {
            if (offset >= p.start() && offset <= p.end()) {
                return Optional.of(p);
            }
            if (offset < p.start()) {
                return Optional.of(prev != null ? prev : p);
            }
            prev = p;
        }
        return Optional.of(prev);
    }

    /** 0-based offset of a 1-based line / column, clamped to the text. */
    static int offsetOf(String sql, int line, int col) {
        int[] starts = lineStarts(sql);
        int li = Math.max(1, Math.min(line, starts.length)) - 1;
        int lineEnd = li + 1 < starts.length ? starts[li + 1] - 1 : sql.length();
        return Math.max(starts[li], Math.min(starts[li] + Math.max(col, 1) - 1, lineEnd));
    }

    // ------------------------------------------------------------------ internals

    private static void add(List<Part> out, String sql, int[] lineStarts, int from, int to) {
        if (to <= from) {
            return;
        }
        int s = from;
        while (s < to && Character.isWhitespace(sql.charAt(s))) {
            s++;
        }
        int e = to;
        while (e > s && Character.isWhitespace(sql.charAt(e - 1))) {
            e--;
        }
        if (e <= s) {
            return;
        }
        String text = sql.substring(s, e);
        if (!hasCode(text)) {
            return; // only comments (or a lone ';')
        }
        out.add(new Part(s, e, lineOf(lineStarts, s), lineOf(lineStarts, e - 1), text));
    }

    /** True if the text has anything besides whitespace, comments and semicolons. */
    private static boolean hasCode(String text) {
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == ';') {
                i++;
            } else if (c == '-' && i + 1 < n && text.charAt(i + 1) == '-') {
                i = skipLineComment(text, i);
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                i = skipBlockComment(text, i);
            } else {
                return true;
            }
        }
        return false;
    }

    private static int[] lineStarts(String sql) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        int[] a = new int[starts.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = starts.get(i);
        }
        return a;
    }

    private static int lineOf(int[] lineStarts, int offset) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo + 1;
    }

    /** {@code i} is at the opening quote; returns the index after the closing quote (doubled quote = escape). */
    private static int skipQuoted(String s, int i, char q) {
        int n = s.length();
        int j = i + 1;
        while (j < n) {
            if (s.charAt(j) == q) {
                if (j + 1 < n && s.charAt(j + 1) == q) {
                    j += 2;
                    continue;
                }
                return j + 1;
            }
            j++;
        }
        return n; // unterminated: swallow the rest
    }

    /** {@code [identifier]}; only when the closing bracket is on the same line, else treat '[' as a plain char. */
    private static int skipBracket(String s, int i) {
        int n = s.length();
        for (int j = i + 1; j < n; j++) {
            char c = s.charAt(j);
            if (c == ']') {
                return j + 1;
            }
            if (c == '\n') {
                break;
            }
        }
        return i + 1;
    }

    private static int skipLineComment(String s, int i) {
        int n = s.length();
        int j = i + 2;
        while (j < n && s.charAt(j) != '\n') {
            j++;
        }
        return j;
    }

    /** Block comments nest (PostgreSQL). */
    private static int skipBlockComment(String s, int i) {
        int n = s.length();
        int depth = 1;
        int j = i + 2;
        while (j < n && depth > 0) {
            if (s.charAt(j) == '/' && j + 1 < n && s.charAt(j + 1) == '*') {
                depth++;
                j += 2;
            } else if (s.charAt(j) == '*' && j + 1 < n && s.charAt(j + 1) == '/') {
                depth--;
                j += 2;
            } else {
                j++;
            }
        }
        return j;
    }

    /** {@code $tag$ … $tag$}; a lone {@code $} or {@code $1} is a plain char. */
    private static int skipDollarQuoted(String s, int i) {
        int n = s.length();
        if (i > 0 && (Character.isLetterOrDigit(s.charAt(i - 1)) || s.charAt(i - 1) == '_' || s.charAt(i - 1) == '$')) {
            return i + 1; // part of an identifier such as a$b
        }
        int j = i + 1;
        if (j < n && (Character.isLetter(s.charAt(j)) || s.charAt(j) == '_')) {
            while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
                j++;
            }
        }
        if (j >= n || s.charAt(j) != '$') {
            return i + 1;
        }
        String tag = s.substring(i, j + 1); // includes both '$'
        int close = s.indexOf(tag, j + 1);
        return close < 0 ? i + 1 : close + tag.length();
    }

    /** A line made only of GO (any case) and an optional repeat count / trailing comment. */
    private static boolean isGoLine(String s, int i) {
        int n = s.length();
        if (i + 1 >= n || (s.charAt(i + 1) != 'o' && s.charAt(i + 1) != 'O')) {
            return false;
        }
        for (int j = i - 1; j >= 0 && s.charAt(j) != '\n'; j--) {
            if (!Character.isWhitespace(s.charAt(j))) {
                return false;
            }
        }
        int j = i + 2;
        while (j < n && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) {
            j++;
        }
        while (j < n && Character.isDigit(s.charAt(j))) {
            j++;
        }
        while (j < n && (s.charAt(j) == ' ' || s.charAt(j) == '\t' || s.charAt(j) == '\r')) {
            j++;
        }
        if (j + 1 < n && s.charAt(j) == '-' && s.charAt(j + 1) == '-') {
            j = skipLineComment(s, j);
        }
        return j >= n || s.charAt(j) == '\n';
    }

    /** Index just after the end-of-line that ends the line containing {@code i}. */
    private static int endOfLine(String s, int i) {
        int j = s.indexOf('\n', i);
        return j < 0 ? s.length() : j + 1;
    }
}
