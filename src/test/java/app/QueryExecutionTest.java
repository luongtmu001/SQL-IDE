package app;

import app.controllers.EditorController;
import app.framework.query.DesktopQueryExecutionService;
import app.models.QueryResult;
import app.services.LayoutService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class QueryExecutionTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ignored) {
            latch.countDown();
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {}
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        app.framework.bootstrap.DesktopBootstrap.getInstance();
    }

    @Test
    void testQueryExecutionSelectSuccess() throws Exception {
        DesktopQueryExecutionService service = DesktopQueryExecutionService.getInstance();
        assertNotNull(service);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<QueryResult> resultRef = new AtomicReference<>();
        AtomicReference<String> summaryRef = new AtomicReference<>();

        service.execute("SELECT 100 AS num, 'SQL-IDE' AS app_name;", null, null, null, new DesktopQueryExecutionService.QueryCallback() {
            @Override
            public void onStart(String sql, String connectionName) {}

            @Override
            public void onSuccess(QueryResult result, int rowsAffected, String summaryMessage, long durationMs) {
                resultRef.set(result);
                summaryRef.set(summaryMessage);
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage, Throwable cause) {
                latch.countDown();
            }

            @Override
            public void onCancelled() {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Query execution should complete within 5s");
        QueryResult res = resultRef.get();
        assertNotNull(res, "QueryResult should not be null");
        assertEquals(1, res.getRowCount(), "Should have retrieved 1 row");
        assertEquals(2, res.getColumnCount(), "Should have retrieved 2 columns");
        assertEquals("num", res.getColumns().get(0).getName());
        assertEquals("app_name", res.getColumns().get(1).getName());
        assertNotNull(summaryRef.get());
    }

    @Test
    void testQueryExecutionDmlUpdateCount() throws Exception {
        DesktopQueryExecutionService service = DesktopQueryExecutionService.getInstance();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        String ddl = "CREATE TABLE IF NOT EXISTS demo_items (id INT, val TEXT);";
        service.execute(ddl, null, null, null, new DesktopQueryExecutionService.QueryCallback() {
            @Override
            public void onStart(String sql, String connectionName) {}

            @Override
            public void onSuccess(QueryResult result, int rowsAffected, String summaryMessage, long durationMs) {
                assertNull(result, "DDL/DML does not return a ResultSet");
                assertTrue(summaryMessage.contains("affected") || summaryMessage.contains("successfully"));
                success.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage, Throwable cause) {
                latch.countDown();
            }

            @Override
            public void onCancelled() {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(success.get());
    }

    @Test
    void testQueryExecutionSyntaxError() throws Exception {
        DesktopQueryExecutionService service = DesktopQueryExecutionService.getInstance();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();

        service.execute("SELECT * FORM invalid_syntax_table;;;", null, null, null, new DesktopQueryExecutionService.QueryCallback() {
            @Override
            public void onStart(String sql, String connectionName) {}

            @Override
            public void onSuccess(QueryResult result, int rowsAffected, String summaryMessage, long durationMs) {
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage, Throwable cause) {
                errorRef.set(errorMessage);
                latch.countDown();
            }

            @Override
            public void onCancelled() {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(errorRef.get(), "Error message must be captured for invalid SQL syntax");
    }

    @Test
    void testResultsPanelDefaultHiddenOnNewTabAndRememberPosition() throws Exception {
        LayoutService layout = LayoutService.getInstance();

        // Simulate user setting a custom divider position
        layout.setResultsDividerPosition(0.72);
        assertEquals(0.72, layout.getResultsDividerPosition(), 0.001);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<EditorController> editorCtrlRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor.fxml"));
                Parent root = loader.load();
                editorCtrlRef.set(loader.getController());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        EditorController editorController = editorCtrlRef.get();
        assertNotNull(editorController);

        CountDownLatch tabLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            // Opening a new tab must ensure results panel is hidden
            editorController.openNewTab("test_results_hide.sql", "SELECT 42;");
            tabLatch.countDown();
        });

        assertTrue(tabLatch.await(5, TimeUnit.SECONDS));

        // 1. Verify results panel is hidden by default when adding a tab
        assertFalse(layout.isResultsVisible(), "Results panel must be hidden by default on new tab");

        // 2. Verify divider position was NOT lost
        assertEquals(0.72, layout.getResultsDividerPosition(), 0.001, "Divider position must be preserved");

        // 3. Toggle results visible (simulating Ctrl+R)
        layout.setResultsVisible(true);
        assertTrue(layout.isResultsVisible(), "Results panel must be visible after toggle/Ctrl+R");
        assertEquals(0.72, layout.getResultsDividerPosition(), 0.001, "Divider position must remain preserved when re-shown");
    }
}

