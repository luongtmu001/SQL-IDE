package app.ui.dependency;

import com.lltool.core.application.dependency.DependencyGraphService;
import com.lltool.core.domain.dependency.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature 05: DependencyGraphService Test Suite")
class DependencyGraphServiceTest {

    private DependencyGraphService service;

    @BeforeEach
    void setUp() {
        service = new DependencyGraphService();
    }

    @Test
    @DisplayName("createSampleOrdersGraph: BOTH direction should contain upstream and downstream nodes")
    void testSampleGraphBothDirections() {
        DependencyGraphModel model = service.createSampleOrdersGraph(GraphDirection.BOTH, 2);

        assertNotNull(model);
        assertEquals("dbo.orders", model.getRootNodeId());
        assertNotNull(model.getRootNode());
        assertTrue(model.getRootNode().isRoot());

        // Should have customers (upstream) and views/proc/trigger (downstream)
        assertTrue(model.getNodeCount() >= 6, "Expected at least 6 nodes in sample graph");
        assertTrue(model.getEdgeCount() >= 5, "Expected at least 5 edges in sample graph");

        DependencyNode root = model.getRootNode();
        assertEquals(0, root.getLayer(), "Root node layer should be 0");

        // Verify upstream node
        DependencyNode cust = model.getNode("dbo.customers");
        assertNotNull(cust);
        assertEquals(-1, cust.getLayer(), "Upstream customer should be layer -1");
        assertTrue(cust.getX() < root.getX(), "Upstream customer X should be to the left of Root");

        // Verify downstream nodes
        DependencyNode vOrders = model.getNode("dbo.v_customer_orders");
        assertNotNull(vOrders);
        assertEquals(1, vOrders.getLayer(), "Downstream view should be layer 1");
        assertTrue(vOrders.getX() > root.getX(), "Downstream view X should be to the right of Root");
    }

    @Test
    @DisplayName("createSampleOrdersGraph: DEPENDENTS only should not contain upstream dependencies")
    void testSampleGraphDependentsOnly() {
        DependencyGraphModel model = service.createSampleOrdersGraph(GraphDirection.DEPENDENTS, 2);

        assertNotNull(model);
        assertNull(model.getNode("dbo.customers"), "Upstream customers should not be included");
        assertNotNull(model.getNode("dbo.v_customer_orders"));
        assertNotNull(model.getNode("dbo.v_monthly_revenue"));
        assertNotNull(model.getNode("dbo.usp_close_period"));
        assertNotNull(model.getNode("dbo.trg_orders_audit"));
    }

    @Test
    @DisplayName("createSampleOrdersGraph: DEPENDENCIES only should not contain downstream dependents")
    void testSampleGraphDependenciesOnly() {
        DependencyGraphModel model = service.createSampleOrdersGraph(GraphDirection.DEPENDENCIES, 2);

        assertNotNull(model);
        assertNotNull(model.getNode("dbo.customers"), "Upstream customers should be included");
        assertNull(model.getNode("dbo.v_customer_orders"), "Downstream view should not be included");
        assertNull(model.getNode("dbo.v_monthly_revenue"), "Downstream view should not be included");
        assertNull(model.getNode("dbo.usp_close_period"), "Downstream proc should not be included");
    }

    @Test
    @DisplayName("calculateHierarchicalLayout: assigns distinct vertical coordinates to prevent overlapping")
    void testLayoutVerticalSpacing() {
        DependencyGraphModel model = service.createSampleOrdersGraph(GraphDirection.DEPENDENTS, 1);
        service.calculateHierarchicalLayout(model);

        // Check layer 1 nodes have distinct Y coordinates
        List<DependencyEdge> outEdges = model.getOutgoingEdges("dbo.orders");
        assertTrue(outEdges.size() >= 4);

        double prevY = -999999;
        for (DependencyEdge edge : outEdges) {
            DependencyNode dep = model.getNode(edge.getTargetId());
            assertNotNull(dep);
            assertNotEquals(prevY, dep.getY(), "Adjacent nodes should not have identical Y coordinates");
            prevY = dep.getY();
        }
    }

