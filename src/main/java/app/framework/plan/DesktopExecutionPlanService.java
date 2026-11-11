package app.framework.plan;

import app.models.PhysicalExecutionPlan;
import app.models.PlanOperatorNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for fetching, parsing, and normalizing database execution plans (Estimated & Actual).
 * Supports PostgreSQL (EXPLAIN JSON), SQL Server (ShowPlan XML), and SQLite (EXPLAIN QUERY PLAN).
 * Compliant with Phase 6 & Section 11 of query_plan_visualization.md.
 */
public class DesktopExecutionPlanService {

    private static final Logger logger = LoggerFactory.getLogger(DesktopExecutionPlanService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DesktopExecutionPlanService INSTANCE = new DesktopExecutionPlanService();

    public static DesktopExecutionPlanService getInstance() {
        return INSTANCE;
    }

    public interface PlanCallback {
        void onStart(String planType, String dialect);
        void onSuccess(PhysicalExecutionPlan plan);
        void onError(Throwable error);
    }

    /**
     * Executes plan retrieval asynchronously on Java 21 Virtual Threads.
     */
    public void getExecutionPlanAsync(
            String connectionName,
            String database,
            String schema,
            String sql,
            String planType, // "estimated" or "actual"
            PlanCallback callback
    ) {
        getExecutionPlanAsync(null, connectionName, database, schema, sql, planType, callback);
    }

    /** Phase 8: plan on the tab's own session; an actual plan runs the statement, so read-only rules apply. */
    public void getExecutionPlanAsync(
            String tabId,
            String connectionName,
            String database,
            String schema,
            String sql,
            String planType,
            PlanCallback callback
    ) {
        String cleanSql = (sql != null ? sql.trim() : "");
        if (cleanSql.isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Câu lệnh SQL không được để trống khi lấy Execution Plan."));
            }
            return;
        }

        String type = (planType != null && planType.equalsIgnoreCase("actual")) ? "actual" : "estimated";

        Thread.ofVirtual().name("exec-plan-worker").start(() -> {
            try {
                // Determine connection & dialect
                app.framework.connection.ConnectionSessionManager sessionMgr = app.framework.connection.ConnectionSessionManager.getInstance();
                com.lltool.infrastructure.database.api.DatabaseConnection activeConn = null;
                com.lltool.core.application.connection.dto.ConnectionProfileDto profile = null;
                if (app.framework.backend.BackendFlags.real(app.framework.backend.BackendFlags.Area.PLAN)) {
                    profile = app.framework.session.TabSessionManager.profileByName(connectionName);
                    String key = tabId != null ? tabId : "default";
                    var tabs = app.framework.session.TabSessionManager.getInstance();
                    var opened = tabs.ensureConnected(key, profile, database);
                    if (opened.isFailure()) {
                        throw new IllegalStateException(opened.getError().map(er -> er.message()).orElse("Không kết nối được."));
                    }
                    var session = tabs.get(key);
                    if ("actual".equals(type)) {
                        var decision = app.framework.query.SqlStatementClassifier.check(sql, session.readOnly(), session.environment(), false);
                        if (!decision.allowed()) {
                            throw new IllegalStateException("Actual plan chạy câu lệnh thật. " + decision.message());
                        }
                    }
                    activeConn = opened.getDataOrThrow();
                }
                String dialect = "sqlserver";

                if (profile != null && profile.databaseType() != null) {
                    dialect = profile.databaseType().toLowerCase();
                } else if (app.framework.state.GlobalDesktopState.getInstance().getActiveDialect() != null) {
                    dialect = app.framework.state.GlobalDesktopState.getInstance().getActiveDialect().toLowerCase();
                }

                final String detectedDialect = dialect;
                if (callback != null) {
                    Platform.runLater(() -> callback.onStart(type, detectedDialect));
                }

                PhysicalExecutionPlan plan = null;
                long startMs = System.currentTimeMillis();

                if (activeConn != null && activeConn.isValid(2)) {
                    Connection conn = activeConn.getRawJdbcConnection();
                    if (conn != null && !conn.isClosed()) {
                        if (detectedDialect.contains("postgres")) {
                            plan = fetchPostgresPlan(conn, cleanSql, type, database, schema);
                        } else if (detectedDialect.contains("sqlite")) {
                            plan = fetchSqlitePlan(conn, cleanSql, type, database, schema);
                        } else {
                            plan = fetchSqlServerPlan(conn, cleanSql, type, database, schema);
                        }
                    }
                }

                if (plan == null) {
                    // Fallback to simulated plan if connection is absent or offline
                    plan = generateSimulatedPlan(cleanSql, detectedDialect, type);
                }

                long elapsedMs = System.currentTimeMillis() - startMs;
                plan.setExecutionDurationMs(elapsedMs);

                final PhysicalExecutionPlan finalPlan = plan;
                if (callback != null) {
                    Platform.runLater(() -> callback.onSuccess(finalPlan));
                }
            } catch (Exception e) {
                logger.error("Error generating execution plan: {}", e.getMessage(), e);
                if (callback != null) {
                    Platform.runLater(() -> callback.onError(e));
                }
            }
        });
    }

