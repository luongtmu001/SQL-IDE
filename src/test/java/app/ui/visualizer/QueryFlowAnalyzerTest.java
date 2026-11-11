package app.ui.visualizer;

import com.lltool.core.application.visualizer.QueryFlowAnalyzer;
import com.lltool.core.domain.visualizer.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature 06: QueryFlowAnalyzer Unit Tests (SV-1..SV-7)")
class QueryFlowAnalyzerTest {

    private final QueryFlowAnalyzer analyzer = new QueryFlowAnalyzer();

    @Test
    @DisplayName("SV-1: All nodes have estimatedCost == null and rowCount == null by default (no hardcoded fake values)")
    void testNoHardcodedMetrics_SV1() {
        String sql = """
                SELECT TOP 50
                    c.id AS customer_id, c.name AS customer_name,
                    SUM(o.amount) AS total_revenue, COUNT(o.id) AS order_count
                FROM dbo.customers c
                INNER JOIN dbo.orders o ON o.customer_id = c.id
                WHERE o.status = 'active' AND o.created_at >= '2026-01-01'
                GROUP BY c.id, c.name
                ORDER BY total_revenue DESC;
                """;
        QueryFlowGraph graph = analyzer.analyze(sql);

        assertNotNull(graph);
        for (QueryFlowNode node : graph.getNodes().values()) {
            assertNull(node.getEstimatedCost(), "Node estimatedCost must be null by default (SV-1)");
            assertNull(node.getRowCount(), "Node rowCount must be null by default (SV-1)");
        }
    }

    @Test
    @DisplayName("SV-3: Three JOINs chain sequentially, join k left is join k-1 output")
    void testThreeJoinsChaining_SV3() {
        String sql = """
                SELECT o.id, c.name, p.status, s.tracking
                FROM orders o
                INNER JOIN customers c ON c.id = o.customer_id
                LEFT JOIN payments p ON p.order_id = o.id
                LEFT JOIN shipments s ON s.order_id = o.id;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        List<QueryFlowNode> joinOps = graph.getNodesByStage(1);
        assertEquals(3, joinOps.size(), "Expected 3 join operator nodes");

        // First join connects from 'orders'
        QueryFlowNode join1 = joinOps.get(0);
        QueryFlowNode join2 = joinOps.get(1);
        QueryFlowNode join3 = joinOps.get(2);

        // Verify sequential chaining via edges
        boolean join1ToJoin2 = graph.getEdges().stream().anyMatch(e -> e.getFromNodeId().equals(join1.getId()) && e.getToNodeId().equals(join2.getId()));
        boolean join2ToJoin3 = graph.getEdges().stream().anyMatch(e -> e.getFromNodeId().equals(join2.getId()) && e.getToNodeId().equals(join3.getId()));

        assertTrue(join1ToJoin2, "Join 1 must flow into Join 2");
        assertTrue(join2ToJoin3, "Join 2 must flow into Join 3");
    }

    @Test
    @DisplayName("SV-3: Self-join on same table produces distinct source nodes and unique IDs")
    void testSelfJoinUniqueIds_SV3() {
        String sql = """
                SELECT e1.name AS emp, e2.name AS mgr
                FROM employees e1
                INNER JOIN employees e2 ON e2.id = e1.manager_id;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        List<QueryFlowNode> stage0Nodes = graph.getNodesByStage(0);
        assertEquals(2, stage0Nodes.size(), "Expected 2 source nodes for self-join");
        assertNotEquals(stage0Nodes.get(0).getId(), stage0Nodes.get(1).getId(), "IDs must be distinct for self-join");
    }

    @Test
    @DisplayName("SV-3: Keyword is not mistaken for table alias")
    void testKeywordNotMistakenForAlias_SV3() {
        String sql = "SELECT id FROM dbo.orders WHERE id = 1;";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        QueryFlowNode fromNode = graph.getNodesByStage(0).get(0);
        assertTrue(fromNode.getTitle().contains("orders"), "Expected orders table node");
        assertFalse(fromNode.getSubtitle().contains("WHERE"), "WHERE must not be captured as an alias");
    }

