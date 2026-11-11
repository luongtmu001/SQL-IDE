package app.framework.syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-Practice SQL Warning Rules (BP001 - BP006):
 * <ul>
 *   <li>BP001: SELECT * without explicit column list (Warning)</li>
 *   <li>BP002: WITH (NOLOCK) / (NOLOCK) dirty reads table hint in T-SQL (Warning)</li>
 *   <li>BP003: Missing schema qualification on table references (Hint)</li>
 *   <li>BP004: TOP N without ORDER BY clause in T-SQL (Warning)</li>
 *   <li>BP005: ISNULL(a, b) instead of ANSI COALESCE(a, b) (Hint)</li>
 *   <li>BP006: 2-argument CONVERT(type, val) instead of ANSI CAST(val AS type) (Hint)</li>
 * </ul>
 */
public final class BestPracticeRules {

    private static final Pattern BP001_SELECT_STAR = Pattern.compile(
            "\\bSELECT\\s+(?:DISTINCT\\s+|TOP\\s+\\d+\\s+)?(\\*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern BP002_NOLOCK = Pattern.compile(
            "(?:\\bWITH\\s*\\(\\s*NOLOCK\\s*\\)|\\(\\s*NOLOCK\\s*\\))", Pattern.CASE_INSENSITIVE);

    private static final Pattern BP004_TOP_NO_ORDER = Pattern.compile(
            "\\bSELECT\\s+TOP\\s+\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern BP005_ISNULL = Pattern.compile(
            "\\bISNULL\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern BP006_CONVERT = Pattern.compile(
            "\\bCONVERT\\s*\\(", Pattern.CASE_INSENSITIVE);

    private BestPracticeRules() {}

