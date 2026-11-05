package app.framework.query;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only / production guard (port of LLTool {@code app/utils/sql_safety.py}:
 * {@code classify_statement} + {@code check_query_safety}).
 *
 * <p>LLTool parses with sqlglot and falls back to a keyword regex. There is no SQL parser in the
 * Java app yet, so this class works on the token stream after removing comments, string literals
 * and quoted identifiers. It is deliberately strict: anything that may write is "modifying".
 * Improvements over the Python version: quoted identifiers ({@code "x"}, {@code [x]}, backticks)
 * and dollar-quoted strings are removed before scanning, {@code EXPLAIN ANALYZE} of a modifying
 * statement is modifying (it executes the statement), and server / permission commands
 * (GRANT, BACKUP, DBCC, VACUUM, COPY, …) count as writes.</p>
 */
public final class SqlStatementClassifier {

    public enum Kind { EMPTY, SELECT, READ_ONLY, DML, DDL, EXEC, ADMIN }

    public record Classification(boolean readOnly, Kind kind, List<String> actions) {
    }

    /** {@code allowed=false} and {@code needsConfirmation=true}: ask (PROD), then run with confirmed=true. */
    public record Decision(boolean allowed, boolean needsConfirmation, String errorCode, String message,
                           Classification classification) {
    }

    private static final Set<String> DML = Set.of("INSERT", "UPDATE", "DELETE", "MERGE", "TRUNCATE", "REPLACE", "UPSERT");
    private static final Set<String> DDL = Set.of("CREATE", "ALTER", "DROP", "RENAME", "COMMENT");
    private static final Set<String> EXEC = Set.of("EXEC", "EXECUTE", "CALL", "DO");
    private static final Set<String> ADMIN = Set.of("GRANT", "REVOKE", "DENY", "BACKUP", "RESTORE", "DBCC",
            "BULK", "COPY", "VACUUM", "REINDEX", "CLUSTER", "REFRESH", "ATTACH", "DETACH", "PRAGMA", "LOCK",
            "IMPORT", "REASSIGN", "SECURITY", "SHUTDOWN", "KILL", "RECONFIGURE", "CHECKPOINT", "ANALYZE");

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_$#@]*");

    private SqlStatementClassifier() {
    }