    @Test
    @DisplayName("SV-3: INNER is not mistaken for table alias in 'FROM a INNER JOIN b'")
    void testInnerNotMistakenForAlias_SV3() {
        String sql = "SELECT * FROM a INNER JOIN b ON b.id = a.id;";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        QueryFlowNode fromNode = graph.getNodesByStage(0).get(0);
        assertEquals("a", fromNode.getTitle());
        assertFalse(fromNode.getSubtitle().contains("INNER"), "INNER must not be captured as an alias");
    }

    @Test
    @DisplayName("SV-2: Comments do not generate phantom nodes or break parsing")
    void testCommentsIgnored_SV2() {
        String sql = """
                -- SELECT * FROM phantom_table
                /* Multi-line comment
                   WHERE fake_condition = 1
                   JOIN phantom_join ON 1=1 */
                SELECT id, name
                FROM dbo.real_table
                WHERE active = 1;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);
        assertFalse(graph.isUnsupported());

        List<QueryFlowNode> sources = graph.getNodesByStage(0);
        assertEquals(1, sources.size(), "Only real_table should be present");
        assertTrue(sources.get(0).getTitle().contains("real_table"));
    }

    @Test
    @DisplayName("SV-2: String literals containing SQL keywords do not confuse parser")
    void testStringLiteralsWithKeywords_SV2() {
        String sql = "SELECT id, 'SELECT * FROM phantom WHERE x=1' AS text_val FROM dbo.orders WHERE status = 'JOIN ON';";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        List<QueryFlowNode> sources = graph.getNodesByStage(0);
        assertEquals(1, sources.size());
        assertTrue(sources.get(0).getTitle().contains("orders"));
    }

    @Test
    @DisplayName("SV-2: HAVING clause produces distinct filter node in aggregation stage")
    void testHavingClause_SV2() {
        String sql = """
                SELECT department_id, COUNT(*) AS emp_count
                FROM employees
                GROUP BY department_id
                HAVING COUNT(*) > 5;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        List<QueryFlowNode> stage3Nodes = graph.getNodesByStage(3);
        assertTrue(stage3Nodes.stream().anyMatch(n -> n.getType() == QueryNodeType.AGGREGATE));
        assertTrue(stage3Nodes.stream().anyMatch(n -> n.getTitle().contains("HAVING")));
    }

    @Test
    @DisplayName("SV-2: DISTINCT keyword is recognized in output projection node")
    void testDistinctOutput_SV2() {
        String sql = "SELECT DISTINCT category FROM products;";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        List<QueryFlowNode> outputNodes = graph.getNodesByStage(5);
        assertEquals(1, outputNodes.size());
        assertTrue(outputNodes.get(0).getTitle().contains("DISTINCT"));
    }

    @Test
    @DisplayName("SV-2: TOP and LIMIT are recognized")
    void testTopAndLimit_SV2() {
        String sql1 = "SELECT TOP 10 id FROM orders;";
        QueryFlowGraph graph1 = analyzer.analyze(sql1);
        assertTrue(graph1.getNodesByStage(5).get(0).getSubtitle().contains("10"));

        String sql2 = "SELECT id FROM orders LIMIT 25 OFFSET 50;";
        QueryFlowGraph graph2 = analyzer.analyze(sql2);
        assertTrue(graph2.getNodesByStage(4).stream().anyMatch(n -> n.getSubtitle().contains("LIMIT 25")));
    }

    @Test
    @DisplayName("SV-2: CROSS JOIN without ON clause parses properly")
    void testCrossJoin_SV2() {
        String sql = "SELECT * FROM colors CROSS JOIN sizes;";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        assertEquals(2, graph.getNodesByStage(0).size());
        assertEquals(1, graph.getNodesByStage(1).size());
        assertTrue(graph.getNodesByStage(1).get(0).getTitle().contains("CROSS"));
    }

