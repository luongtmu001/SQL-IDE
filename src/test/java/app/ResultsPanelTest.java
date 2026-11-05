package app;

import app.controllers.ResultsController;
import app.models.ConsoleMessage;
import app.models.QueryResult;
import app.models.QueryResultColumn;
import app.models.QueryResultRow;
import app.services.QueryResultMockService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ResultsPanelTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    void testMockQueryResultGeneration() {
        QueryResult result = QueryResultMockService.getInstance().generateDefaultQueryResult();
        assertNotNull(result, "Query result should not be null");
        assertEquals(7, result.getColumnCount(), "Should have 7 columns");
        assertEquals(50, result.getRowCount(), "Should have 50 rows");
        assertEquals(42, result.getExecutionDurationMs());

        // Check columns
        assertEquals("id", result.getColumns().get(0).getName());
        assertEquals("username", result.getColumns().get(1).getName());
        assertEquals("email", result.getColumns().get(2).getName());

        // Check first row
        QueryResultRow firstRow = result.getRows().get(0);
        assertEquals(1, firstRow.getRowNumber());
        assertNotNull(firstRow.getValue(0));
        assertNotNull(firstRow.getValue(1));
    }

    @Test
    void testQueryResultRowCsvExportAndFilter() {
        QueryResultRow row = new QueryResultRow(1, List.of(101, "alex_dev", "alex@example.com", "active"));
        String csv = row.toCsvLine();
        assertEquals("101,alex_dev,alex@example.com,active", csv);

        assertTrue(row.matchesQuery("alex"));
        assertTrue(row.matchesQuery("101"));
        assertFalse(row.matchesQuery("non_existent_term"));
    }

    @Test
    void testInitialConsoleMessages() {
        List<ConsoleMessage> messages = QueryResultMockService.getInstance().generateInitialMessages();
        assertNotNull(messages);
        assertFalse(messages.isEmpty());
        assertTrue(messages.stream().anyMatch(m -> m.getLevel() == ConsoleMessage.Level.SUCCESS));
    }

    @Test
    void testResultsPanelContainsExecutionPlanTab() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ResultsController> controllerRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                URL fxml = getClass().getResource("/fxml/results.fxml");
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

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        ResultsController controller = controllerRef.get();
        assertNotNull(controller);
        assertNotNull(controller.getExecutionPlanTab(), "ResultsPanel must contain Execution Plan tab placeholder");
        assertEquals("Execution Plan", controller.getExecutionPlanTab().getText());
    }
}

