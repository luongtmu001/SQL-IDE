package app.framework.syntax;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Procedural SQL Detector and Extractor:
 * <ul>
 *   <li>Identifies stored procedures, functions, triggers, and procedural execution blocks.</li>
 *   <li>Extracts start lines to accurately map line offsets for procedure syntax errors.</li>
 *   <li>Extracts routine parameters and local variables for semantic whitelist checking.</li>
 * </ul>
 */
public final class ProcBodyDetector {

    public record RoutineInfo(
            String name,
            String cleanName,
            String type,
            int startLine,
            Set<String> parameters,
            Set<String> localVariables
    ) {}

    private static final Pattern ROUTINE_START_PATTERN = Pattern.compile(
            "\\b(?:CREATE(?:\\s+OR\\s+(?:ALTER|REPLACE))?|ALTER)\\s+(PROCEDURE|PROC|FUNCTION|TRIGGER)\\s+([#@\\w\\.\"\\[\\]]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PG_DO_PATTERN = Pattern.compile(
            "\\bDO\\s+(?:LANGUAGE\\s+[a-zA-Z0-9_]+\\s+)?(\\$[a-zA-Z0-9_]*\\$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TSQL_PARAM_PATTERN = Pattern.compile(
            "(@[a-zA-Z0-9_#$]+)\\s+(?:AS\\s+)?([a-zA-Z0-9_#$]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TSQL_DECLARE_PATTERN = Pattern.compile(
            "\\bDECLARE\\s+(@[a-zA-Z0-9_#$]+)",
            Pattern.CASE_INSENSITIVE);

    private ProcBodyDetector() {}

    public static boolean isProcedural(String sql, String dialect) {
        if (sql == null || sql.isBlank()) return false;
        String d = dialect != null ? dialect.toLowerCase(Locale.ROOT) : "sqlserver";
        if (d.contains("postgres") || d.contains("pgsql")) {
            return ROUTINE_START_PATTERN.matcher(sql).find() || PG_DO_PATTERN.matcher(sql).find();
        }
        return ROUTINE_START_PATTERN.matcher(sql).find();
    }

    public static List<RoutineInfo> extractRoutines(String sql, String dialect) {
        List<RoutineInfo> routines = new ArrayList<>();
        if (sql == null || sql.isBlank()) return routines;

        Matcher m = ROUTINE_START_PATTERN.matcher(sql);
        while (m.find()) {
            String type = m.group(1).toUpperCase(Locale.ROOT);
            if ("PROC".equals(type)) type = "PROCEDURE";
            String rawName = m.group(2);
            String cleanName = cleanIdentifier(rawName);

            // Calculate 1-based start line
            int startIdx = m.start();
            int startLine = countLines(sql, startIdx) + 1;

            Set<String> params = new HashSet<>();
            Set<String> localVars = new HashSet<>();

            // Find AS or BEGIN to separate parameters from body in T-SQL
            String afterHeader = sql.substring(m.end());
            Matcher asMatcher = Pattern.compile("\\b(?:AS|RETURNS)\\b", Pattern.CASE_INSENSITIVE).matcher(afterHeader);
            String headerSection = afterHeader;
            String bodySection = "";
            if (asMatcher.find()) {
                headerSection = afterHeader.substring(0, asMatcher.start());
                bodySection = afterHeader.substring(asMatcher.end());
            }

            Matcher paramMatcher = TSQL_PARAM_PATTERN.matcher(headerSection);
            while (paramMatcher.find()) {
                params.add(paramMatcher.group(1).toLowerCase(Locale.ROOT));
            }

            Matcher declareMatcher = TSQL_DECLARE_PATTERN.matcher(bodySection);
            while (declareMatcher.find()) {
                localVars.add(declareMatcher.group(1).toLowerCase(Locale.ROOT));
            }

            routines.add(new RoutineInfo(rawName, cleanName, type, startLine, params, localVars));
        }

        return routines;
    }

    private static String cleanIdentifier(String s) {
        if (s == null) return "";
        String r = s.strip();
        if ((r.startsWith("[") && r.endsWith("]")) || (r.startsWith("\"") && r.endsWith("\""))) {
            r = r.substring(1, r.length() - 1).strip();
        }
        return r;
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