    // =========================================================================
    // 1. PostgreSQL Parser & Fetcher
    // =========================================================================

    public PhysicalExecutionPlan fetchPostgresPlan(
            Connection conn, String sql, String planType, String database, String schema
    ) throws Exception {
        String explainCmd = (planType.equalsIgnoreCase("actual"))
                ? "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql
                : "EXPLAIN (FORMAT JSON, COSTS, VERBOSE) " + sql;

        StringBuilder jsonSb = new StringBuilder();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(explainCmd)) {
            while (rs.next()) {
                jsonSb.append(rs.getString(1));
            }
        }

        String rawJson = jsonSb.toString().trim();
        PhysicalExecutionPlan plan = parsePostgresJsonPlan(rawJson, planType);
        plan.setRawPlanText(rawJson);
        return plan;
    }

    public PhysicalExecutionPlan parsePostgresJsonPlan(String jsonText, String planType) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonText);
            JsonNode planWrapper = rootNode;
            if (rootNode.isArray() && rootNode.size() > 0) {
                planWrapper = rootNode.get(0);
            }
            JsonNode planObj = planWrapper.has("Plan") ? planWrapper.get("Plan") : planWrapper;

            double totalCost = planObj.path("Total Cost").asDouble(1.0);
            int[] idCounter = new int[]{0};

            PlanOperatorNode root = parsePostgresNode(planObj, totalCost, idCounter, planType);
            PhysicalExecutionPlan plan = new PhysicalExecutionPlan("postgresql", planType, totalCost, root);
            plan.setRawPlanText(jsonText);
            return plan;
        } catch (Exception e) {
            logger.warn("Failed to parse PostgreSQL JSON plan: {}. Falling back to simulated plan.", e.getMessage());
            return generateSimulatedPlan("", "postgresql", planType);
        }
    }

    private PlanOperatorNode parsePostgresNode(JsonNode node, double totalCost, int[] idCounter, String planType) {
        idCounter[0]++;
        String nodeType = node.path("Node Type").asText("Result");
        String relName = node.path("Relation Name").asText("");
        String schemaName = node.path("Schema").asText("public");
        double subCost = node.path("Total Cost").asDouble(0.0);
        double estRows = node.path("Plan Rows").asDouble(0.0);

        Double actRows = node.has("Actual Rows") ? node.path("Actual Rows").asDouble() : null;
        Double actTime = node.has("Actual Total Time") ? node.path("Actual Total Time").asDouble() : null;
        Integer actLoops = node.has("Actual Loops") ? node.path("Actual Loops").asInt() : null;

        List<PlanOperatorNode> children = new ArrayList<>();
        JsonNode plansArray = node.path("Plans");
        if (plansArray.isArray()) {
            for (JsonNode childJson : plansArray) {
                children.add(parsePostgresNode(childJson, totalCost, idCounter, planType));
            }
        }

        double childSubCosts = 0.0;
        for (PlanOperatorNode c : children) {
            childSubCosts += c.getSubtreeCost();
        }
        double opCost = Math.max(0.0001, subCost - childSubCosts);
        double costPct = (opCost / Math.max(totalCost, 0.0001)) * 100.0;

        PlanOperatorNode opNode = new PlanOperatorNode(String.valueOf(idCounter[0]), nodeType);
        opNode.setRelationName(relName);
        opNode.setSchema(schemaName);
        opNode.setEstimatedRows(estRows);
        opNode.setActualRows(actRows);
        opNode.setSubtreeCost(subCost);
        opNode.setOperatorCost(opCost);
        opNode.setCostPercent(costPct);
        opNode.setActualTimeMs(actTime);
        opNode.setActualLoops(actLoops);
        opNode.setChildren(children);

        if (node.has("Output") && node.path("Output").isArray()) {
            List<String> outputCols = new ArrayList<>();
            for (JsonNode out : node.path("Output")) {
                outputCols.add(out.asText());
            }
            opNode.setOutputColumns(outputCols);
        }

        return opNode;
    }

    // =========================================================================
    // 2. SQL Server Parser & Fetcher
    // =========================================================================

    public PhysicalExecutionPlan fetchSqlServerPlan(
            Connection conn, String sql, String planType, String database, String schema
    ) throws Exception {
        String xmlPlan = "";
        boolean isActual = planType.equalsIgnoreCase("actual");

        try (Statement stmt = conn.createStatement()) {
            if (isActual) {
                stmt.execute("SET STATISTICS XML ON");
                try {
                    boolean hasResults = stmt.execute(sql);
                    while (true) {
                        if (hasResults) {
                            try (ResultSet rs = stmt.getResultSet()) {
                                while (rs.next()) {
                                    String val = rs.getString(1);
                                    if (val != null && val.contains("<ShowPlanXML")) {
                                        xmlPlan = val;
                                        break;
                                    }
                                }
                            }
                        }
                        if (xmlPlan.contains("<ShowPlanXML")) break;
                        if (!stmt.getMoreResults() && stmt.getUpdateCount() == -1) break;
                    }
                } finally {
                    try (Statement cleanStmt = conn.createStatement()) {
                        cleanStmt.execute("SET STATISTICS XML OFF");
                    } catch (Exception ignored) {}
                }
            } else {
                stmt.execute("SET SHOWPLAN_XML ON");
                try {
                    try (ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) {
                            String val = rs.getString(1);
                            if (val != null && val.contains("<ShowPlanXML")) {
                                xmlPlan = val;
                                break;
                            }
                        }
                    }
                } finally {
                    try (Statement cleanStmt = conn.createStatement()) {
                        cleanStmt.execute("SET SHOWPLAN_XML OFF");
                    } catch (Exception ignored) {}
                }
            }
        }

        if (xmlPlan.isEmpty()) {
            return generateSimulatedPlan(sql, "sqlserver", planType);
        }

        PhysicalExecutionPlan plan = parseSqlServerXmlPlan(xmlPlan, planType);
        plan.setRawPlanText(xmlPlan);
        return plan;
    }

    public PhysicalExecutionPlan parseSqlServerXmlPlan(String xmlText, String planType) {
        try {
            // Strip XML namespaces to prevent DOM prefix issues
            String cleanXml = xmlText.replaceAll("\\sxmlns=\"[^\"]+\"", "");
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(cleanXml)));

            NodeList relOpList = doc.getElementsByTagName("RelOp");
            if (relOpList.getLength() == 0) {
                return generateSimulatedPlan("", "sqlserver", planType);
            }

            Element topRelOp = (Element) relOpList.item(0);
            double totalCost = parseDouble(topRelOp.getAttribute("EstimatedTotalSubtreeCost"), 1.0);

            int[] idCounter = new int[]{0};
            PlanOperatorNode root = parseSqlServerRelOp(topRelOp, totalCost, idCounter);
            PhysicalExecutionPlan plan = new PhysicalExecutionPlan("sqlserver", planType, totalCost, root);
            plan.setRawPlanText(xmlText);
            return plan;
        } catch (Exception e) {
            logger.warn("Failed to parse SQL Server XML plan: {}. Falling back to simulated plan.", e.getMessage());
            return generateSimulatedPlan("", "sqlserver", planType);
        }
    }

    private PlanOperatorNode parseSqlServerRelOp(Element el, double totalCost, int[] idCounter) {
        idCounter[0]++;
        String nodeId = el.getAttribute("NodeId");
        if (nodeId == null || nodeId.isBlank()) nodeId = String.valueOf(idCounter[0]);

        String physOp = el.getAttribute("PhysicalOp");
        if (physOp == null || physOp.isBlank()) physOp = "Unknown";
        String logOp = el.getAttribute("LogicalOp");
        if (logOp == null || logOp.isBlank()) logOp = physOp;

        double estRows = parseDouble(el.getAttribute("EstimateRows"), 0.0);
        double subCost = parseDouble(el.getAttribute("EstimatedTotalSubtreeCost"), 0.0);

        // Find child RelOps directly under this RelOp's child elements
        List<PlanOperatorNode> children = new ArrayList<>();
        NodeList childNodes = el.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element) {
                findChildRelOps((Element) child, children, totalCost, idCounter);
            }
        }

        double childSubCosts = 0.0;
        for (PlanOperatorNode c : children) {
            childSubCosts += c.getSubtreeCost();
        }
        double opCost = Math.max(0.0001, subCost - childSubCosts);
        double costPct = (opCost / Math.max(totalCost, 0.0001)) * 100.0;

        PlanOperatorNode opNode = new PlanOperatorNode(nodeId, physOp);
        opNode.setLogicalOp(logOp);
        opNode.setEstimatedRows(estRows);
        opNode.setSubtreeCost(subCost);
        opNode.setOperatorCost(opCost);
        opNode.setCostPercent(costPct);
        opNode.setChildren(children);

        // Extract Relation / Table name if available
        NodeList objList = el.getElementsByTagName("Object");
        if (objList.getLength() > 0) {
            Element objEl = (Element) objList.item(0);
            String tableVal = objEl.getAttribute("Table");
            if (tableVal != null) {
                opNode.setRelationName(tableVal.replace("[", "").replace("]", ""));
            }
            String schemaVal = objEl.getAttribute("Schema");
            if (schemaVal != null) {
                opNode.setSchema(schemaVal.replace("[", "").replace("]", ""));
            }
            String dbVal = objEl.getAttribute("Database");
            if (dbVal != null) {
                opNode.setDatabase(dbVal.replace("[", "").replace("]", ""));
            }
        }

        // Actual runtime rows if available
        NodeList runTimeList = el.getElementsByTagName("RunTimeCountersPerThread");
        if (runTimeList.getLength() > 0) {
            Element rtEl = (Element) runTimeList.item(0);
            String actRowsStr = rtEl.getAttribute("ActualRows");
            if (actRowsStr != null && !actRowsStr.isBlank()) {
                opNode.setActualRows(parseDouble(actRowsStr, null));
            }
        }

        return opNode;
    }

    private void findChildRelOps(Element el, List<PlanOperatorNode> result, double totalCost, int[] idCounter) {
        if ("RelOp".equals(el.getTagName())) {
            result.add(parseSqlServerRelOp(el, totalCost, idCounter));
            return;
        }
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c instanceof Element) {
                findChildRelOps((Element) c, result, totalCost, idCounter);
            }
        }
    }

    // =========================================================================
    // 3. SQLite Parser & Fetcher
    // =========================================================================

    public PhysicalExecutionPlan fetchSqlitePlan(
            Connection conn, String sql, String planType, String database, String schema
    ) throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN QUERY PLAN " + sql)) {
            while (rs.next()) {
                rows.add(Arrays.asList(
                        rs.getInt(1), // id
                        rs.getInt(2), // parent
                        rs.getInt(3), // notused
                        rs.getString(4) // detail
                ));
            }
        }
        return parseSqlitePlan(rows, planType);
    }

    public PhysicalExecutionPlan parseSqlitePlan(List<List<Object>> rows, String planType) {
        if (rows == null || rows.isEmpty()) {
            return generateSimulatedPlan("", "sqlite", planType);
        }

        Map<Integer, PlanOperatorNode> nodesMap = new LinkedHashMap<>();
        Map<Integer, Integer> parentMap = new HashMap<>();

        for (List<Object> r : rows) {
            int nid = ((Number) r.get(0)).intValue();
            int pid = ((Number) r.get(1)).intValue();
            String detail = (r.size() > 3 && r.get(3) != null) ? r.get(3).toString() : "SCAN";

            String opType = detail.split("\\s+")[0];
            PlanOperatorNode node = new PlanOperatorNode(String.valueOf(nid), opType);
            node.setPhysicalOp(detail);
            node.setLogicalOp(detail);
            node.setSubtreeCost(1.0);
            node.setCostPercent(100.0 / Math.max(rows.size(), 1));

            nodesMap.put(nid, node);
            parentMap.put(nid, pid);
        }

        // Build hierarchy
        List<PlanOperatorNode> rootNodes = new ArrayList<>();
        for (Map.Entry<Integer, PlanOperatorNode> entry : nodesMap.entrySet()) {
            int nid = entry.getKey();
            PlanOperatorNode node = entry.getValue();
            int pid = parentMap.getOrDefault(nid, 0);
            if (nodesMap.containsKey(pid) && pid != nid) {
                nodesMap.get(pid).addChild(node);
            } else {
                rootNodes.add(node);
            }
        }

        PlanOperatorNode root = rootNodes.isEmpty() ? new PlanOperatorNode("0", "Result") : rootNodes.get(0);
        PhysicalExecutionPlan plan = new PhysicalExecutionPlan("sqlite", planType, 1.0, root);
        return plan;
    }

    // =========================================================================
    // 4. Simulated / Fallback Plan Generator
    // =========================================================================

    public PhysicalExecutionPlan generateSimulatedPlan(String sql, String dialect, String planType) {
        String sqlUpper = (sql != null ? sql.toUpperCase() : "");
        boolean isJoin = sqlUpper.contains("JOIN");
        boolean isAgg = sqlUpper.contains("GROUP BY") || sqlUpper.contains("SUM(") || sqlUpper.contains("COUNT(");
        boolean isSort = sqlUpper.contains("ORDER BY");
        boolean isActual = planType.equalsIgnoreCase("actual");

        // Extract tables from SQL
        List<String> tableNames = new ArrayList<>();
        Pattern tablePattern = Pattern.compile("\\b(?:FROM|JOIN)\\s+(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = tablePattern.matcher(sql != null ? sql : "");
        while (matcher.find()) {
            String t = matcher.group(2) != null ? matcher.group(2) : matcher.group(1);
            if (t != null && !tableNames.contains(t)) {
                tableNames.add(t);
            }
        }
        if (tableNames.isEmpty()) {
            tableNames.add("Customer");
            if (isJoin) tableNames.add("Orders");
        }

        double totalCost = 4.258;

        // Leaf 1
        PlanOperatorNode leaf1 = new PlanOperatorNode("1", "Seq Scan");
        leaf1.setRelationName(tableNames.get(0));
        leaf1.setEstimatedRows(1200);
        leaf1.setActualRows(isActual ? 1200.0 : null);
        leaf1.setSubtreeCost(1.45);
        leaf1.setOperatorCost(1.45);
        leaf1.setCostPercent(34.1);
        leaf1.setActualTimeMs(isActual ? 1.2 : null);

        PlanOperatorNode currentTop = leaf1;

        if (isJoin && tableNames.size() > 1) {
            PlanOperatorNode leaf2 = new PlanOperatorNode("2", "Index Scan");
            leaf2.setRelationName(tableNames.get(1));
            leaf2.setEstimatedRows(5400);
            leaf2.setActualRows(isActual ? 5380.0 : null);
            leaf2.setSubtreeCost(1.80);
            leaf2.setOperatorCost(1.80);
            leaf2.setCostPercent(42.3);
            leaf2.setActualTimeMs(isActual ? 2.5 : null);

            PlanOperatorNode joinNode = new PlanOperatorNode("3", "Hash Match (Inner Join)");
            joinNode.setEstimatedRows(5400);
            joinNode.setActualRows(isActual ? 5380.0 : null);
            joinNode.setSubtreeCost(3.60);
            joinNode.setOperatorCost(0.35);
            joinNode.setCostPercent(8.2);
            joinNode.setActualTimeMs(isActual ? 3.9 : null);
            joinNode.addChild(leaf1);
            joinNode.addChild(leaf2);
            currentTop = joinNode;
        }

        if (isAgg) {
            PlanOperatorNode aggNode = new PlanOperatorNode("4", "Hash Aggregate");
            aggNode.setEstimatedRows(150);
            aggNode.setActualRows(isActual ? 150.0 : null);
            aggNode.setSubtreeCost(3.95);
            aggNode.setOperatorCost(0.35);
            aggNode.setCostPercent(8.2);
            aggNode.setActualTimeMs(isActual ? 4.3 : null);
            aggNode.addChild(currentTop);
            currentTop = aggNode;
        }

        if (isSort) {
            PlanOperatorNode sortNode = new PlanOperatorNode("5", "Sort");
            sortNode.setEstimatedRows(150);
            sortNode.setActualRows(isActual ? 150.0 : null);
            sortNode.setSubtreeCost(4.258);
            sortNode.setOperatorCost(0.308);
            sortNode.setCostPercent(7.2);
            sortNode.setActualTimeMs(isActual ? 4.8 : null);
            sortNode.addChild(currentTop);
            currentTop = sortNode;
        }

        PhysicalExecutionPlan plan = new PhysicalExecutionPlan(dialect, planType, totalCost, currentTop);
        plan.setSimulated(true);
        plan.setRawPlanText(plan.toTextTree());
        return plan;
    }

    private static Double parseDouble(String str, Double def) {
        if (str == null || str.isBlank()) return def;
        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return def;
        }
    }
}
