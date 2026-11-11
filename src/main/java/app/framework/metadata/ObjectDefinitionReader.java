package app.framework.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common database object definition reader for Views, Procedures, Functions, and Triggers (BE-0.2).
 * Supports SQL Server and PostgreSQL, returns definitions along with warnings for partial retrieval failures.
 */
public class ObjectDefinitionReader {

    private static final Logger log = LoggerFactory.getLogger(ObjectDefinitionReader.class);

    public record ObjectDefinition(
            String catalog,
            String schema,
            String name,
            String type,           // "VIEW", "PROCEDURE", "FUNCTION", "TRIGGER"
            String definition,
            String parentTable,    // Table name for Triggers, or null
            String identityArgs    // Identity arguments for overloaded PostgreSQL functions, or null
    ) {}

    public record ObjectDefinitionResult(
            List<ObjectDefinition> definitions,
            List<String> warnings
    ) {}

    public static class FilterOptions {
        private String catalog;
        private String schema;
        private Set<String> objectTypes = new HashSet<>(Arrays.asList("VIEW", "PROCEDURE", "FUNCTION", "TRIGGER"));
        private String keyword;
        private boolean caseSensitive = false;
        private boolean isRegex = false;

        public FilterOptions() {}

        public FilterOptions(String catalog, String schema, Set<String> objectTypes) {
            this.catalog = catalog;
            this.schema = schema;
            if (objectTypes != null && !objectTypes.isEmpty()) {
                this.objectTypes = new HashSet<>(objectTypes);
            }
        }

