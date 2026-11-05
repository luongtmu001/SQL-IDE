package app;

import app.controllers.SidebarController;
import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import app.services.DatabaseMockService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseExplorerTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {
        }
    }

    @Test
    void testMockServiceGeneratesHierarchy() {
        DatabaseNode root = DatabaseMockService.getInstance().createMockRoot();
        assertNotNull(root, "Root must not be null");
        assertEquals(DatabaseNodeType.ROOT, root.getType());
        assertEquals(3, root.getChildren().size(), "Should have 3 mock connections");

        DatabaseNode postgresConn = root.getChildren().get(0);
        assertEquals(DatabaseNodeType.CONNECTION, postgresConn.getType());
        assertTrue(postgresConn.getName().contains("PostgreSQL"));

        DatabaseNode postgresDb = postgresConn.getChildren().get(0);
        assertEquals(DatabaseNodeType.DATABASE, postgresDb.getType());

        DatabaseNode publicSchema = postgresDb.getChildren().get(0);
        assertEquals(DatabaseNodeType.SCHEMA, publicSchema.getType());

        DatabaseNode tablesCat = publicSchema.getChildren().get(0);
        assertEquals(DatabaseNodeType.CATEGORY_TABLES, tablesCat.getType());
        assertTrue(tablesCat.getChildren().size() >= 4, "Should have at least 4 tables");

        // Verify users table and PK column
        DatabaseNode usersTable = tablesCat.getChildren().get(0);
        assertEquals("users", usersTable.getName());
        assertEquals(DatabaseNodeType.TABLE, usersTable.getType());

        DatabaseNode idColumn = usersTable.getChildren().get(0);
        assertEquals("id", idColumn.getName());
        assertEquals(DatabaseNodeType.COLUMN, idColumn.getType());
        assertTrue(idColumn.isPrimaryKey(), "id column should be primary key");
    }

    @Test
    void testFilterLogic() {
        DatabaseNode root = DatabaseMockService.getInstance().createMockRoot();

        // Search for "orders"
        int ordersCount = countMatching(root, "orders");
        assertTrue(ordersCount > 0, "Should find 'orders' related objects");

        // Search for non-existent item
        int nonExistentCount = countMatching(root, "non_existent_xyz");
        assertEquals(0, nonExistentCount, "Should find 0 matches for non existent item");
    }

    @Test
    void testMiniSidebarCollapsedMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SidebarController> controllerRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/sidebar.fxml"));
                Parent root = loader.load();
                rootRef.set(root);
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Sidebar FXML should load within 5s");
        SidebarController controller = controllerRef.get();
        assertNotNull(controller, "Controller must be loaded");

        // Test collapse to mini strip on FX thread
        CountDownLatch collapseLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            controller.setCollapsed(true);
            collapseLatch.countDown();
        });
        assertTrue(collapseLatch.await(5, TimeUnit.SECONDS));

        // Find mini connection box
        VBox miniBox = (VBox) rootRef.get().lookup("#miniConnectionIconsBox");
        assertNotNull(miniBox, "miniConnectionIconsBox should exist");
        assertFalse(miniBox.getChildren().isEmpty(), "Mini strip should contain connection buttons");
        assertTrue(miniBox.getChildren().size() >= 3, "Should have at least 3 connection buttons in mini strip");

        // Verify tooltips on buttons
        for (javafx.scene.Node node : miniBox.getChildren()) {
            assertTrue(node instanceof Button, "Each connection icon must be a Button");
            Button btn = (Button) node;
            Tooltip tip = btn.getTooltip();
            assertNotNull(tip, "Connection button must have a tooltip");
            assertNotNull(tip.getText(), "Tooltip text must not be null");
            assertTrue(tip.getText().contains("Connection:"), "Tooltip should contain connection details");
        }
    }

    private int countMatching(DatabaseNode node, String query) {
        int count = 0;
        for (DatabaseNode child : node.getChildren()) {
            if (child.getName().toLowerCase().contains(query.toLowerCase())) {
                count++;
            }
            count += countMatching(child, query);
        }
        return count;
    }
}