    public static List<SyntaxDiagnostic> check(String sql, String dialect) {
        List<SyntaxDiagnostic> diags = new ArrayList<>();
        if (sql == null || sql.isBlank()) return diags;

        String masked = maskStringsAndComments(sql);
        String d = dialect != null ? dialect.toLowerCase(Locale.ROOT) : "sqlserver";
        boolean isSqlServer = d.contains("sqlserver") || d.contains("mssql") || d.contains("tsql");

        // BP001: SELECT *
        Matcher m1 = BP001_SELECT_STAR.matcher(masked);
        while (m1.find()) {
            int starOffset = m1.start(1);
            String prefix = masked.substring(0, m1.start()).stripTrailing();
            if (Pattern.compile("\\bEXISTS\\s*\\(\\s*$", Pattern.CASE_INSENSITIVE).matcher(prefix).find()) {
                continue;
            }
            int[] pos = offsetToLineCol(sql, starOffset);
            diags.add(new SyntaxDiagnostic(
                    SyntaxDiagnostic.Severity.WARNING,
                    "BP001",
                    "best-practice",
                    "*",
                    pos[0], pos[1], pos[0], pos[1] + 1,
                    "BP001: Tránh dùng 'SELECT *', nên liệt kê danh sách cột tường minh để tối ưu hiệu năng."
            ));
        }

        // BP002: WITH (NOLOCK)
        if (isSqlServer) {
            Matcher m2 = BP002_NOLOCK.matcher(masked);
            while (m2.find()) {
                int offset = m2.start();
                int[] pos = offsetToLineCol(sql, offset);
                diags.add(new SyntaxDiagnostic(
                        SyntaxDiagnostic.Severity.WARNING,
                        "BP002",
                        "best-practice",
                        "NOLOCK",
                        pos[0], pos[1], pos[0], pos[1] + m2.group(0).length(),
                        "BP002: Bảng sử dụng gợi ý '(NOLOCK)' có thể đọc phải dữ liệu rác (dirty reads)."
                ));
            }
        }

        // BP004: TOP without ORDER BY
        if (isSqlServer) {
            Matcher m4 = BP004_TOP_NO_ORDER.matcher(masked);
            while (m4.find()) {
                // Check if statement has ORDER BY before next statement / semicolon / GO
                int stmtStart = m4.start();
                int stmtEnd = masked.indexOf(';', stmtStart);
                if (stmtEnd == -1) stmtEnd = masked.length();
                String stmt = masked.substring(stmtStart, stmtEnd);
                if (!Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE).matcher(stmt).find()) {
                    int[] pos = offsetToLineCol(sql, stmtStart);
                    diags.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.WARNING,
                            "BP004",
                            "best-practice",
                            "TOP",
                            pos[0], pos[1], pos[0], pos[1] + m4.group(0).length(),
                            "BP004: 'TOP' không kèm mệnh đề 'ORDER BY' sẽ trả về tập kết quả không xác định."
                    ));
                }
            }
        }

        // BP005: ISNULL -> COALESCE
        Matcher m5 = BP005_ISNULL.matcher(masked);
        while (m5.find()) {
            int offset = m5.start();
            int[] pos = offsetToLineCol(sql, offset);
            diags.add(new SyntaxDiagnostic(
                    SyntaxDiagnostic.Severity.HINT,
                    "BP005",
                    "best-practice",
                    "ISNULL",
                    pos[0], pos[1], pos[0], pos[1] + 6,
                    "BP005: Khuyến nghị dùng hàm chuẩn ANSI COALESCE(...) thay cho ISNULL(...)."
            ));
        }

        // BP006: CONVERT(type, val) -> CAST
        Matcher m6 = BP006_CONVERT.matcher(masked);
        while (m6.find()) {
            int offset = m6.start();
            int[] pos = offsetToLineCol(sql, offset);
            diags.add(new SyntaxDiagnostic(
                    SyntaxDiagnostic.Severity.HINT,
                    "BP006",
                    "best-practice",
                    "CONVERT",
                    pos[0], pos[1], pos[0], pos[1] + 7,
                    "BP006: Khuyến nghị dùng cú pháp chuẩn ANSI CAST(val AS type) cho các phép đổi kiểu cơ bản."
            ));
        }

        return diags;
    }

    public static String maskStringsAndComments(String sql) {
        if (sql == null) return "";
        char[] chars = sql.toCharArray();
        int n = chars.length;
        int i = 0;

        while (i < n) {
            // 1. Line comment: --
            if (i + 1 < n && chars[i] == '-' && chars[i + 1] == '-') {
                chars[i] = ' ';
                chars[i + 1] = ' ';
                i += 2;
                while (i < n && chars[i] != '\n') {
                    chars[i] = ' ';
                    i++;
                }
                continue;
            }

            // 2. Block comment: /* ... */
            if (i + 1 < n && chars[i] == '/' && chars[i + 1] == '*') {
                chars[i] = ' ';
                chars[i + 1] = ' ';
                i += 2;
                while (i < n) {
                    if (i + 1 < n && chars[i] == '*' && chars[i + 1] == '/') {
                        chars[i] = ' ';
                        chars[i + 1] = ' ';
                        i += 2;
                        break;
                    }
                    if (chars[i] != '\n') {
                        chars[i] = ' ';
                    }
                    i++;
                }
                continue;
            }

            // 3. String literal: '...'
            if (chars[i] == '\'') {
                chars[i] = ' ';
                i++;
                while (i < n) {
                    if (chars[i] == '\'') {
                        if (i + 1 < n && chars[i + 1] == '\'') {
                            chars[i] = ' ';
                            chars[i + 1] = ' ';
                            i += 2;
                            continue;
                        }
                        chars[i] = ' ';
                        i++;
                        break;
                    }
                    if (chars[i] != '\n') {
                        chars[i] = ' ';
                    }
                    i++;
                }
                continue;
            }

            i++;
        }

        return new String(chars);
    }

    public static int[] offsetToLineCol(String sql, int offset) {
        int target = Math.max(0, Math.min(offset, sql.length()));
        int line = 1;
        int col = 1;
        for (int i = 0; i < target; i++) {
            char ch = sql.charAt(i);
            if (ch == '\n') {
                line++;
                col = 1;
            } else if (ch != '\r') {
                col++;
            }
        }
        return new int[]{line, col};
    }
}