    public static Classification classify(String sql) {
        if (sql == null || sql.isBlank()) return new Classification(true, Kind.EMPTY, List.of());
        String clean = stripCommentsStringsAndIdentifiers(sql);
        List<String> words = new ArrayList<>();
        Matcher m = WORD.matcher(clean);
        while (m.find()) words.add(m.group().toUpperCase(Locale.ROOT));
        if (words.isEmpty()) return new Classification(true, Kind.EMPTY, List.of());

        Set<String> actions = new LinkedHashSet<>();
        Kind kind = null;
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            String prev = i > 0 ? words.get(i - 1) : "";
            String next = i + 1 < words.size() ? words.get(i + 1) : "";
            // tokens that are not statements in this position
            if ((w.equals("UPDATE") || w.equals("DELETE")) && prev.equals("ON")) continue; // FK actions inside DDL
            if (w.equals("WRITE") && prev.equals("READ")) {                 // SET … / BEGIN … READ WRITE
                actions.add("READ WRITE");
                kind = max(kind, Kind.ADMIN);
                continue;
            }
            if (w.endsWith("TRANSACTION_READ_ONLY")) {                       // SET [default_]transaction_read_only
                actions.add(w);
                kind = max(kind, Kind.ADMIN);
                continue;
            }
            if (w.equals("UPDATE") && prev.equals("FOR")) {
                // SELECT … FOR UPDATE takes write locks (refused by read-only PostgreSQL): still a write intent
                actions.add("FOR UPDATE");
                kind = max(kind, Kind.DML);
                continue;
            }
            if (w.equals("ANALYZE") && prev.equals("EXPLAIN")) continue;    // handled by the inner statement
            if (w.equals("REPLACE") && (next.isEmpty() || !next.equals("INTO"))) {
                if (!prev.equals("OR")) continue;                           // REPLACE() function; CREATE OR REPLACE caught by CREATE
            }
            if (w.equals("COMMENT") && !next.equals("ON")) continue;
            if (w.equals("SECURITY") && !next.equals("LABEL")) continue;
            if (w.equals("LOCK") && !(next.equals("TABLE") || i == 0 || prev.equals("BEGIN"))) continue;
            if (w.equals("DO") && !(i == 0 || isStatementStart(prev))) continue;
            if (w.equals("ANALYZE") && !(i == 0 || isStatementStart(prev))) continue;
            if (w.equals("COPY") && !(i == 0 || isStatementStart(prev))) continue;

            if (DML.contains(w)) { actions.add(w); kind = max(kind, Kind.DML); }
            else if (DDL.contains(w)) { actions.add(w); kind = max(kind, Kind.DDL); }
            else if (EXEC.contains(w)) { actions.add(w); kind = max(kind, Kind.EXEC); }
            else if (ADMIN.contains(w)) { actions.add(w); kind = max(kind, Kind.ADMIN); }
        }
        // SELECT … INTO (T-SQL creates a table, PostgreSQL CREATE TABLE AS); INSERT INTO is DML already
        for (int i = 0; i < words.size(); i++) {
            if (!words.get(i).equals("INTO")) continue;
            String before = i > 0 ? words.get(i - 1) : "";
            if (before.equals("INSERT") || before.equals("MERGE") || before.equals("REPLACE")) continue;
            if (hasSelectBefore(words, i) && !actions.contains("INSERT")) {
                actions.add("SELECT_INTO");
                kind = max(kind, Kind.DDL);
            }
        }
        if (kind == null) {
            String first = words.get(0);
            return new Classification(true, first.equals("SELECT") || first.equals("WITH") ? Kind.SELECT : Kind.READ_ONLY, List.of());
        }
        return new Classification(false, kind, List.copyOf(actions));
    }

    /**
     * @param readOnly    profile read-only or the global "all read-only" setting
     * @param environment profile environment ("PROD" asks before any write)
     * @param confirmed   the user confirmed the PRODUCTION dialog
     */
    public static Decision check(String sql, boolean readOnly, String environment, boolean confirmed) {
        Classification c = classify(sql);
        if (readOnly && !c.readOnly()) {
            String acts = c.actions().isEmpty() ? "sửa đổi dữ liệu" : String.join(", ", c.actions());
            return new Decision(false, false, "ERR_READ_ONLY",
                    "Kết nối đang ở chế độ CHỈ ĐỌC. Lệnh bị chặn vì có thao tác ghi: " + acts + ".", c);
        }
        String env = environment != null ? environment.trim().toLowerCase(Locale.ROOT) : "";
        if ((env.equals("prod") || env.equals("production")) && !c.readOnly() && !confirmed) {
            String acts = c.actions().isEmpty() ? "DML / DDL" : String.join(", ", c.actions());
            return new Decision(false, true, "ERR_PROD_CONFIRMATION_REQUIRED",
                    "Môi trường PRODUCTION: thao tác [" + acts + "] cần xác nhận trước khi chạy.", c);
        }
        return new Decision(true, false, null, null, c);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isStatementStart(String prev) {
        return prev.equals("BEGIN") || prev.equals("THEN") || prev.equals("ELSE") || prev.equals("GO") || prev.equals("AS");
    }

    private static boolean hasSelectBefore(List<String> words, int intoIndex) {
        for (int j = intoIndex - 1; j >= 0 && j >= intoIndex - 400; j--) {
            String w = words.get(j);
            if (w.equals("SELECT")) return true;
            if (w.equals("FROM") || w.equals("WHERE")) return false;
        }
        return false;
    }

    private static Kind max(Kind a, Kind b) {
        if (a == null) return b;
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /** Removes -- and /* *\/ comments, '…' / N'…' / $tag$…$tag$ strings and "…", […], `…` identifiers. */
    static String stripCommentsStringsAndIdentifiers(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            char d = i + 1 < n ? sql.charAt(i + 1) : '\0';
            if (c == '-' && d == '-') {
                while (i < n && sql.charAt(i) != '\n') i++;
                out.append(' ');
            } else if (c == '/' && d == '*') {
                int depth = 1;
                i += 2;
                while (i < n && depth > 0) {
                    if (sql.startsWith("/*", i)) { depth++; i += 2; }
                    else if (sql.startsWith("*/", i)) { depth--; i += 2; }
                    else i++;
                }
                out.append(' ');
            } else if (c == '\'') {
                i++;
                while (i < n) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < n && sql.charAt(i + 1) == '\'') { i += 2; continue; }
                        i++;
                        break;
                    }
                    i++;
                }
                out.append(" '' ");
            } else if (c == '"' || c == '`' || c == '[') {
                char close = c == '[' ? ']' : c;
                i++;
                while (i < n && sql.charAt(i) != close) i++;
                i++;
                out.append(" q ");
            } else if (c == '$') {
                int end = sql.indexOf('$', i + 1);
                String tag = end > i ? sql.substring(i, end + 1) : null;
                if (tag != null && tag.matches("\\$[A-Za-z_]*\\$")) {
                    int close = sql.indexOf(tag, end + 1);
                    i = close < 0 ? n : close + tag.length();
                    out.append(" '' ");
                } else {
                    out.append(c);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