    @Test
    @DisplayName("generateSuggestedSteps: produces safe sequential impact migration steps")
    void testGenerateSuggestedSteps() {
        DependencyGraphModel model = service.createSampleOrdersGraph(GraphDirection.BOTH, 2);
        List<SuggestedStep> steps = service.generateSuggestedSteps(model, "dbo.orders");

        assertNotNull(steps);
        assertFalse(steps.isEmpty(), "Suggested steps should not be empty");

        // Verify steps order: backup/drop views first, alter table, recreate views
        boolean sawDrop = false;
        boolean sawModify = false;
        boolean sawRecreate = false;

        for (SuggestedStep step : steps) {
            if (step.getActionType().contains("DROP_VIEW") || step.getActionType().contains("DISABLE_TRIGGER")) {
                sawDrop = true;
            }
            if (step.getActionType().contains("MODIFY_TABLE")) {
                sawModify = true;
                assertTrue(sawDrop, "Drop/disable step must precede table modification");
            }
            if (step.getActionType().contains("RECREATE_VIEW") || step.getActionType().contains("ENABLE_TRIGGER")) {
                sawRecreate = true;
                assertTrue(sawModify, "Recreate/enable step must follow table modification");
            }
        }

        assertTrue(sawDrop);
        assertTrue(sawModify);
        assertTrue(sawRecreate);
    }

    @Test
    @DisplayName("generateSuggestedSteps: for column target generates column alteration DDL")
    void testGenerateSuggestedStepsForColumn() {
        DependencyGraphModel model = new DependencyGraphModel("dbo.orders.customer_id", GraphDirection.BOTH, 2);
        DependencyNode colNode = DependencyNode.ofColumn("dbo", "orders", "customer_id", true);
        model.addNode(colNode);

        DependencyNode vOrders = DependencyNode.of("dbo", "v_customer_orders", DependencyNodeType.VIEW, false);
        model.addNode(vOrders);
        model.addEdge(DependencyEdge.of(colNode.getId(), vOrders.getId(), RelationType.USES_COLUMN, "SELECT"));

        List<SuggestedStep> steps = service.generateSuggestedSteps(model, colNode.getId());
        assertNotNull(steps);
        assertFalse(steps.isEmpty());

        boolean hasAlterCol = steps.stream().anyMatch(s -> s.getActionType().equals("ALTER_COLUMN"));
        assertTrue(hasAlterCol, "Expected ALTER_COLUMN step for column-level target");
    }

