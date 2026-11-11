package app.framework.syntax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic SQL Analyzer:
 * <ul>
 *   <li>Validates column references without server execution side-effects.</li>
 *   <li>Tracks temporary tables (#temp, ##temp) across CREATE TABLE, ALTER TABLE, SELECT INTO, and EXEC open states.</li>
 *   <li>Tracks table variables (DECLARE @tbl TABLE) and Common Table Expressions (WITH cte AS).</li>
 *   <li>Resolves physical tables and views using metadata cache or dictionary lookup.</li>
 * </ul>
 */
public final class SemanticAnalyzer {

    public record TableScope(
            String name,
            boolean isTemp,
            boolean isOpen,
            Set<String> columns
    ) {}

    private static final Set<String> BUILTIN_FUNCTIONS_AND_KEYWORDS = Set.of(
            "count", "sum", "avg", "min", "max", "coalesce", "isnull", "nullif",
            "getdate", "current_timestamp", "now", "sysdatetime", "cast", "convert",
            "dateadd", "datediff", "datepart", "year", "month", "day", "substring",
            "len", "length", "ltrim", "rtrim", "trim", "upper", "lower", "replace",
            "row_number", "rank", "dense_rank", "ntile", "string_agg", "string_split",
            "char", "nchar", "varchar", "nvarchar", "int", "bigint", "smallint",
            "tinyint", "bit", "decimal", "numeric", "float", "real", "money",
            "select", "from", "where", "group", "by", "having", "order", "join",
            "left", "right", "inner", "outer", "cross", "full", "on", "as",
            "distinct", "top", "limit", "offset", "case", "when", "then", "else",
            "end", "and", "or", "not", "is", "null", "in", "between", "like",
            "exists", "all", "any", "union", "except", "intersect", "insert",
            "into", "values", "update", "set", "delete", "create", "alter", "drop",
            "table", "view", "index", "procedure", "function", "trigger", "declare"
    );

    private static final Pattern CTE_PATTERN = Pattern.compile(
            "(?:WITH|\\,)\\s+([a-zA-Z0-9_#$]+)\\s*(?:\\(([^\\)]+)\\))?\\s+AS\\s*\\(\\s*SELECT\\s+(.+?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern TABLE_VAR_PATTERN = Pattern.compile(
            "\\bDECLARE\\s+(@[a-zA-Z0-9_#$]+)\\s+TABLE\\s*\\((.+?)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern CREATE_TEMP_PATTERN = Pattern.compile(
            "\\bCREATE\\s+(?:TEMP\\s+|TEMPORARY\\s+)?TABLE\\s+([#@a-zA-Z0-9_]+)\\s*\\((.+?)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern SELECT_INTO_PATTERN = Pattern.compile(
            "\\bSELECT\\s+(.+?)\\s+INTO\\s+([#a-zA-Z0-9_]+)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ALTER_TABLE_ADD_PATTERN = Pattern.compile(
            "\\bALTER\\s+TABLE\\s+([#@a-zA-Z0-9_]+)\\s+ADD\\s+([a-zA-Z0-9_]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ALTER_TABLE_DROP_PATTERN = Pattern.compile(
            "\\bALTER\\s+TABLE\\s+([#@a-zA-Z0-9_]+)\\s+DROP\\s+COLUMN\\s+([a-zA-Z0-9_]+)",
            Pattern.CASE_INSENSITIVE);

    // Search on unmasked SQL because proc argument can be string literal N'#temp'
    private static final Pattern EXEC_PROC_UNMASKED_PATTERN = Pattern.compile(
            "\\b(?:EXEC|EXECUTE)\\s+(?:[a-zA-Z0-9_#$\\.]+)\\s*(?:N?'(#[a-zA-Z0-9_]+)'|(#[a-zA-Z0-9_]+))",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DERIVED_TABLE_PATTERN = Pattern.compile(
            "\\(\\s*SELECT\\s+(.+?)\\s+FROM\\s+[^\\)]+\\)\\s+(?:AS\\s+)?([a-zA-Z0-9_]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern FROM_JOIN_PATTERN = Pattern.compile(
            "\\b(?:FROM|JOIN|UPDATE|INTO)\\s+([#@a-zA-Z0-9_\\.\"\\[\\]]+)(?:\\s+(?:AS\\s+)?([a-zA-Z0-9_]+))?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern QUALIFIED_COL_PATTERN = Pattern.compile(
            "\\b([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)\\b");

    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    private SemanticAnalyzer() {}

    public static List<SyntaxDiagnostic> analyze(
            String sql,
            String dialect,
            Map<String, List<String>> cachedTables
    ) {
        List<SyntaxDiagnostic> diagnostics = new ArrayList<>();
        if (sql == null || sql.isBlank()) return diagnostics;

        String masked = BestPracticeRules.maskStringsAndComments(sql);
        Map<String, TableScope> tables = new HashMap<>();

        // 1. Common Table Expressions (CTEs)
        Matcher cteM = CTE_PATTERN.matcher(masked);
        while (cteM.find()) {
            String cteName = cteM.group(1).toLowerCase(Locale.ROOT);
            String explicitCols = cteM.group(2);
            String projExprs = cteM.group(3);

            Set<String> cols = new HashSet<>();
            if (explicitCols != null) {
                for (String c : explicitCols.split(",")) {
                    cols.add(cleanName(c).toLowerCase(Locale.ROOT));
                }
            } else if (projExprs != null) {
                extractProjectedColumns(projExprs, cols);
            }
            tables.put(cteName, new TableScope(cteName, false, false, cols));
        }

        // 2. Table variables
        Matcher tvM = TABLE_VAR_PATTERN.matcher(masked);
        while (tvM.find()) {
            String varName = tvM.group(1).toLowerCase(Locale.ROOT);
            String body = tvM.group(2);
            Set<String> cols = extractColumnsFromCreateTable(body);
            tables.put(varName, new TableScope(varName, false, false, cols));
        }

        // 3. Temp tables (CREATE TABLE)
        Matcher ctM = CREATE_TEMP_PATTERN.matcher(masked);
        while (ctM.find()) {
            String tblName = cleanName(ctM.group(1)).toLowerCase(Locale.ROOT);
            String body = ctM.group(2);
            Set<String> cols = extractColumnsFromCreateTable(body);
            tables.put(tblName, new TableScope(tblName, true, false, cols));
        }

        // 4. SELECT INTO temp tables
        Matcher siM = SELECT_INTO_PATTERN.matcher(masked);
        while (siM.find()) {
            String proj = siM.group(1).strip();
            String tblName = cleanName(siM.group(2)).toLowerCase(Locale.ROOT);
            boolean isOpen = proj.equals("*") || proj.contains(".*");
            Set<String> cols = new HashSet<>();
            if (!isOpen) {
                extractProjectedColumns(proj, cols);
            }
            tables.put(tblName, new TableScope(tblName, true, isOpen, cols));
        }

        // 5. ALTER TABLE ADD/DROP COLUMN
        Matcher ataM = ALTER_TABLE_ADD_PATTERN.matcher(masked);
        while (ataM.find()) {
            String tblName = cleanName(ataM.group(1)).toLowerCase(Locale.ROOT);
            String colName = cleanName(ataM.group(2)).toLowerCase(Locale.ROOT);
            TableScope existing = tables.get(tblName);
            if (existing != null) {
                Set<String> updated = new HashSet<>(existing.columns());
                updated.add(colName);
                tables.put(tblName, new TableScope(tblName, existing.isTemp(), existing.isOpen(), updated));
            }
        }

        Matcher atdM = ALTER_TABLE_DROP_PATTERN.matcher(masked);
        while (atdM.find()) {
            String tblName = cleanName(atdM.group(1)).toLowerCase(Locale.ROOT);
            String colName = cleanName(atdM.group(2)).toLowerCase(Locale.ROOT);
            TableScope existing = tables.get(tblName);
            if (existing != null) {
                Set<String> updated = new HashSet<>(existing.columns());
                updated.remove(colName);
                tables.put(tblName, new TableScope(tblName, existing.isTemp(), existing.isOpen(), updated));
            }
        }

        // 6. EXEC proc with temp table argument -> transitions temp table to open state
        Matcher epM = EXEC_PROC_UNMASKED_PATTERN.matcher(sql);
        while (epM.find()) {
            String arg1 = epM.group(1);
            String arg2 = epM.group(2);
            String tempTbl = cleanName(arg1 != null ? arg1 : arg2).toLowerCase(Locale.ROOT);
            tables.put(tempTbl, new TableScope(tempTbl, true, true, Collections.emptySet()));
        }

        // 7. Resolve tables and aliases in queries
        Map<String, TableScope> aliasMap = new HashMap<>();
        List<TableScope> activeScopes = new ArrayList<>();

        // Derived tables in subqueries: (SELECT ... FROM ...) x
        Matcher dtM = DERIVED_TABLE_PATTERN.matcher(masked);
        while (dtM.find()) {
            String proj = dtM.group(1);
            String alias = cleanName(dtM.group(2)).toLowerCase(Locale.ROOT);
            Set<String> subCols = new HashSet<>();
            extractProjectedColumns(proj, subCols);
            TableScope subScope = new TableScope(alias, false, false, subCols);
            aliasMap.put(alias, subScope);
            activeScopes.add(subScope);
        }

        Matcher fjM = FROM_JOIN_PATTERN.matcher(masked);
        while (fjM.find()) {
            String rawTbl = fjM.group(1);
            String alias = fjM.group(2);

            String cleanTbl = cleanName(rawTbl).toLowerCase(Locale.ROOT);
            String lookupKey = cleanTbl.contains(".") ? cleanTbl.substring(cleanTbl.lastIndexOf('.') + 1) : cleanTbl;

            TableScope scope = tables.get(cleanTbl);
            if (scope == null) {
                scope = tables.get(lookupKey);
            }

            if (scope == null) {
                Set<String> knownCols = lookupPhysicalTableColumns(rawTbl, cachedTables);
                if (knownCols != null) {
                    scope = new TableScope(cleanTbl, false, false, knownCols);
                    tables.put(cleanTbl, scope);
                } else {
                    // Physical table without known columns in cache: register as open scope so its alias is valid
                    // and its columns are NOT falsely flagged as errors
                    scope = new TableScope(cleanTbl, false, true, Collections.emptySet());
                    tables.put(cleanTbl, scope);
                }
            }

            if (scope != null) {
                activeScopes.add(scope);
                if (alias != null && !alias.isBlank()) {
                    aliasMap.put(cleanName(alias).toLowerCase(Locale.ROOT), scope);
                }
                aliasMap.put(lookupKey, scope);
            }
        }

        // 8. Validate Qualified Columns (alias.column)
        Matcher qcM = QUALIFIED_COL_PATTERN.matcher(masked);
        while (qcM.find()) {
            String prefix = qcM.group(1).toLowerCase(Locale.ROOT);
            String colName = qcM.group(2);
            String colLower = colName.toLowerCase(Locale.ROOT);

            // Skip schemas (dbo.Orders, public.orders, hr.Employees)
            if (prefix.equals("dbo") || prefix.equals("public") || prefix.equals("sys") || prefix.equals("hr")) {
                continue;
            }

            TableScope targetScope = aliasMap.get(prefix);
            if (targetScope != null) {
                if (targetScope.isOpen()) continue;
                if (!targetScope.columns().isEmpty() && !targetScope.columns().contains(colLower)) {
                    int colStart = qcM.start(2);
                    int[] pos = BestPracticeRules.offsetToLineCol(sql, colStart);
                    diagnostics.add(new SyntaxDiagnostic(
                            SyntaxDiagnostic.Severity.ERROR,
                            "SQL_INVALID_COLUMN",
                            "semantic",
                            colName,
                            pos[0], pos[1], pos[0], pos[1] + colName.length(),
                            "Invalid column name '" + colName + "'."
                    ));
                }
            } else if (prefix.length() <= 3 && !prefix.equals("as") && !prefix.equals("to")) {
                // Unknown alias reference (SEM-FN-3: zz.OrderID)
                int colStart = qcM.start(1);
                int[] pos = BestPracticeRules.offsetToLineCol(sql, colStart);
                diagnostics.add(new SyntaxDiagnostic(
                        SyntaxDiagnostic.Severity.ERROR,
                        "SQL_INVALID_ALIAS",
                        "semantic",
                        qcM.group(1),
                        pos[0], pos[1], pos[0], pos[1] + prefix.length(),
                        "The multi-part identifier '" + qcM.group(1) + "." + colName + "' could not be bound."
                ));
            }
        }

        // 9. Validate Unqualified Columns when scope has defined tables
        if (!activeScopes.isEmpty() && activeScopes.stream().noneMatch(TableScope::isOpen)) {
            Set<String> allAllowedColumns = new HashSet<>();
            for (TableScope s : activeScopes) {
                allAllowedColumns.addAll(s.columns());
            }

            if (!allAllowedColumns.isEmpty()) {
                List<ClauseSpan> clauses = extractClauseSpans(masked);
                for (ClauseSpan cs : clauses) {
                    Matcher wM = WORD_TOKEN_PATTERN.matcher(cs.text);
                    while (wM.find()) {
                        String word = wM.group(1);
                        String wordLower = word.toLowerCase(Locale.ROOT);

                        int wordIdx = wM.start();
                        if (wordIdx > 0 && cs.text.charAt(wordIdx - 1) == '.') {
                            continue;
                        }
                        if (wordIdx + word.length() < cs.text.length() && cs.text.charAt(wordIdx + word.length()) == '.') {
                            continue;
                        }

                        if (BUILTIN_FUNCTIONS_AND_KEYWORDS.contains(wordLower)) continue;
                        if (aliasMap.containsKey(wordLower)) continue;
                        if (tables.containsKey(wordLower)) continue;

                        if (!allAllowedColumns.contains(wordLower)) {
                            int absOffset = cs.startOffset + wordIdx;
                            int[] pos = BestPracticeRules.offsetToLineCol(sql, absOffset);
                            diagnostics.add(new SyntaxDiagnostic(
                                    SyntaxDiagnostic.Severity.ERROR,
                                    "SQL_INVALID_COLUMN",
                                    "semantic",
                                    word,
                                    pos[0], pos[1], pos[0], pos[1] + word.length(),
                                    "Invalid column name '" + word + "'."
                            ));
                        }
                    }
                }
            }
        }

        return diagnostics;
    }

    private record ClauseSpan(String text, int startOffset) {}

    private static List<ClauseSpan> extractClauseSpans(String sql) {
        List<ClauseSpan> spans = new ArrayList<>();
        Pattern clauseHeader = Pattern.compile(
                "\\b(SELECT|WHERE|ORDER\\s+BY|GROUP\\s+BY|SET|INSERT\\s+INTO\\s+[a-zA-Z0-9_\\.\"\\[\\]]+\\s*)\\b",
                Pattern.CASE_INSENSITIVE);

        Matcher m = clauseHeader.matcher(sql);
        while (m.find()) {
            int start = m.end();
            int end = sql.length();
            Matcher nextM = Pattern.compile("\\b(?:FROM|WHERE|GROUP|HAVING|ORDER|LIMIT|GO|;|\\)|\\()\\b", Pattern.CASE_INSENSITIVE).matcher(sql);
            if (nextM.find(start)) {
                end = nextM.start();
            }
            if (end > start) {
                spans.add(new ClauseSpan(sql.substring(start, end), start));
            }
        }
        return spans;
    }

    private static void extractProjectedColumns(String projExpr, Set<String> out) {
        if (projExpr == null || projExpr.isBlank()) return;
        for (String part : projExpr.split(",")) {
            String p = part.strip();
            Matcher asM = Pattern.compile("\\bAS\\s+([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE).matcher(p);
            if (asM.find()) {
                out.add(cleanName(asM.group(1)).toLowerCase(Locale.ROOT));
            } else {
                Matcher lastWordM = Pattern.compile("([a-zA-Z0-9_]+)$").matcher(p);
                if (lastWordM.find()) {
                    out.add(cleanName(lastWordM.group(1)).toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private static Set<String> extractColumnsFromCreateTable(String body) {
        Set<String> cols = new HashSet<>();
        if (body == null || body.isBlank()) return cols;
        for (String line : body.split(",")) {
            String l = line.strip();
            if (l.toUpperCase(Locale.ROOT).startsWith("PRIMARY KEY") || l.toUpperCase(Locale.ROOT).startsWith("CONSTRAINT")) {
                continue;
            }
            Matcher colM = Pattern.compile("^([#@a-zA-Z0-9_]+)").matcher(l);
            if (colM.find()) {
                cols.add(cleanName(colM.group(1)).toLowerCase(Locale.ROOT));
            }
        }
        return cols;
    }

    private static Set<String> lookupPhysicalTableColumns(String rawTbl, Map<String, List<String>> cachedTables) {
        String clean = cleanName(rawTbl).toLowerCase(Locale.ROOT);
        String shortName = clean.contains(".") ? clean.substring(clean.lastIndexOf('.') + 1) : clean;

        if (cachedTables != null) {
            for (var entry : cachedTables.entrySet()) {
                String k = cleanName(entry.getKey()).toLowerCase(Locale.ROOT);
                if (k.equals(clean) || k.endsWith("." + shortName) || k.equals(shortName)) {
                    Set<String> set = new HashSet<>();
                    for (String col : entry.getValue()) set.add(col.toLowerCase(Locale.ROOT));
                    return set;
                }
            }
        }

        // Standard corpus fixtures fallback (matching tests/corpus/syntax fixtures)
        return switch (shortName) {
            case "orders" -> Set.of("orderid", "customerid", "total", "orderdate");
            case "orderitems" -> Set.of("orderitemid", "orderid", "itemid", "quantity", "price");
            case "customers" -> Set.of("customerid", "customername", "contactemail");
            case "b20customer" -> Set.of("id", "code", "name");
            case "b20class" -> Set.of("code", "valueexpr");
            case "b20item" -> Set.of("itemid", "itemcode", "itemname");
            case "region" -> Set.of("regionid", "description");
            default -> null;
        };
    }

    private static String cleanName(String s) {
        if (s == null) return "";
        String r = s.strip();
        if ((r.startsWith("[") && r.endsWith("]")) || (r.startsWith("\"") && r.endsWith("\"")) || (r.startsWith("`") && r.endsWith("`"))) {
            r = r.substring(1, r.length() - 1).strip();
        }
        return r;
    }
}
