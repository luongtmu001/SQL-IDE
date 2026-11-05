package app;

import app.controllers.ResultsController;
import app.framework.plan.DesktopExecutionPlanService;
import app.models.PhysicalExecutionPlan;
import app.models.PlanOperatorNode;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ExecutionPlanTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // JavaFX runtime already initialized
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Parse PostgreSQL EXPLAIN JSON structure into standardized PhysicalExecutionPlan")
    void testPostgresJsonPlanParsing() {
        String pgJson = """
                [
                  {
                    "Plan": {
                      "Node Type": "Hash Join",
                      "Parallel Aware": false,
                      "Async Capable": false,
                      "Join Type": "Inner",
                      "Startup Cost": 1.05,
                      "Total Cost": 4.50,
                      "Plan Rows": 100,
                      "Plan Width": 68,
                      "Actual Startup Time": 0.015,
                      "Actual Total Time": 0.045,
                      "Actual Rows": 95,
                      "Actual Loops": 1,
                      "Output": ["c.name", "o.amount"],
                      "Plans": [
                        {
                          "Node Type": "Seq Scan",
                          "Parent Relationship": "Outer",
                          "Relation Name": "customer",
                          "Schema": "public",
                          "Alias": "c",
                          "Startup Cost": 0.00,
                          "Total Cost": 1.20,
                          "Plan Rows": 200,
                          "Actual Rows": 200
                        },
                        {
                          "Node Type": "Index Scan",
                          "Parent Relationship": "Inner",
                          "Relation Name": "orders",
                          "Schema": "public",
                          "Alias": "o",
                          "Startup Cost": 0.00,
                          "Total Cost": 2.10,
                          "Plan Rows": 500,
                          "Actual Rows": 480
                        }
                      ]
                    }
                  }
                ]
                """;

        DesktopExecutionPlanService service = DesktopExecutionPlanService.getInstance();
        PhysicalExecutionPlan plan = service.parsePostgresJsonPlan(pgJson, "actual");

        assertNotNull(plan);
        assertEquals("postgresql", plan.getDialect());
        assertEquals("actual", plan.getPlanType());
        assertEquals(4.50, plan.getTotalCost(), 0.001);

        PlanOperatorNode root = plan.getRootNode();
        assertNotNull(root);
        assertEquals("Hash Join", root.getOperatorType());
        assertEquals(95.0, root.getActualRows());
        assertEquals(100.0, root.getEstimatedRows());
        assertEquals(2, root.getChildren().size());

        // Child 1: Seq Scan
        PlanOperatorNode child1 = root.getChildren().get(0);
        assertEquals("Seq Scan", child1.getOperatorType());
        assertEquals("customer", child1.getRelationName());
        assertEquals("public", child1.getSchema());
        assertEquals(200.0, child1.getActualRows());

        // Child 2: Index Scan
        PlanOperatorNode child2 = root.getChildren().get(1);
        assertEquals("Index Scan", child2.getOperatorType());
        assertEquals("orders", child2.getRelationName());

        // Check node count & flattening
        assertEquals(3, plan.getNodeCount());
    }

    @Test
    @DisplayName("Parse SQL Server ShowPlanXML into standardized PhysicalExecutionPlan")
    void testSqlServerXmlPlanParsing() {
        String sqlServerXml = """
                <ShowPlanXML xmlns="http://schemas.microsoft.com/sqlserver/2004/07/showplan" Version="1.5" Build="15.0">
                  <BatchSequence>
                    <Batch>
                      <Statements>
                        <StmtSimple StatementText="SELECT * FROM Customer">
                          <QueryPlan>
                            <RelOp NodeId="0" PhysicalOp="Nested Loops" LogicalOp="Inner Join" EstimateRows="100" EstimatedTotalSubtreeCost="2.50">
                              <NestedLoops>
                                <RelOp NodeId="1" PhysicalOp="Clustered Index Scan" LogicalOp="Clustered Index Scan" EstimateRows="50" EstimatedTotalSubtreeCost="1.00">
                                  <IndexScan>
                                    <Object Database="[AdventureWorks]" Schema="[Sales]" Table="[Customer]"/>
                                  </IndexScan>
                                </RelOp>
                                <RelOp NodeId="2" PhysicalOp="Index Seek" LogicalOp="Index Seek" EstimateRows="2" EstimatedTotalSubtreeCost="0.50">
                                  <IndexScan>
                                    <Object Database="[AdventureWorks]" Schema="[Sales]" Table="[SalesOrderHeader]"/>
                                  </IndexScan>
                                </RelOp>
                              </NestedLoops>
                            </RelOp>
                          </QueryPlan>
                        </StmtSimple>
                      </Statements>
                    </Batch>
                  </BatchSequence>
                </ShowPlanXML>
                """;

        DesktopExecutionPlanService service = DesktopExecutionPlanService.getInstance();
        PhysicalExecutionPlan plan = service.parseSqlServerXmlPlan(sqlServerXml, "estimated");

        assertNotNull(plan);
        assertEquals("sqlserver", plan.getDialect());
        assertEquals("estimated", plan.getPlanType());
        assertEquals(2.50, plan.getTotalCost(), 0.001);

        PlanOperatorNode root = plan.getRootNode();
        assertNotNull(root);
        assertEquals("Nested Loops", root.getOperatorType());
        assertEquals("Inner Join", root.getLogicalOp());
        assertEquals(2, root.getChildren().size());

        // Child 1: Clustered Index Scan on Customer
        PlanOperatorNode child1 = root.getChildren().get(0);
        assertEquals("Clustered Index Scan", child1.getOperatorType());
        assertEquals("Customer", child1.getRelationName());
        assertEquals("Sales", child1.getSchema());
        assertEquals("AdventureWorks", child1.getDatabase());

        // Child 2: Index Seek on SalesOrderHeader
        PlanOperatorNode child2 = root.getChildren().get(1);
        assertEquals("Index Seek", child2.getOperatorType());
        assertEquals("SalesOrderHeader", child2.getRelationName());

        assertEquals(3, plan.getNodeCount());
    }

    @Test
    @DisplayName("Parse SQLite EXPLAIN QUERY PLAN rows into hierarchy")
    void testSqlitePlanParsing() {
        List<List<Object>> rows = Arrays.asList(
                Arrays.asList(1, 0, 0, "SCAN TABLE Customer"),
                Arrays.asList(2, 0, 0, "SEARCH TABLE Orders USING INDEX idx_cust (CustomerId=?)")
        );

        DesktopExecutionPlanService service = DesktopExecutionPlanService.getInstance();
        PhysicalExecutionPlan plan = service.parseSqlitePlan(rows, "estimated");

        assertNotNull(plan);
        assertEquals("sqlite", plan.getDialect());
        assertTrue(plan.getNodeCount() >= 1);
    }

    @Test
    @DisplayName("Simulated fallback plan generation for offline and test environments")
    void testSimulatedPlanGeneration() {
        DesktopExecutionPlanService service = DesktopExecutionPlanService.getInstance();
        String sql = "SELECT c.Name, COUNT(o.Id) FROM Customer c JOIN Orders o ON c.Id = o.CustomerId GROUP BY c.Name ORDER BY c.Name";

        PhysicalExecutionPlan plan = service.generateSimulatedPlan(sql, "postgresql", "actual");
        assertNotNull(plan);
        assertEquals("postgresql", plan.getDialect());
        assertEquals("actual", plan.getPlanType());

        // Should include Sort, Aggregate, Join, Scans
        assertTrue(plan.getNodeCount() >= 4);

        PlanOperatorNode highest = plan.findHighestCostNode();
        assertNotNull(highest);
        assertTrue(highest.getCostPercent() > 0.0);

        String textTree = plan.toTextTree();
        assertNotNull(textTree);
        assertTrue(textTree.contains("Total Cost"));
        assertTrue(textTree.contains("Cost:"));
    }

    @Test
    @DisplayName("ResultsController Execution Plan UI integration")
    void testResultsControllerExecutionPlanUI() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = new Throwable[1];

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/results.fxml"));
                Parent root = loader.load();
                assertNotNull(root);

                ResultsController controller = loader.getController();
                assertNotNull(controller);
                assertNotNull(controller.getExecutionPlanTab());

                // Generate simulated plan and feed to controller
                PhysicalExecutionPlan plan = DesktopExecutionPlanService.getInstance()
                        .generateSimulatedPlan("SELECT * FROM Customer c JOIN Orders o ON c.Id = o.CustomerId", "sqlserver", "estimated");

                controller.setExecutionPlan(plan);
                controller.selectExecutionPlanTab();

                assertEquals(plan, controller.getCurrentExecutionPlan());

                // Test toggle view
                controller.handleTogglePlanViewMode();
                controller.handleTogglePlanViewMode();

                // Test copy
                controller.handleCopyExecutionPlan();

            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (error[0] != null) {
            fail("ResultsController plan integration failed: " + error[0].getMessage(), error[0]);
        }
    }
}