    @Test
    @DisplayName("DG-3: Suggested steps correctly orders multi-level dependents (dismantle reverse, recreate forward)")
    void testDismantleAndRecreationOrderingMultiLevel() {
        DependencyGraphModel model = new DependencyGraphModel("dbo.orders", GraphDirection.DEPENDENTS, 3);
        DependencyNode root = DependencyNode.of("dbo", "orders", DependencyNodeType.TABLE, true);
        model.addNode(root);

        // View 1 depends on orders (layer 1)
        DependencyNode v1 = DependencyNode.of("dbo", "v_orders", DependencyNodeType.VIEW, false);
        v1.setDefinitionSql("CREATE VIEW dbo.v_orders AS SELECT * FROM dbo.orders;");
        model.addNode(v1);
        model.addEdge(DependencyEdge.of(root.getId(), v1.getId(), RelationType.REFERENCES_VIEW, "SELECT"));

        // View 2 depends on View 1 (layer 2)
        DependencyNode v2 = DependencyNode.of("dbo", "v_summary", DependencyNodeType.VIEW, false);
        // null DDL definition for v2
        model.addNode(v2);
        model.addEdge(DependencyEdge.of(v1.getId(), v2.getId(), RelationType.REFERENCES_VIEW, "SELECT"));

        service.calculateHierarchicalLayout(model);
        List<SuggestedStep> steps = service.generateSuggestedSteps(model, root.getId());

        assertNotNull(steps);
        assertEquals(5, steps.size(), "Expected 5 steps: DROP v2, DROP v1, MODIFY orders, CREATE v1, CREATE v2");

        // 1. Drop v2 first (deepest dependent)
        assertEquals("BACKUP_OR_DROP_VIEW", steps.get(0).getActionType());
        assertEquals("dbo.v_summary", steps.get(0).getTargetObject());

        // 2. Drop v1 next
        assertEquals("BACKUP_OR_DROP_VIEW", steps.get(1).getActionType());
        assertEquals("dbo.v_orders", steps.get(1).getTargetObject());

        // 3. Middle step: clean MODIFY_TABLE placeholder
        assertEquals("MODIFY_TABLE", steps.get(2).getActionType());
        assertEquals("dbo.orders", steps.get(2).getTargetObject());
        assertTrue(steps.get(2).getSqlScript().contains("-- TODO: Áp dụng thay đổi"));

        // 4. Recreate v1 first (closest dependent)
        assertEquals("RECREATE_VIEW", steps.get(3).getActionType());
        assertEquals("dbo.v_orders", steps.get(3).getTargetObject());
        assertEquals("CREATE VIEW dbo.v_orders AS SELECT * FROM dbo.orders;", steps.get(3).getSqlScript());

        // 5. Recreate v2 next (missing definition message)
        assertEquals("RECREATE_VIEW", steps.get(4).getActionType());
        assertEquals("dbo.v_summary", steps.get(4).getTargetObject());
        assertTrue(steps.get(4).getSqlScript().contains("-- Không đọc được định nghĩa của dbo.v_summary"));
    }

    @Test
    @DisplayName("DG-5: Barycenter crossing reduction reorders nodes in layer to untangle edges")
    void testBarycenterCrossingReduction() {
        DependencyGraphModel model = new DependencyGraphModel("dbo.root", GraphDirection.DEPENDENTS, 2);
        DependencyNode root = DependencyNode.of("dbo", "root", DependencyNodeType.TABLE, true);
        model.addNode(root);

        // Layer 1 nodes: N1_A and N1_B
        DependencyNode n1A = DependencyNode.of("dbo", "n1_a", DependencyNodeType.VIEW, false);
        DependencyNode n1B = DependencyNode.of("dbo", "n1_b", DependencyNodeType.VIEW, false);
        model.addNode(n1A);
        model.addNode(n1B);
        model.addEdge(DependencyEdge.of(root.getId(), n1A.getId(), RelationType.REFERENCES_VIEW, "A"));
        model.addEdge(DependencyEdge.of(root.getId(), n1B.getId(), RelationType.REFERENCES_VIEW, "B"));

        // Layer 2 nodes: N2_X (connected to N1_B) and N2_Y (connected to N1_A)
        // If inserted as N2_X then N2_Y, crossing would exist if N1_A is above N1_B
        DependencyNode n2X = DependencyNode.of("dbo", "n2_x", DependencyNodeType.VIEW, false);
        DependencyNode n2Y = DependencyNode.of("dbo", "n2_y", DependencyNodeType.VIEW, false);
        model.addNode(n2X);
        model.addNode(n2Y);

        model.addEdge(DependencyEdge.of(n1A.getId(), n2Y.getId(), RelationType.REFERENCES_VIEW, "A->Y"));
        model.addEdge(DependencyEdge.of(n1B.getId(), n2X.getId(), RelationType.REFERENCES_VIEW, "B->X"));

        service.calculateHierarchicalLayout(model);

        // n1A y should correlate with n2Y y, and n1B y with n2X y
        if (n1A.getY() < n1B.getY()) {
            assertTrue(n2Y.getY() < n2X.getY(), "n2Y should be positioned above n2X to eliminate edge crossing");
        } else {
            assertTrue(n2X.getY() < n2Y.getY(), "n2X should be positioned above n2Y to eliminate edge crossing");
        }
    }
}
