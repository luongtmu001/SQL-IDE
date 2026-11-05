package app;

import app.controllers.ResultsController;
import app.models.ConsoleMessage;
import app.models.QueryResult;
import app.models.QueryResultColumn;
import app.models.QueryResultRow;
import app.ui.results.GridDataExportUtils;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ResultsGridTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Toolkit already initialized
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX failed to initialize");
    }

    @Test
    @DisplayName("TC01 - TSV formatting with and without headers and RFC 4180 escaping")
    void testTsvFormatting() {
        List<String> cols = List.of("id", "name", "notes");
        List<List<Object>> rows = List.of(
            List.of(1, "Alice", "Simple text"),
            List.of(2, "Bob", "Line 1\nLine 2"),
            List.of(3, "Charlie", "Has \"quotes\" and \ttabs"),
            List.of(4, "Dave", "")
        );

        String tsvWithHeaders = GridDataExportUtils.formatTsv(cols, rows, true);
        assertTrue(tsvWithHeaders.startsWith("id\tname\tnotes\r\n"), "TSV must start with header row");
        assertTrue(tsvWithHeaders.contains("1\tAlice\tSimple text"), "TSV must contain simple row");
        assertTrue(tsvWithHeaders.contains("\"Line 1\nLine 2\""), "TSV must quote cells with newlines");
        assertTrue(tsvWithHeaders.contains("\"Has \"\"quotes\"\" and \ttabs\""), "TSV must double internal quotes");

        String tsvNoHeaders = GridDataExportUtils.formatTsv(cols, rows, false);
        assertFalse(tsvNoHeaders.startsWith("id\tname\tnotes"), "TSV without headers must omit header row");
    }

    @Test
    @DisplayName("TC02, TC03, TC04 - SQL Literals and Script as INSERT adhering to .SKILL/result_sql.md")
    void testSqlLiteralsAndScriptAsInsert() {
        // TC02: Empty string must produce '' not NULL
        assertEquals("''", GridDataExportUtils.formatSqlLiteral(""), "Empty string must format as ''");

        // TC03: Whitespace must be preserved exactly, NEVER trimmed
        assertEquals("'   '", GridDataExportUtils.formatSqlLiteral("   "), "Whitespace must preserve all 3 spaces without trim");

        // TC04: NULL must produce NULL keyword without quotes
        assertEquals("NULL", GridDataExportUtils.formatSqlLiteral(null), "Null must produce keyword NULL");

        // Quotes escaping
        assertEquals("'O''Reilly'", GridDataExportUtils.formatSqlLiteral("O'Reilly"), "Single quotes must be doubled");

        // Script as INSERT
        List<String> cols = List.of("id", "code", "notes", "salary");
        List<List<Object>> rows = List.of(
            List.of(1, "", "   ", 50000.50),
            java.util.Arrays.asList(2, "XYZ", null, 65000)
        );

        String script = GridDataExportUtils.formatScriptAsInsert("#tabletmp", cols, rows);
        assertTrue(script.contains("CREATE TABLE #tabletmp"), "Script must contain CREATE TABLE #tabletmp");
        assertTrue(script.contains("INSERT INTO #tabletmp ([id], [code], [notes], [salary]) VALUES"), "Script must contain INSERT INTO");
        assertTrue(script.contains("(1, '', '   ', 50000.5)"), "Script must properly format empty string and untrimmed whitespace");
        assertTrue(script.contains("(2, 'XYZ', NULL, 65000)"), "Script must format null as NULL keyword");
    }

    @Test
    @DisplayName("TC05 - SQL values list formatting (single column and multi-column)")
    void testSqlValuesListFormatting() {
        // Single column
        List<String> singleCol = List.of("category");
        List<List<Object>> singleRows = List.of(
            List.of("Electronics"),
            List.of("Books"),
            List.of("Clothing")
        );
        String singleList = GridDataExportUtils.formatSqlValuesList(singleCol, singleRows);
        assertEquals("('Electronics', 'Books', 'Clothing')", singleList, "Single column must format as ('val1', 'val2')");

        // Multi column
        List<String> multiCols = List.of("id", "val");
        List<List<Object>> multiRows = List.of(
            List.of(1, "A"),
            List.of(2, "B")
        );
        String multiList = GridDataExportUtils.formatSqlValuesList(multiCols, multiRows);
        assertTrue(multiList.contains("(1, 'A')"), "Multi column must contain row 1");
        assertTrue(multiList.contains("(2, 'B')"), "Multi column must contain row 2");
    }

    @Test
    @DisplayName("TC06 - Mathematical Aggregates calculation (Count, Num Count, Sum, Avg, Min, Max)")
    void testAggregatesCalculation() {
        List<List<Object>> rows = List.of(
            List.of("Text", 10, "1,000"),
            java.util.Arrays.asList(null, 20, 30.5),
            List.of("", "Invalid", 40)
        );

        var agg = GridDataExportUtils.calculateAggregates(rows);
        assertEquals(9, agg.totalCells(), "Total cells must be 9");
        assertEquals(5, agg.numericCount(), "Numeric cells count must be 5 (10, 1000, 20, 30.5, 40)");
        assertEquals(1100.5, agg.sum(), 0.001, "Sum must be 1100.5");
        assertEquals(220.1, agg.average(), 0.001, "Average must be 220.1");
        assertEquals(10.0, agg.min(), 0.001, "Min must be 10.0");
        assertEquals(1000.0, agg.max(), 0.001, "Max must be 1000.0");
    }

    @Test
    @DisplayName("TC07 - XML Spreadsheet 2003 schema generation for Excel clipboard")
    void testXmlSpreadsheetFormatting() {
        List<String> cols = List.of("Col1", "Col2");
        List<List<Object>> rows = List.of(List.of("Val1", 123));

        String xml = GridDataExportUtils.formatXmlSpreadsheet(cols, rows);
        assertTrue(xml.contains("xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\""), "Must contain spreadsheet XML namespace");
        assertTrue(xml.contains("<Data ss:Type=\"String\">Col1</Data>"), "Must contain header string Col1");
        assertTrue(xml.contains("<Data ss:Type=\"Number\">123</Data>"), "Must contain typed number cell");
    }

    @Test
    @DisplayName("TC08 - Hex Dump and Structured Data candidate detection")
    void testHexDumpAndStructuredData() {
        String hex = GridDataExportUtils.formatHexDump("Hello World");
        assertTrue(hex.startsWith("00000000: 48 65 6c 6c 6f 20 57 6f  72 6c 64"), "Hex dump must show correct offsets and bytes");
        assertTrue(hex.contains("| Hello World"), "Hex dump must show ASCII characters");

        assertEquals("json", GridDataExportUtils.detectStructuredCandidate("{\"status\": \"ok\"}"));
        assertEquals("json", GridDataExportUtils.detectStructuredCandidate("[1, 2, 3]"));
        assertEquals("xml", GridDataExportUtils.detectStructuredCandidate("<root><item>1</item></root>"));
        assertNull(GridDataExportUtils.detectStructuredCandidate("Plain string"));
        assertNull(GridDataExportUtils.detectStructuredCandidate(null));
    }

    @Test
    @DisplayName("TC09 - ResultsController dynamic data binding and context menu inspection")
    void testResultsControllerIntegration() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/results.fxml"));
                loader.load();
                ResultsController loadedCtrl = loader.getController();

                assertNotNull(loadedCtrl, "ResultsController should load cleanly from FXML");
                assertNotNull(loadedCtrl.getResultsTable(), "Results table should not be null");

                // Set sample query result with null and special values
                List<QueryResultColumn> cols = List.of(
                    new QueryResultColumn("id", "INT"),
                    new QueryResultColumn("name", "VARCHAR"),
                    new QueryResultColumn("description", "TEXT")
                );
                List<QueryResultRow> rows = List.of(
                    new QueryResultRow(1, List.of(101, "First Item", "Desc 1")),
                    new QueryResultRow(2, java.util.Arrays.asList(102, null, "Desc 2"))
                );
                QueryResult result = new QueryResult("SELECT * FROM items", cols, rows, 25, "12:00:00");
                loadedCtrl.setQueryResult(result);

                // Check table columns: '#' column + 3 data columns = 4 columns
                assertEquals(4, loadedCtrl.getResultsTable().getColumns().size(), "Table should have row number '#' and 3 data columns");
                assertEquals("#", loadedCtrl.getResultsTable().getColumns().get(0).getText());
                assertEquals("id [INT]", loadedCtrl.getResultsTable().getColumns().get(1).getText());

                // Check rows count
                assertEquals(2, loadedCtrl.getMasterRows().size(), "Master rows should contain 2 rows");

                // Test Messages & Timeline tabs
                loadedCtrl.addMessage(ConsoleMessage.Level.SUCCESS, "Executed successfully", "15ms");
                loadedCtrl.addMessage(ConsoleMessage.Level.ERROR, "Syntax error", "2ms");
                assertEquals("Messages", loadedCtrl.getResultsTabPane().getTabs().get(1).getText());
                assertEquals("Timeline (6)", loadedCtrl.getResultsTabPane().getTabs().get(2).getText());

                // Test Multi-Grid Support
                QueryResult result2 = new QueryResult("SELECT * FROM items_2", cols, rows, 10, "12:00:01");
                loadedCtrl.setQueryResults(List.of(result, result2));
                assertTrue(loadedCtrl.getResultsTabPane().getTabs().get(0).getText().contains("2 bảng"), "Multi-grid tab text should show 2 tables");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
                fail("Failed to load ResultsController: " + e.getMessage());
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Controller test timed out");
    }
}
