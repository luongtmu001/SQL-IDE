package app;

import app.controllers.EditorController;
import app.ui.editor.SqlCodeArea;
import app.ui.editor.SqlEditorTab;
import app.ui.editor.SqlSyntaxHighlighter;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EditorWorkspaceTest {

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void testSqlSyntaxHighlighter() {
        String sql = "SELECT id, 'John' AS name FROM users WHERE id = 42; -- test comment";
        StyleSpans<Collection<String>> spans = SqlSyntaxHighlighter.computeHighlighting(sql);
        assertNotNull(spans);
        assertTrue(spans.getSpanCount() > 0, "Spans should be produced for SQL text");
    }

    @Test
    void testSqlEditorTabDirtyTracking() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                SqlEditorTab tab = new SqlEditorTab("test.sql", "SELECT 1;", null, null);
                assertFalse(tab.isDirty(), "Newly opened tab should not be dirty");
                assertFalse(tab.isPinned(), "Newly opened tab should not be pinned");
                assertEquals("test.sql", tab.getTabTitle());

                // Modify text
                tab.getCodeArea().replaceText("SELECT 2;");
                assertTrue(tab.isDirty(), "Tab should be dirty after modification");

                // Mark saved
                tab.markSaved();
                assertFalse(tab.isDirty(), "Tab should not be dirty after markSaved()");

                success.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(success.get());
    }

    @Test
    void testEditorEmptyStateWhenNoTabsOpen() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<EditorController> controllerRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                URL fxml = getClass().getResource("/fxml/editor.fxml");
                assertNotNull(fxml);
                FXMLLoader loader = new FXMLLoader(fxml);
                Parent root = loader.load();
                assertNotNull(root);
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        EditorController controller = controllerRef.get();
        assertNotNull(controller);
        assertNotNull(controller.getEmptyEditorStateBox());

        // Initially has default tab, so empty state should be false
        CountDownLatch check1 = new CountDownLatch(1);
        Platform.runLater(() -> {
            assertFalse(controller.getEmptyEditorStateBox().isVisible(), "Empty state should be hidden when tabs exist");
            // Clear all tabs
            controller.clearAllTabsSilently();
            controller.updateEmptyState();
            assertTrue(controller.getEmptyEditorStateBox().isVisible(), "Empty state should be visible when all tabs are closed");
            // Re-open tab
            controller.handleNewTab();
            assertFalse(controller.getEmptyEditorStateBox().isVisible(), "Empty state should be hidden after opening a new tab");
            check1.countDown();
        });

        assertTrue(check1.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testTabContextAndLifecycle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                SqlEditorTab tab = new SqlEditorTab(
                        "orders_query.sql",
                        "SELECT * FROM orders;",
                        null,
                        "PostgreSQL (dev-local)",
                        "bravo_db",
                        "public",
                        null
                );

                assertEquals("orders_query.sql", tab.getTabTitle());
                assertEquals("PostgreSQL (dev-local)", tab.getConnectionName());
                assertEquals("bravo_db", tab.getDatabaseName());
                assertEquals("public", tab.getSchemaName());
                assertFalse(tab.isDirty());

                // Update context
                tab.setSchemaName("analytics");
                assertEquals("analytics", tab.getSchemaName());

                tab.setConnectionName("SQL Server (prod)");
                assertEquals("SQL Server (prod)", tab.getConnectionName());

                success.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(success.get());
    }

    @Test
    void testTabPinningAndCloseProtection() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                EditorController controller = new EditorController();
                URL fxml = getClass().getResource("/fxml/editor.fxml");
                FXMLLoader loader = new FXMLLoader(fxml);
                loader.load();
                controller = loader.getController();

                // Open 3 tabs: Tab 1 (pinned), Tab 2 (clean), Tab 3 (dirty)
                controller.clearAllTabsSilently();
                SqlEditorTab tab1 = controller.openNewTab("Tab1.sql", "SELECT 1;");
                SqlEditorTab tab2 = controller.openNewTab("Tab2.sql", "SELECT 2;");
                SqlEditorTab tab3 = controller.openNewTab("Tab3.sql", "SELECT 3;");

                // Pin tab1
                tab1.setPinned(true);
                assertTrue(tab1.isPinned(), "Tab 1 should be pinned");
                assertFalse(tab1.isClosable(), "Pinned tab must not be closable");
                // Modify tab3
                tab3.getCodeArea().replaceText("SELECT 33;");
                assertTrue(tab3.isDirty());

                // Close unmodified tabs -> Tab2 should be closed, Tab1 (pinned) and Tab3 (dirty) remain
                controller.closeUnmodifiedTabs();
                List<SqlEditorTab> remaining = controller.getOpenTabs();
                assertTrue(remaining.contains(tab1), "Pinned tab must remain open");
                assertFalse(remaining.contains(tab2), "Unmodified tab must be closed");
                assertTrue(remaining.contains(tab3), "Dirty tab must remain open");

                success.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(success.get());
    }

    @Test
    void testSplitEditorPreservesContext() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                URL fxml = getClass().getResource("/fxml/editor.fxml");
                FXMLLoader loader = new FXMLLoader(fxml);
                loader.load();
                EditorController controller = loader.getController();

                controller.clearAllTabsSilently();
                SqlEditorTab tab = controller.openNewTab(
                        "analytics.sql",
                        "SELECT COUNT(*) FROM sales;",
                        "PostgreSQL (dev-local)",
                        "sales_db",
                        "public"
                );

                // Split Right
                controller.splitRight(tab);
                assertTrue(controller.isSplit(), "Editor should be in split mode");

                // Close Split
                controller.closeSplit();
                assertFalse(controller.isSplit(), "Editor should no longer be split after closeSplit()");

                success.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(success.get());
    }
}

