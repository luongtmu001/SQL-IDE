package com.lltool.core.application.objectsearch;

import com.lltool.core.domain.objectsearch.model.ObjectSearchHit;
import com.lltool.core.domain.objectsearch.model.ObjectSearchQuery;
import com.lltool.core.domain.objectsearch.model.SearchMatchLocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Service orchestrating Object Content Search inside SQL definitions (Views, Procedures, Functions, Triggers).
 * Provides regex pattern compilation, line/column coordinate extraction, catalog query generation,
 * and search execution for SQL Server, PostgreSQL, and Mock mode.
 */
public class ObjectSearchService {

    public Pattern compilePattern(String keyword, boolean caseSensitive, boolean wholeWord, boolean isRegex) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if (isRegex) {
            return Pattern.compile(keyword, flags);
        }
        if (wholeWord) {
            String patternStr = "(?<!\\w)" + Pattern.quote(keyword) + "(?!\\w)";
            return Pattern.compile(patternStr, flags);
        }
        return Pattern.compile(Pattern.quote(keyword), flags);
    }

    public static class TimedCharSequence implements CharSequence {
        private final CharSequence delegate;
        private final long deadlineNano;

        public TimedCharSequence(CharSequence delegate, long timeoutMillis) {
            this.delegate = delegate != null ? delegate : "";
            this.deadlineNano = System.nanoTime() + timeoutMillis * 1_000_000L;
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadlineNano) {
                throw new MatchTimeoutException("Regex evaluation timed out (> 2000ms)");
            }
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new TimedCharSequence(delegate.subSequence(start, end), Math.max(0, (deadlineNano - System.nanoTime()) / 1_000_000L));
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    public static class MatchTimeoutException extends RuntimeException {
        public MatchTimeoutException(String message) {
            super(message);
        }
    }

    public List<SearchMatchLocation> extractMatches(String text, Pattern pattern) {
        return extractMatches(text, pattern, 2000L);
    }

    public List<SearchMatchLocation> extractMatches(String text, Pattern pattern, long timeoutMillisPerObject) {
        List<SearchMatchLocation> locations = new ArrayList<>();
        if (text == null || text.isEmpty() || pattern == null) {
            return locations;
        }

        long deadline = System.nanoTime() + timeoutMillisPerObject * 1_000_000L;
        String[] lines = text.split("\r\n|\r|\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            if (System.nanoTime() > deadline) {
                throw new MatchTimeoutException("Regex evaluation exceeded timeout on line " + (lineIndex + 1));
            }
            String lineText = lines[lineIndex];
            TimedCharSequence timedLine = new TimedCharSequence(lineText, Math.max(1, (deadline - System.nanoTime()) / 1_000_000L));
            Matcher matcher = pattern.matcher(timedLine);
            while (matcher.find()) {
                if (System.nanoTime() > deadline) {
                    throw new MatchTimeoutException("Regex evaluation exceeded timeout during match loop on line " + (lineIndex + 1));
                }
                int col = matcher.start() + 1; // 1-based
                int len = matcher.end() - matcher.start();
                locations.add(new SearchMatchLocation(
                        lineIndex + 1,
                        col,
                        len,
                        lineText.trim()
                ));
                if (locations.size() >= 200) {
                    break; // Cap per object at 200 match locations
                }
            }
        }
        return locations;
    }

    public record SearchResultWithWarnings(
            List<ObjectSearchHit> hits,
            List<String> warnings,
            int encryptedObjectsSkipped,
            boolean truncated
    ) {}

    public SearchResultWithWarnings searchWithWarnings(Connection conn, String engine, ObjectSearchQuery query) throws Exception {
        if (query == null || query.getKeyword() == null || query.getKeyword().trim().isEmpty()) {
            return new SearchResultWithWarnings(Collections.emptyList(), Collections.emptyList(), 0, false);
        }

        Pattern pattern = compilePattern(query.getKeyword().trim(), query.isCaseSensitive(), query.isWholeWord(), query.isRegex());
        if (pattern == null) {
            return new SearchResultWithWarnings(Collections.emptyList(), Collections.emptyList(), 0, false);
        }

        boolean isPg = engine != null && (engine.equalsIgnoreCase("postgresql") || engine.equalsIgnoreCase("postgres"));

        List<RawObjectDefinition> rawDefs;
        List<String> warnings = new ArrayList<>();
        int encryptedCount = 0;

        if (conn == null) {
            rawDefs = getMockDefinitions(query);
        } else if (isPg) {
            rawDefs = fetchPostgresDefinitions(conn, query, warnings);
        } else {
            FetchResult fr = fetchSqlServerDefinitionsWithStats(conn, query, warnings);
            rawDefs = fr.definitions();
            encryptedCount = fr.encryptedCount();
        }

        List<ObjectSearchHit> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int totalMatchesCount = 0;
        boolean truncated = false;

        for (RawObjectDefinition raw : rawDefs) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Tìm kiếm đã bị hủy");
            }
            if (raw.definition() == null || raw.definition().isEmpty()) continue;

            String key = (raw.schema() + "." + raw.name() + "|" + raw.type()).toLowerCase();
            if (seen.contains(key)) continue;
            seen.add(key);

            try {
                List<SearchMatchLocation> matches = extractMatches(raw.definition(), pattern);
                if (!matches.isEmpty()) {
                    hits.add(new ObjectSearchHit(
                            raw.catalog(),
                            raw.schema(),
                            raw.name(),
                            raw.type(),
                            matches.size(),
                            matches,
                            raw.definition()
                    ));
                    totalMatchesCount += matches.size();
                    if (hits.size() >= 500 || totalMatchesCount >= 5000) {
                        truncated = true;
                        warnings.add("Đã cắt bớt (đạt giới hạn 500 đối tượng hoặc 5.000 vị trí khớp), hãy thu hẹp từ khoá.");
                        break;
                    }
                }
            } catch (MatchTimeoutException mte) {
                warnings.add("Bỏ qua đối tượng " + raw.schema() + "." + raw.name() + " (" + raw.type() + "): " + mte.getMessage());
            }
        }

        // Sort: VIEW -> PROCEDURE -> FUNCTION -> TRIGGER, then schema, then name
        hits.sort((a, b) -> {
            int tA = typeSortOrder(a.getType());
            int tB = typeSortOrder(b.getType());
            if (tA != tB) return Integer.compare(tA, tB);
            int cmpSchema = a.getSchema().compareToIgnoreCase(b.getSchema());
            if (cmpSchema != 0) return cmpSchema;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return new SearchResultWithWarnings(hits, warnings, encryptedCount, truncated);
    }

    public List<ObjectSearchHit> search(Connection conn, String engine, ObjectSearchQuery query) throws Exception {
        return searchWithWarnings(conn, engine, query).hits();
    }

    private int typeSortOrder(String type) {
        if ("VIEW".equalsIgnoreCase(type)) return 1;
        if ("PROCEDURE".equalsIgnoreCase(type)) return 2;
        if ("FUNCTION".equalsIgnoreCase(type)) return 3;
        if ("TRIGGER".equalsIgnoreCase(type)) return 4;
        return 99;
    }

    public record RawObjectDefinition(String catalog, String schema, String name, String type, String definition) {}

    // === SQL SERVER QUERY EXECUTION ===

    public record FetchResult(List<RawObjectDefinition> definitions, int encryptedCount) {}

    private String extractSearchableLiteral(ObjectSearchQuery query) {
        if (query == null || query.getKeyword() == null) return null;
        String kw = query.getKeyword().trim();
        if (kw.isEmpty()) return null;
        if (!query.isRegex()) {
            return kw;
        }
        Pattern litPattern = Pattern.compile("[a-zA-Z0-9_]{3,}");
        Matcher m = litPattern.matcher(kw);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    private void appendSqlServerKeywordFilter(StringBuilder sb, List<Object> params, ObjectSearchQuery query) {
        String kw = extractSearchableLiteral(query);
        if (kw != null && !kw.isBlank()) {
            String escaped = kw.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
            String collation = query.isCaseSensitive() ? "Latin1_General_CS_AS" : "Latin1_General_CI_AI";
            sb.append("AND sm.definition COLLATE ").append(collation).append(" LIKE ? ");
            params.add("%" + escaped + "%");
        }
    }

    private FetchResult fetchSqlServerDefinitionsWithStats(Connection conn, ObjectSearchQuery query, List<String> warnings) {
        List<RawObjectDefinition> list = new ArrayList<>();
        int[] encryptedCount = new int[1];
        Set<String> requestedTypes = query.getObjectTypes();
        String catalog = query.getCatalog();
        String schema = query.getSchema();
        String dbPrefix = (catalog != null && !catalog.isBlank()) ? "[" + catalog.replace("]", "]]") + "]." : "";

        // 1. Views, Procedures, Functions
        List<String> typeCodes = new ArrayList<>();
        if (requestedTypes.contains("VIEW")) typeCodes.add("'V'");
        if (requestedTypes.contains("PROCEDURE")) {
            typeCodes.add("'P'");
            typeCodes.add("'PC'");
        }
        if (requestedTypes.contains("FUNCTION")) {
            typeCodes.add("'FN'");
            typeCodes.add("'IF'");
            typeCodes.add("'TF'");
            typeCodes.add("'FS'");
            typeCodes.add("'FT'");
        }

        if (!typeCodes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT s.name AS [schema], o.name AS [name], ")
              .append("CASE WHEN o.type = 'V' THEN 'VIEW' WHEN o.type IN ('P', 'PC') THEN 'PROCEDURE' ELSE 'FUNCTION' END AS [type], ")
              .append("sm.definition ")
              .append("FROM ").append(dbPrefix).append("sys.objects o ")
              .append("JOIN ").append(dbPrefix).append("sys.schemas s ON s.schema_id = o.schema_id ")
              .append("LEFT JOIN ").append(dbPrefix).append("sys.sql_modules sm ON sm.object_id = o.object_id ")
              .append("WHERE o.is_ms_shipped = 0 ")
              .append("AND o.type IN (").append(String.join(", ", typeCodes)).append(") ");

            List<Object> params = new ArrayList<>();
            if (schema != null && !schema.isBlank() && !schema.equals("*") && !schema.equalsIgnoreCase("all")) {
                sb.append("AND (s.name = ? OR LOWER(s.name) = LOWER(?)) ");
                params.add(schema);
                params.add(schema);
            }
            appendSqlServerKeywordFilter(sb, params, query);

            executeQueryWithWarnings(conn, sb.toString(), params, catalog, list, warnings, "Views/Procedures/Functions", encryptedCount);
        }

        // 2. Triggers
        if (requestedTypes.contains("TRIGGER")) {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT s.name AS [schema], tr.name AS [name], 'TRIGGER' AS [type], sm.definition ")
              .append("FROM ").append(dbPrefix).append("sys.triggers tr ")
              .append("JOIN ").append(dbPrefix).append("sys.objects parent ON parent.object_id = tr.parent_id ")
              .append("JOIN ").append(dbPrefix).append("sys.schemas s ON s.schema_id = parent.schema_id ")
              .append("LEFT JOIN ").append(dbPrefix).append("sys.sql_modules sm ON sm.object_id = tr.object_id ")
              .append("WHERE tr.is_ms_shipped = 0 ");

            List<Object> params = new ArrayList<>();
            if (schema != null && !schema.isBlank() && !schema.equals("*") && !schema.equalsIgnoreCase("all")) {
                sb.append("AND (s.name = ? OR LOWER(s.name) = LOWER(?)) ");
                params.add(schema);
                params.add(schema);
            }
            appendSqlServerKeywordFilter(sb, params, query);

            executeQueryWithWarnings(conn, sb.toString(), params, catalog, list, warnings, "Triggers", encryptedCount);
        }

        return new FetchResult(list, encryptedCount[0]);
    }

    private List<RawObjectDefinition> fetchSqlServerDefinitions(Connection conn, ObjectSearchQuery query) {
        return fetchSqlServerDefinitionsWithStats(conn, query, new ArrayList<>()).definitions();
    }

    // === POSTGRESQL QUERY EXECUTION ===

    private void appendPostgresKeywordFilter(StringBuilder sb, List<Object> params, ObjectSearchQuery query, String expr) {
        String kw = extractSearchableLiteral(query);
        if (kw != null && !kw.isBlank()) {
            String escaped = kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String op = query.isCaseSensitive() ? "LIKE" : "ILIKE";
            sb.append("AND (").append(expr).append(" ").append(op).append(" ? ESCAPE '\\') ");
            params.add("%" + escaped + "%");
        }
    }

    private List<RawObjectDefinition> fetchPostgresDefinitions(Connection conn, ObjectSearchQuery query, List<String> warnings) {
        List<RawObjectDefinition> list = new ArrayList<>();
        Set<String> requestedTypes = query.getObjectTypes();
        String catalog = query.getCatalog();
        String schema = query.getSchema();
        String cleanSchema = (schema != null && !schema.isBlank() && !schema.equals("*") && !schema.equalsIgnoreCase("all")) ? schema : null;

        // 1. Routines (Functions & Procedures)
        if (requestedTypes.contains("FUNCTION") || requestedTypes.contains("PROCEDURE")) {
            List<String> typeClauses = new ArrayList<>();
            if (requestedTypes.contains("FUNCTION")) typeClauses.add("p.prokind IN ('f', 'w')");
            if (requestedTypes.contains("PROCEDURE")) typeClauses.add("p.prokind = 'p'");

            StringBuilder sb = new StringBuilder();
            sb.append("SELECT n.nspname AS schema, p.proname AS name, ")
              .append("CASE WHEN p.prokind = 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END AS type, ")
              .append("CASE WHEN l.lanname IN ('c', 'internal') THEN p.prosrc ")
              .append("ELSE COALESCE(pg_catalog.pg_get_functiondef(p.oid), p.prosrc) END AS definition ")
              .append("FROM pg_catalog.pg_proc p ")
              .append("JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace ")
              .append("LEFT JOIN pg_catalog.pg_language l ON l.oid = p.prolang ")
              .append("WHERE LEFT(n.nspname, 3) != 'pg_' AND n.nspname != 'information_schema' ")
              .append("AND (l.lanname IS NULL OR l.lanname NOT IN ('c', 'internal')) ");

            if (!typeClauses.isEmpty()) {
                sb.append("AND (").append(String.join(" OR ", typeClauses)).append(") ");
            }

            List<Object> params = new ArrayList<>();
            if (cleanSchema != null) {
                sb.append("AND (n.nspname = ? OR lower(n.nspname) = lower(?)) ");
                params.add(cleanSchema);
                params.add(cleanSchema);
            }
            appendPostgresKeywordFilter(sb, params, query, "COALESCE(pg_catalog.pg_get_functiondef(p.oid), p.prosrc)");

            executeQueryWithWarnings(conn, sb.toString(), params, catalog, list, warnings, "Functions/Procedures", null);
        }

        // 2. Views
        if (requestedTypes.contains("VIEW")) {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT v.schemaname AS schema, v.viewname AS name, 'VIEW' AS type, ")
              .append("'CREATE OR REPLACE VIEW \"' || v.schemaname || '\".\"' || v.viewname || '\" AS\\n' || v.definition AS definition ")
              .append("FROM pg_catalog.pg_views v ")
              .append("WHERE LEFT(v.schemaname, 3) != 'pg_' AND v.schemaname != 'information_schema' ");

            List<Object> params = new ArrayList<>();
            if (cleanSchema != null) {
                sb.append("AND (v.schemaname = ? OR lower(v.schemaname) = lower(?)) ");
                params.add(cleanSchema);
                params.add(cleanSchema);
            }
            appendPostgresKeywordFilter(sb, params, query, "v.definition");

            executeQueryWithWarnings(conn, sb.toString(), params, catalog, list, warnings, "Views", null);

            // Materialized views
            StringBuilder msb = new StringBuilder();
            msb.append("SELECT m.schemaname AS schema, m.matviewname AS name, 'VIEW' AS type, ")
               .append("'CREATE MATERIALIZED VIEW \"' || m.schemaname || '\".\"' || m.matviewname || '\" AS\\n' || m.definition AS definition ")
               .append("FROM pg_catalog.pg_matviews m ")
               .append("WHERE LEFT(m.schemaname, 3) != 'pg_' AND m.schemaname != 'information_schema' ");

            List<Object> mparams = new ArrayList<>();
            if (cleanSchema != null) {
                msb.append("AND (m.schemaname = ? OR lower(m.schemaname) = lower(?)) ");
                mparams.add(cleanSchema);
                mparams.add(cleanSchema);
            }
            appendPostgresKeywordFilter(msb, mparams, query, "m.definition");

            executeQueryWithWarnings(conn, msb.toString(), mparams, catalog, list, warnings, "Materialized Views", null);
        }

        // 3. Triggers
        if (requestedTypes.contains("TRIGGER")) {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT n.nspname AS schema, t.tgname AS name, 'TRIGGER' AS type, ")
              .append("pg_catalog.pg_get_triggerdef(t.oid, true) AS definition ")
              .append("FROM pg_catalog.pg_trigger t ")
              .append("JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid ")
              .append("JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace ")
              .append("WHERE NOT t.tgisinternal AND LEFT(n.nspname, 3) != 'pg_' AND n.nspname != 'information_schema' ");

            List<Object> params = new ArrayList<>();
            if (cleanSchema != null) {
                sb.append("AND (n.nspname = ? OR lower(n.nspname) = lower(?)) ");
                params.add(cleanSchema);
                params.add(cleanSchema);
            }
            appendPostgresKeywordFilter(sb, params, query, "pg_catalog.pg_get_triggerdef(t.oid, true)");

            executeQueryWithWarnings(conn, sb.toString(), params, catalog, list, warnings, "Triggers", null);
        }

        return list;
    }

    private List<RawObjectDefinition> fetchPostgresDefinitions(Connection conn, ObjectSearchQuery query) {
        return fetchPostgresDefinitions(conn, query, new ArrayList<>());
    }

    private void executeQueryWithWarnings(Connection conn, String sql, List<Object> params, String catalog,
                                          List<RawObjectDefinition> out, List<String> warnings,
                                          String objectCategory, int[] encryptedCounter) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sch = rs.getString(1);
                    String name = rs.getString(2);
                    String type = rs.getString(3);
                    String def = rs.getString(4);
                    if (def != null && !def.isBlank()) {
                        out.add(new RawObjectDefinition(catalog, sch, name, type, def));
                    } else if (encryptedCounter != null) {
                        encryptedCounter[0]++;
                    }
                }
            }
        } catch (Exception ex) {
            String msg = "Không thể nạp đối tượng " + objectCategory + ": " + ex.getMessage();
            if (warnings != null) {
                warnings.add(msg);
            }
        }
    }

    private void executeQuery(Connection conn, String sql, List<Object> params, String catalog, List<RawObjectDefinition> out) {
        executeQueryWithWarnings(conn, sql, params, catalog, out, null, "objects", null);
    }

    // === MOCK DEFINITIONS FOR OFFLINE / TEST MODE ===

    public List<RawObjectDefinition> getMockDefinitions(ObjectSearchQuery query) {
        List<RawObjectDefinition> list = new ArrayList<>();
        String cat = (query.getCatalog() != null && !query.getCatalog().isBlank()) ? query.getCatalog() : "master";
        String sch = (query.getSchema() != null && !query.getSchema().isBlank() && !query.getSchema().equals("*")) ? query.getSchema() : "dbo";

        // View 1
        list.add(new RawObjectDefinition(cat, sch, "v_customer_orders", "VIEW",
                """
                CREATE VIEW dbo.v_customer_orders AS
                SELECT c.id AS customer_id, c.name AS customer_name,
                       o.id AS order_id, o.amount, o.status, o.created_at
                FROM dbo.customers c
                JOIN dbo.orders o ON o.customer_id = c.id
                WHERE o.status = 'active';
                """));

        // View 2
        list.add(new RawObjectDefinition(cat, sch, "v_monthly_revenue", "VIEW",
                """
                CREATE VIEW dbo.v_monthly_revenue AS
                SELECT DATEPART(year, created_at) AS order_year,
                       DATEPART(month, created_at) AS order_month,
                       SUM(amount) AS total_amount,
                       COUNT(id) AS total_orders
                FROM dbo.orders
                GROUP BY DATEPART(year, created_at), DATEPART(month, created_at);
                """));

        // Stored Procedure 1
        list.add(new RawObjectDefinition(cat, sch, "usp_close_period", "PROCEDURE",
                """
                CREATE PROCEDURE dbo.usp_close_period
                    @PeriodYear INT,
                    @PeriodMonth INT
                AS
                BEGIN
                    SET NOCOUNT ON;
                    -- Calculate period totals from orders
                    UPDATE dbo.orders
                    SET status = 'closed'
                    WHERE DATEPART(year, created_at) = @PeriodYear
                      AND DATEPART(month, created_at) = @PeriodMonth;
                      
                    SELECT @@ROWCOUNT AS closed_count;
                END;
                """));

        // Stored Procedure 2
        list.add(new RawObjectDefinition(cat, sch, "usp_recalc_stock", "PROCEDURE",
                """
                CREATE PROCEDURE dbo.usp_recalc_stock
                    @ProductId INT
                AS
                BEGIN
                    SET NOCOUNT ON;
                    DECLARE @CurrentStock INT;
                    SELECT @CurrentStock = SUM(amount) FROM dbo.order_items WHERE product_id = @ProductId;
                    UPDATE dbo.products SET stock = @CurrentStock WHERE id = @ProductId;
                END;
                """));

        // Function 1
        list.add(new RawObjectDefinition(cat, sch, "fn_format_code", "FUNCTION",
                """
                CREATE FUNCTION dbo.fn_format_code(@RawCode VARCHAR(32))
                RETURNS VARCHAR(64)
                AS
                BEGIN
                    RETURN UPPER(LTRIM(RTRIM(@RawCode)));
                END;
                """));

        // Function 2
        list.add(new RawObjectDefinition(cat, sch, "fn_get_balance", "FUNCTION",
                """
                CREATE FUNCTION dbo.fn_get_balance(@CustomerId INT)
                RETURNS NUMERIC(18, 2)
                AS
                BEGIN
                    DECLARE @Balance NUMERIC(18, 2);
                    SELECT @Balance = COALESCE(SUM(amount), 0)
                    FROM dbo.orders
                    WHERE customer_id = @CustomerId AND status = 'active';
                    RETURN @Balance;
                END;
                """));

        // Trigger 1
        list.add(new RawObjectDefinition(cat, sch, "trg_orders_audit", "TRIGGER",
                """
                CREATE TRIGGER dbo.trg_orders_audit
                ON dbo.orders
                AFTER INSERT, UPDATE
                AS
                BEGIN
                    SET NOCOUNT ON;
                    INSERT INTO dbo.audit_events (table_name, operation, created_at)
                    VALUES ('orders', 'AUDIT_ORDER_CHANGE', GETDATE());
                END;
                """));

        // Filter by requested types
        Set<String> types = query.getObjectTypes();
        if (types != null && !types.isEmpty()) {
            list.removeIf(def -> !types.contains(def.type()));
        }

        return list;
    }
}