        public String getCatalog() { return catalog; }
        public FilterOptions setCatalog(String catalog) { this.catalog = catalog; return this; }
        public String getSchema() { return schema; }
        public FilterOptions setSchema(String schema) { this.schema = schema; return this; }
        public Set<String> getObjectTypes() { return objectTypes; }
        public FilterOptions setObjectTypes(Set<String> objectTypes) {
            if (objectTypes != null) this.objectTypes = new HashSet<>(objectTypes);
            return this;
        }
        public String getKeyword() { return keyword; }
        public FilterOptions setKeyword(String keyword) { this.keyword = keyword; return this; }
        public boolean isCaseSensitive() { return caseSensitive; }
        public FilterOptions setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; return this; }
        public boolean isRegex() { return isRegex; }
        public FilterOptions setRegex(boolean regex) { isRegex = regex; return this; }
    }

    public ObjectDefinitionResult readDefinitions(Connection conn, String engine, FilterOptions options) {
        if (options == null) {
            options = new FilterOptions();
        }

        List<ObjectDefinition> definitions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (conn == null) {
            definitions.addAll(getMockDefinitions(options));
            return new ObjectDefinitionResult(definitions, warnings);
        }

        boolean isPg = engine != null && (engine.equalsIgnoreCase("postgresql") || engine.equalsIgnoreCase("postgres"));
        if (isPg) {
            readPostgresDefinitions(conn, options, definitions, warnings);
        } else {
            readSqlServerDefinitions(conn, options, definitions, warnings);
        }

        return new ObjectDefinitionResult(definitions, warnings);
    }

    // === SQL SERVER ===

    private void readSqlServerDefinitions(Connection conn, FilterOptions options,
                                          List<ObjectDefinition> out, List<String> warnings) {
        Set<String> requestedTypes = options.getObjectTypes();
        String catalog = options.getCatalog();
        String schema = options.getSchema();
        String dbPrefix = (catalog != null && !catalog.isBlank()) ? "[" + catalog.replace("]", "]]") + "]." : "";
        String cleanSchema = (schema != null && !schema.isBlank() && !schema.equals("*") && !schema.equalsIgnoreCase("all")) ? schema : null;

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
              .append("sm.definition, NULL AS [parent_table], NULL AS [identity_args] ")
              .append("FROM ").append(dbPrefix).append("sys.sql_modules sm ")
              .append("JOIN ").append(dbPrefix).append("sys.objects o ON o.object_id = sm.object_id ")
              .append("JOIN ").append(dbPrefix).append("sys.schemas s ON s.schema_id = o.schema_id ")
              .append("WHERE sm.definition IS NOT NULL AND o.is_ms_shipped = 0 ")
              .append("AND o.type IN (").append(String.join(", ", typeCodes)).append(") ");

            List<Object> params = new ArrayList<>();
            if (cleanSchema != null) {
                sb.append("AND (s.name = ? OR LOWER(s.name) = LOWER(?)) ");
                params.add(cleanSchema);
                params.add(cleanSchema);
            }

            appendSqlServerKeywordFilter(sb, params, options);

            try {
                executeDefinitionQuery(conn, sb.toString(), params, catalog, out);
            } catch (SQLException e) {
                String msg = "Không thể nạp Views/Routines (SQL Server): " + e.getMessage();
                log.warn(msg, e);
                warnings.add(msg);
            }
        }

        // 2. Triggers
        if (requestedTypes.contains("TRIGGER")) {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT s.name AS [schema], tr.name AS [name], 'TRIGGER' AS [type], sm.definition, ")
              .append("parent.name AS [parent_table], NULL AS [identity_args] ")
              .append("FROM ").append(dbPrefix).append("sys.sql_modules sm ")
              .append("JOIN ").append(dbPrefix).append("sys.triggers tr ON tr.object_id = sm.object_id ")
              .append("JOIN ").append(dbPrefix).append("sys.objects parent ON parent.object_id = tr.parent_id ")
              .append("JOIN ").append(dbPrefix).append("sys.schemas s ON s.schema_id = parent.schema_id ")
              .append("WHERE sm.definition IS NOT NULL AND tr.is_ms_shipped = 0 ");

            List<Object> params = new ArrayList<>();
            if (cleanSchema != null) {
                sb.append("AND (s.name = ? OR LOWER(s.name) = LOWER(?)) ");
                params.add(cleanSchema);
                params.add(cleanSchema);
            }

            appendSqlServerKeywordFilter(sb, params, options);

            try {
                executeDefinitionQuery(conn, sb.toString(), params, catalog, out);
            } catch (SQLException e) {
                String msg = "Không thể nạp Triggers (SQL Server): " + e.getMessage();
                log.warn(msg, e);
                warnings.add(msg);
            }
        }
    }

    private void appendSqlServerKeywordFilter(StringBuilder sb, List<Object> params, FilterOptions options) {
        String kw = extractSearchableLiteral(options);
        if (kw != null && !kw.isBlank()) {
            String escaped = kw.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
            String collation = options.isCaseSensitive() ? "Latin1_General_CS_AS" : "Latin1_General_CI_AI";
            sb.append("AND sm.definition COLLATE ").append(collation).append(" LIKE ? ");
            params.add("%" + escaped + "%");
        }
    }

    // === POSTGRESQL ===

    private void readPostgresDefinitions(Connection conn, FilterOptions options,
                                         List<ObjectDefinition> out, List<String> warnings) {
        Set<String> requestedTypes = options.getObjectTypes();
        String catalog = options.getCatalog();
        String schema = options.getSchema();
        String cleanSchema = (schema != null && !schema.isBlank() && !schema.equals("*") && !schema.equalsIgnoreCase("all")) ? schema : null;

        // 1. Routines
        if (requestedTypes.contains("FUNCTION") || requestedTypes.contains("PROCEDURE")) {
            List<String> typeClauses = new ArrayList<>();
            if (requestedTypes.contains("FUNCTION")) typeClauses.add("p.prokind IN ('f', 'w')");
            if (requestedTypes.contains("PROCEDURE")) typeClauses.add("p.prokind = 'p'");

            StringBuilder sb = new StringBuilder();
            sb.append("SELECT n.nspname AS schema, p.proname AS name, ")
              .append("CASE WHEN p.prokind = 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END AS type, ")
              .append("CASE WHEN l.lanname IN ('c', 'internal') THEN p.prosrc ")
              .append("ELSE COALESCE(pg_catalog.pg_get_functiondef(p.oid), p.prosrc) END AS definition, ")
              .append("NULL AS parent_table, ")
              .append("pg_catalog.pg_get_function_identity_arguments(p.oid) AS identity_args ")
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

            appendPostgresKeywordFilter(sb, params, options, "COALESCE(pg_catalog.pg_get_functiondef(p.oid), p.prosrc)");

            try {
                executeDefinitionQuery(conn, sb.toString(), params, catalog, out);
            } catch (SQLException e) {
                String msg = "Không thể nạp Functions/Procedures (PostgreSQL): " + e.getMessage();
                log.warn(msg, e);
                warnings.add(msg);
            }
        }

        // 2. Views
        if (requestedTypes.contains("VIEW")) {
            // Standard views
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT v.schemaname AS schema, v.viewname AS name, 'VIEW' AS type, ")
              .append("'CREATE OR REPLACE VIEW \"' || v.schemaname || '\".\"' || v.viewname || '\" AS\n' || v.definition AS definition, ")
              .append("NULL AS parent_table, NULL AS identity_args ")
              .append("FROM pg_catalog.pg_views v ")
              .append("WHERE LEFT(v.schemaname, 3) != 'pg_' AND v.schemaname != 'information_schema' ");

            List<Object> params = new ArrayList<>();
            if (cleanSchema != null) {
                sb.append("AND (v.schemaname = ? OR lower(v.schemaname) = lower(?)) ");
                params.add(cleanSchema);
                params.add(cleanSchema);
            }

            appendPostgresKeywordFilter(sb, params, options, "v.definition");

            try {
                executeDefinitionQuery(conn, sb.toString(), params, catalog, out);
            } catch (SQLException e) {
                String msg = "Không thể nạp Views (PostgreSQL): " + e.getMessage();
                log.warn(msg, e);
                warnings.add(msg);
            }

            // Materialized views
            StringBuilder msb = new StringBuilder();
            msb.append("SELECT m.schemaname AS schema, m.matviewname AS name, 'VIEW' AS type, ")
               .append("'CREATE MATERIALIZED VIEW \"' || m.schemaname || '\".\"' || m.matviewname || '\" AS\n' || m.definition AS definition, ")
               .append("NULL AS parent_table, NULL AS identity_args ")
               .append("FROM pg_catalog.pg_matviews m ")
               .append("WHERE LEFT(m.schemaname, 3) != 'pg_' AND m.schemaname != 'information_schema' ");

            List<Object> mparams = new ArrayList<>();
            if (cleanSchema != null) {
                msb.append("AND (m.schemaname = ? OR lower(m.schemaname) = lower(?)) ");
                mparams.add(cleanSchema);
                mparams.add(cleanSchema);
            }

            appendPostgresKeywordFilter(msb, mparams, options, "m.definition");

            try {
                executeDefinitionQuery(conn, msb.toString(), mparams, catalog, out);
            } catch (SQLException e) {
                String msg = "Không thể nạp Materialized Views (PostgreSQL): " + e.getMessage();
                log.warn(msg, e);
                warnings.add(msg);
            }
        }

        // 3. Triggers
        if (requestedTypes.contains("TRIGGER")) {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT n.nspname AS schema, t.tgname AS name, 'TRIGGER' AS type, ")
              .append("pg_catalog.pg_get_triggerdef(t.oid, true) AS definition, ")
              .append("c.relname AS parent_table, NULL AS identity_args ")
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

            appendPostgresKeywordFilter(sb, params, options, "pg_catalog.pg_get_triggerdef(t.oid, true)");

            try {
                executeDefinitionQuery(conn, sb.toString(), params, catalog, out);
            } catch (SQLException e) {
                String msg = "Không thể nạp Triggers (PostgreSQL): " + e.getMessage();
                log.warn(msg, e);
                warnings.add(msg);
            }
        }
    }

    private void appendPostgresKeywordFilter(StringBuilder sb, List<Object> params, FilterOptions options, String expr) {
        String kw = extractSearchableLiteral(options);
        if (kw != null && !kw.isBlank()) {
            String escaped = kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String op = options.isCaseSensitive() ? "LIKE" : "ILIKE";
            sb.append("AND (").append(expr).append(" ").append(op).append(" ? ESCAPE '\\') ");
            params.add("%" + escaped + "%");
        }
    }

    private String extractSearchableLiteral(FilterOptions options) {
        if (options == null || options.getKeyword() == null) return null;
        String kw = options.getKeyword().trim();
        if (kw.isEmpty()) return null;
        if (!options.isRegex()) {
            return kw;
        }
        // If regex, extract any literal substring of length >= 3
        Pattern litPattern = Pattern.compile("[a-zA-Z0-9_]{3,}");
        Matcher m = litPattern.matcher(kw);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    private void executeDefinitionQuery(Connection conn, String sql, List<Object> params,
                                        String catalog, List<ObjectDefinition> out) throws SQLException {
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
                    String parentTable = rs.getString(5);
                    String identityArgs = rs.getString(6);
                    if (def != null && !def.isBlank()) {
                        out.add(new ObjectDefinition(catalog, sch, name, type, def, parentTable, identityArgs));
                    }
                }
            }
        }
    }

    // === MOCK DEFINITIONS ===

    public List<ObjectDefinition> getMockDefinitions(FilterOptions options) {
        List<ObjectDefinition> list = new ArrayList<>();
        String cat = (options.getCatalog() != null && !options.getCatalog().isBlank()) ? options.getCatalog() : "master";
        String sch = (options.getSchema() != null && !options.getSchema().isBlank() && !options.getSchema().equals("*")) ? options.getSchema() : "dbo";
        Set<String> types = options.getObjectTypes();

        if (types.contains("VIEW")) {
            list.add(new ObjectDefinition(cat, sch, "v_customer_summary", "VIEW",
                    "CREATE VIEW " + sch + ".v_customer_summary AS\nSELECT c.id, c.name, COUNT(o.id) as total_orders\nFROM customers c\nLEFT JOIN orders o ON o.customer_id = c.id\nGROUP BY c.id, c.name;",
                    null, null));
            list.add(new ObjectDefinition(cat, sch, "v_active_orders", "VIEW",
                    "CREATE VIEW " + sch + ".v_active_orders AS\nSELECT id, customer_id, order_date, status\nFROM orders\nWHERE status = 'ACTIVE';",
                    null, null));
        }
        if (types.contains("PROCEDURE")) {
            list.add(new ObjectDefinition(cat, sch, "sp_process_order", "PROCEDURE",
                    "CREATE PROCEDURE " + sch + ".sp_process_order @order_id INT AS\nBEGIN\n    SET NOCOUNT ON;\n    UPDATE orders SET status = 'PROCESSING' WHERE id = @order_id;\nEND;",
                    null, null));
        }
        if (types.contains("FUNCTION")) {
            list.add(new ObjectDefinition(cat, sch, "fn_calculate_tax", "FUNCTION",
                    "CREATE FUNCTION " + sch + ".fn_calculate_tax(@amount DECIMAL(18,2)) RETURNS DECIMAL(18,2) AS\nBEGIN\n    RETURN @amount * 0.1;\nEND;",
                    null, "amount numeric"));
        }
        if (types.contains("TRIGGER")) {
            list.add(new ObjectDefinition(cat, sch, "tr_orders_audit", "TRIGGER",
                    "CREATE TRIGGER " + sch + ".tr_orders_audit ON " + sch + ".orders AFTER UPDATE AS\nBEGIN\n    INSERT INTO audit_log (table_name, action) VALUES ('orders', 'UPDATE');\nEND;",
                    "orders", null));
        }

        return list;
    }
}
