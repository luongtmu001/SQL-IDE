package app;

import app.controllers.ResultsController;
import app.models.QueryResult;
import app.models.QueryResultColumn;
import app.models.QueryResultRow;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** D4.3: each editor tab keeps its own results (LLTool restoreTabResults). */
class ResultsTabStateTest {

    @BeforeAll
    static void init() throws Exception {
        CountDownLatch l = new CountDownLatch(1);
        try {
            Platform.startup(l::countDown);
        } catch (IllegalStateException e) {
            l.countDown();
        }
        Platform.setImplicitExit(false);
        assertTrue(l.await(5, TimeUnit.SECONDS));
    }

    private static <T> T fx(Callable<T> c) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                out.set(c.call());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                l.countDown();
            }
        });
        assertTrue(l.await(30, TimeUnit.SECONDS));
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }

    private static QueryResult result(String col, int rows) {
        List<QueryResultRow> r = new java.util.ArrayList<>();
        for (int i = 0; i < rows; i++) r.add(new QueryResultRow(i + 1, List.of(i)));
        return new QueryResult("SELECT " + col, List.of(new QueryResultColumn(col, "int")), r, 1, "00:00:00");
    }

    @Test
    void eachTabGetsItsOwnResultsBack() throws Exception {
        String[] seen = fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/results.fxml"));
            Parent root = loader.load();
            ResultsController rc = loader.getController();
            Stage st = new Stage();
            st.setScene(new Scene(root, 900, 500));
            st.setX(-4000);
            st.show();
            root.applyCss();
            root.layout();
            // each tab keeps its own grid node, so the grid is looked up again after every switch
            java.util.function.Supplier<TableView<?>> grid = () -> {
                root.applyCss();
                root.layout();
                return (TableView<?>) root.lookup("#resultsTable");
            };

            rc.setQueryResults(List.of(result("tab_a_col", 5)));          // tab A ran a query
            rc.saveTabState("A");
            rc.restoreTabState("B");                                        // tab B never ran
            TableView<?> table = grid.get();
            String b = table.getColumns().size() + "/" + table.getItems().size();
            TableView<?> tableB = table;
            rc.setQueryResults(List.of(result("tab_b_col", 2)));          // tab B runs
            rc.saveTabState("B");
            rc.restoreTabState("A");                                        // back to A
            table = grid.get();
            String a = table.getColumns().get(table.getColumns().size() - 1).getText() + "/" + table.getItems().size();
            rc.saveTabState("A");
            rc.restoreTabState("B");
            table = grid.get();
            boolean sameNodeForB = table == tableB;
            String b2 = table.getColumns().get(table.getColumns().size() - 1).getText() + "/" + table.getItems().size();
            st.close();
            return new String[]{b, a, b2, String.valueOf(sameNodeForB)};
        });
        assertEquals("0/0", seen[0], "a tab that never ran shows an empty panel");
        assertTrue(seen[1].startsWith("tab_a_col") && seen[1].endsWith("/5"), seen[1]);
        assertTrue(seen[2].startsWith("tab_b_col") && seen[2].endsWith("/2"), seen[2]);
        assertEquals("true", seen[3], "going back to a tab shows its own live grid, not a rebuilt one");
    }
}
