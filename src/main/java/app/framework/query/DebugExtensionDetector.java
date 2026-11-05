package app.framework.query;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds LLTool IDE debug extension calls ({@code bs.xxx(...)}) outside comments and strings. */
public final class DebugExtensionDetector {

    public record Occurrence(int line, String text) {
    }

    private static final Pattern CALL = Pattern.compile("(?i)\\bbs\\.[a-z_][a-z0-9_]*\\s*\\(");

    private DebugExtensionDetector() {
    }

    public static List<Occurrence> find(String sql) {
        List<Occurrence> out = new ArrayList<>();
        if (sql == null || sql.isEmpty()) return out;
        String[] lines = sql.split("\\R", -1);
        boolean inBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String code = stripCommentsAndStrings(lines[i], inBlock);
            inBlock = endsInBlockComment(lines[i], inBlock);
            Matcher m = CALL.matcher(code);
            if (m.find()) out.add(new Occurrence(i + 1, lines[i].trim()));
        }
        return out;
    }

    /** Prefixes every line that contains a bs.* call with "-- ". */
    public static String commentOut(String sql) {
        if (sql == null) return null;
        List<Occurrence> found = find(sql);
        if (found.isEmpty()) return sql;
        String[] lines = sql.split("\\R", -1);
        java.util.Set<Integer> targets = new java.util.HashSet<>();
        for (Occurrence o : found) targets.add(o.line() - 1);
        boolean inBlock = false;
        for (int i = 0; i < lines.length; i++) {
            boolean startedInBlock = inBlock;
            inBlock = endsInBlockComment(lines[i], inBlock);
            if (!targets.contains(i)) continue;
            int close = startedInBlock ? lines[i].indexOf("*/") : -1;
            // keep the end of a block comment outside the new line comment
            lines[i] = close >= 0
                    ? lines[i].substring(0, close + 2) + " -- " + lines[i].substring(close + 2)
                    : "-- " + lines[i];
        }
        return String.join("\n", lines);
    }

    private static String stripCommentsAndStrings(String line, boolean inBlock) {
        StringBuilder sb = new StringBuilder();
        boolean block = inBlock, str = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char n = i + 1 < line.length() ? line.charAt(i + 1) : 0;
            if (block) {
                if (c == '*' && n == '/') { block = false; i++; }
                continue;
            }
            if (str) {
                if (c == '\'') str = false;
                continue;
            }
            if (c == '-' && n == '-') break;
            if (c == '/' && n == '*') { block = true; i++; continue; }
            if (c == '\'') { str = true; continue; }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean endsInBlockComment(String line, boolean inBlock) {
        boolean block = inBlock, str = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char n = i + 1 < line.length() ? line.charAt(i + 1) : 0;
            if (block) {
                if (c == '*' && n == '/') { block = false; i++; }
                continue;
            }
            if (str) { if (c == '\'') str = false; continue; }
            if (c == '-' && n == '-') return false;
            if (c == '/' && n == '*') { block = true; i++; continue; }
            if (c == '\'') str = true;
        }
        return block;
    }
}