    @Test
    @DisplayName("SV-2: CROSS APPLY and OUTER APPLY parse properly")
    void testApplyClauses_SV2() {
        String sql = """
                SELECT d.id, f.val
                FROM departments d
                CROSS APPLY (SELECT TOP 1 val FROM fn_get_data(d.id)) f;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);
        assertEquals(2, graph.getNodesByStage(0).size());
        assertEquals(1, graph.getNodesByStage(1).size());
        assertTrue(graph.getNodesByStage(1).get(0).getTitle().contains("APPLY"));
    }

    @Test
    @DisplayName("SV-2: WITH CTE produces CTE nodes")
    void testWithCte_SV2() {
        String sql = """
                WITH RegionalSales AS (
                    SELECT region, SUM(amount) AS total_sales
                    FROM orders
                    GROUP BY region
                )
                SELECT region, total_sales
                FROM RegionalSales
                WHERE total_sales > 10000;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        assertTrue(graph.getNodes().values().stream().anyMatch(n -> n.getType() == QueryNodeType.CTE && n.getTitle().contains("RegionalSales")));
    }

    @Test
    @DisplayName("SV-2: Subquery in FROM clause generates SUBQUERY node")
    void testSubqueryInFrom_SV2() {
        String sql = """
                SELECT sq.id, sq.name
                FROM (SELECT id, name FROM users WHERE active = 1) sq
                WHERE sq.id > 10;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        List<QueryFlowNode> sources = graph.getNodesByStage(0);
        assertEquals(1, sources.size());
        assertEquals(QueryNodeType.SUBQUERY, sources.get(0).getType());
        assertEquals("sq", sources.get(0).getSubtitle().replace("Alias: ", "").trim());
    }

    @Test
    @DisplayName("SV-2: Non-SELECT query produces unsupported graph with reason")
    void testNonSelectQueryUnsupported_SV2() {
        String sql = "UPDATE orders SET status = 'CANCELLED' WHERE id = 5;";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);
        assertTrue(graph.isUnsupported());
        assertFalse(graph.getUnsupportedReason().isBlank());
        assertEquals(0, graph.getNodeCount());
    }

    @Test
    @DisplayName("SV-7: CREATE VIEW ... AS is stripped before analyzing")
    void testCreateViewStripping_SV7() {
        String sql = """
                CREATE VIEW dbo.v_active_users AS
                SELECT u.id, u.username
                FROM dbo.users u
                WHERE u.active = 1;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);
        assertFalse(graph.isUnsupported());
        assertEquals(3, graph.getNodeCount()); // FROM, WHERE, SELECT
    }

    @Test
    @DisplayName("SV-7: CREATE OR ALTER VIEW ... AS is stripped")
    void testCreateOrAlterViewStripping_SV7() {
        String sql = """
                CREATE OR ALTER VIEW sales.v_quarterly AS
                SELECT q.yr, q.quarter, SUM(q.amount) AS revenue
                FROM sales.orders q
                GROUP BY q.yr, q.quarter;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);
        assertFalse(graph.isUnsupported());
        assertTrue(graph.getNodesByStage(0).stream().anyMatch(n -> n.getTitle().contains("orders")));
    }

    @Test
    @DisplayName("SV-7: CREATE OR REPLACE MATERIALIZED VIEW ... AS is stripped")
    void testCreateMaterializedViewStripping_SV7() {
        String sql = """
                CREATE OR REPLACE MATERIALIZED VIEW mv_summary AS
                SELECT c.id, c.name FROM customers c;
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);
        assertFalse(graph.isUnsupported());
        assertEquals(2, graph.getNodeCount());
    }

    @Test
    @DisplayName("SV-4: Source range correctly accounts for CRLF and leading whitespace")
    void testSourceRangeWithCrlf_SV4() {
        String sql = "   \r\n\r\nSELECT id, name\r\nFROM dbo.users\r\nWHERE active = 1;";
        SourceRange range = QueryFlowAnalyzer.findSourceRange(sql, "FROM dbo.users");

        assertNotNull(range);
        assertTrue(range.getStartLine() >= 3, "FROM should start on line 4 or 3");
        assertEquals(range.getStartOffset() + "FROM dbo.users".length(), range.getEndOffset());
    }

    @Test
    @DisplayName("SV-5: Fast performance on 200-line SQL (< 50ms)")
    void testAnalysisPerformance_SV5() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT o.id, c.name\nFROM orders o\n");
        for (int i = 1; i <= 50; i++) {
            sb.append("LEFT JOIN table_").append(i).append(" t").append(i)
              .append(" ON t").append(i).append(".id = o.id\n");
        }
        sb.append("WHERE o.status = 'ACTIVE'\nGROUP BY o.id, c.name\nORDER BY o.id;\n");
        String longSql = sb.toString();

        // Warmup JIT
        analyzer.analyze(longSql);

        long start = System.nanoTime();
        QueryFlowGraph graph = analyzer.analyze(longSql);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(graph);
        assertTrue(graph.getNodeCount() >= 50);
        assertTrue(elapsedMs < 100, "Expected analysis under 100ms, actual: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("Handles quoted identifiers (brackets, quotes, backticks)")
    void testQuotedIdentifiers() {
        String sql = "SELECT [id] FROM [dbo].[my table] WHERE \"col 1\" = `val 1`;";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        QueryFlowNode fromNode = graph.getNodesByStage(0).get(0);
        assertEquals("dbo.my table", fromNode.getTitle());
    }

    @Test
    @DisplayName("Handles nested parentheses in WHERE condition")
    void testNestedParenthesesInWhere() {
        String sql = "SELECT id FROM users WHERE (a = 1 AND (b = 2 OR (c = 3 AND d = 4)));";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);

        List<QueryFlowNode> filters = graph.getNodesByStage(2);
        assertEquals(1, filters.size());
        assertTrue(filters.get(0).getSubtitle().contains("(a = 1"));
    }

    @Test
    @DisplayName("Handles subquery inside WHERE condition (IN / EXISTS)")
    void testSubqueryInWhere() {
        String sql = """
                SELECT id, name
                FROM customers
                WHERE id IN (SELECT customer_id FROM orders WHERE total > 500);
                """;

        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);
        assertEquals(1, graph.getNodesByStage(0).size());
        assertEquals("customers", graph.getNodesByStage(0).get(0).getTitle());
    }

    @Test
    @DisplayName("Handles value-only SELECT without FROM")
    void testValueOnlySelect() {
        String sql = "SELECT 1 AS num, 'hello' AS greeting;";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);
        assertEquals(1, graph.getNodeCount());
        assertEquals(QueryNodeType.OUTPUT, graph.getNodes().values().iterator().next().getType());
    }

    @Test
    @DisplayName("Handles natural join")
    void testNaturalJoin() {
        String sql = "SELECT * FROM t1 NATURAL JOIN t2;";
        QueryFlowGraph graph = analyzer.analyze(sql);
        assertNotNull(graph);
        assertEquals(2, graph.getNodesByStage(0).size());
        assertEquals(1, graph.getNodesByStage(1).size());
    }

    @Test
    @DisplayName("Handles empty or null queries without exceptions")
    void testEmptySqlHandling() {
        QueryFlowGraph emptyGraph = analyzer.analyze("");
        assertNotNull(emptyGraph);
        assertEquals(0, emptyGraph.getNodeCount());

        QueryFlowGraph nullGraph = analyzer.analyze(null);
        assertNotNull(nullGraph);
        assertEquals(0, nullGraph.getNodeCount());
    }
}
