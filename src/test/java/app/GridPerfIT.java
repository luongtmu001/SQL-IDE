package app;

import app.controllers.ResultsController;
import app.framework.query.CellValues;
import app.models.QueryResult;
import app.models.QueryResultColumn;
import app.models.QueryResultRow;
import app.services.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D4.4: grid speed with the real ResultsController + theme, in a window of 1200 x 700 placed off-screen.
 * Not in the normal test run (run-db-tests.cmd runs it). Writes target/grid-perf.txt.
 * Times are the FX-thread cost of CSS + layout for each step (what a scroll costs before painting).
 */
class GridPerfIT {

    private static final Path REPORT = Path.of("target", "grid-perf.txt");

    @BeforeAll
    static void init() throws Exception {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, "=== grid perf " + java.time.LocalDateTime.now() + System.lineSeparator());
        CountDownLatch l = new CountDownLatch(1);
        try {
            Platform.startup(l::countDown);
        } catch (IllegalStateException e) {
            l.countDown();
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {
        }
        assertTrue(l.await(5, TimeUnit.SECONDS));
    }

    static void report(String s) {
        try {
            Files.writeString(REPORT, s + System.lineSeparator(), StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static QueryResult data(int rows, int cols) {
        List<QueryResultColumn> c = new ArrayList<>();
        for (int i = 0; i < cols; i++) c.add(new QueryResultColumn("Column_" + i, i % 3 == 0 ? "int" : "nvarchar"));
        List<QueryResultRow> r = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            List<Object> v = new ArrayList<>(cols);
            for (int j = 0; j < cols; j++) {
                v.add(switch (j % 5) {
                    case 0 -> i * 7 + j;
                    case 1 -> "Text " + i + "-" + j;
                    case 2 -> new CellValues.PlainDecimal(new BigDecimal(i).movePointLeft(2));
                    case 3 -> (i + j) % 4 == 0 ? null : "2024-01-02 10:11:12";
                    default -> (i % 2 == 0);
                });
            }
            r.add(new QueryResultRow(i + 1, v));
        }
        return new QueryResult("SELECT * FROM perf", c, r, 10, "00:00:00");
    }

    /** Runs on the FX thread and waits. */
    private static <T> T fx(java.util.concurrent.Callable<T> work) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                out.set(work.call());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                l.countDown();
            }
        });
        assertTrue(l.await(120, TimeUnit.SECONDS), "FX step timed out");
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }

    private record Ui(Stage stage, ResultsController controller, Parent root) {
    }

    /** A fresh window for a second, broken-down measurement (closed right after). */
    private static Ui ui2() throws Exception {
        Ui u = open();
        Platform.runLater(() -> Platform.runLater(() -> u.stage().close()));
        return u;
    }

    private static Ui open() throws Exception {
        return fx(() -> {
            FXMLLoader loader = new FXMLLoader(GridPerfIT.class.getResource("/fxml/results.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1200, 700);
            ThemeService.getInstance().setTheme(ThemeService.AppTheme.PRIMER_LIGHT, scene);
            Stage st = new Stage();
            st.setScene(scene);
            st.setX(-4000);
            st.setY(0);
            st.show();
            return new Ui(st, loader.getController(), root);
        });
    }

    @SuppressWarnings("unchecked")
    private static TableView<QueryResultRow> table(Ui ui) {
        return (TableView<QueryResultRow>) ui.root().lookup("#resultsTable");
    }

    private static void pulse(Node n) {
        n.applyCss();
        if (n instanceof Parent p) p.layout();
    }

    private static int cellsInFirstRow(TableView<?> t) {
        for (Node n : t.lookupAll(".table-row-cell")) {
            if (n instanceof TableRow<?> r && !r.isEmpty()) {
                int c = 0;
                for (Node k : r.getChildrenUnmodifiable()) if (k instanceof TableCell<?, ?> && k.isVisible()) c++;
                return c;
            }
        }
        return -1;
    }

    /** The grid's own horizontal bar (inside the VirtualFlow; not the empty-table placeholder's bar). */
    private static ScrollBar hbar(TableView<?> t) {
        Node flow = t.lookup(".virtual-flow");
        if (!(flow instanceof Parent fp)) return null;
        for (Node n : fp.getChildrenUnmodifiable()) {
            if (n instanceof ScrollBar sb && sb.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) return sb;
        }
        return null;
    }

    /** Times model / CSS / layout separately (ms). */
    private static String showBreakdown(Ui ui, QueryResult res) throws Exception {
        return fx(() -> {
            long a = System.nanoTime();
            ui.controller().setQueryResult(res);
            long b = System.nanoTime();
            ui.root().applyCss();
            long c = System.nanoTime();
            ui.root().layout();
            long d = System.nanoTime();
            return String.format("[build %d ms, css %d ms, layout %d ms]", (b - a) / 1_000_000, (c - b) / 1_000_000, (d - c) / 1_000_000);
        });
    }

    /** Plain JavaFX TableView, default cells and text headers, same size: what JavaFX itself costs. */
    private static String baseline(int rows, int cols) throws Exception {
        QueryResult res = data(rows, cols);
        return fx(() -> {
            TableView<QueryResultRow> t = new TableView<>();
            t.setFixedCellSize(24);
            List<javafx.scene.control.TableColumn<QueryResultRow, ?>> cs = new ArrayList<>();
            for (int i = 0; i < cols; i++) {
                final int ci = i;
                javafx.scene.control.TableColumn<QueryResultRow, Object> c = new javafx.scene.control.TableColumn<>(res.getColumns().get(i).getName());
                c.setPrefWidth(120);
                c.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cd.getValue().getValue(ci)));
                cs.add(c);
            }
            Scene scene = new Scene(t, 1200, 700);
            ThemeService.getInstance().setTheme(ThemeService.AppTheme.PRIMER_LIGHT, scene);
            Stage st = new Stage();
            st.setScene(scene);
            st.setX(-4000);
            st.show();
            long a = System.nanoTime();
            t.getColumns().setAll(cs);
            t.getItems().setAll(res.getRows());
            t.applyCss();
            t.layout();
            long show = System.nanoTime() - a;
            String scroll = "";
            if (rows > 0) {
                VirtualFlow<?> flow = (VirtualFlow<?>) t.lookup(".virtual-flow");
                long tot = 0;
                for (int i = 0; i < 100; i++) {
                    long s0 = System.nanoTime();
                    flow.scrollPixels(240);
                    t.applyCss();
                    t.layout();
                    tot += System.nanoTime() - s0;
                }
                ScrollBar hb = hbar(t);
                long htot = 0;
                int steps = 0;
                if (hb != null) {
                    for (double x = 0; x <= hb.getMax() && steps < 60; x += 300, steps++) {
                        long s0 = System.nanoTime();
                        hb.setValue(x);
                        t.applyCss();
                        t.layout();
                        htot += System.nanoTime() - s0;
                    }
                }
                scroll = String.format(", vertical step %.2f ms, horizontal step %.2f ms", tot / 100.0 / 1e6,
                        steps == 0 ? 0 : htot / (double) steps / 1e6);
            }
            st.close();
            return String.format("BASELINE plain TableView %,d rows x %d cols: show %d ms%s", rows, cols, show / 1_000_000, scroll);
        });
    }

    @Test
    void baselines() throws Exception {
        report(baseline(0, 279));
        report(baseline(5_000, 279));
        report(baseline(32_960, 30));
    }

    @Test
    void tallTableScrollsVertically() throws Exception {
        Ui ui = open();
        QueryResult res = data(32_960, 30);
        long t0 = System.nanoTime();
        fx(() -> {
            ui.controller().setQueryResult(res);
            pulse(ui.root());
            return null;
        });
        long showMs = (System.nanoTime() - t0) / 1_000_000;
        TableView<QueryResultRow> t = fx(() -> table(ui));
        double[] stepMs = fx(() -> {
            VirtualFlow<?> flow = (VirtualFlow<?>) t.lookup(".virtual-flow");
            double[] out = new double[2];
            long total = 0, worst = 0;
            for (int i = 0; i < 150; i++) {
                long s = System.nanoTime();
                flow.scrollPixels(240);   // ~10 rows, like a mouse-wheel notch or two
                pulse(t);
                long d = System.nanoTime() - s;
                total += d;
                worst = Math.max(worst, d);
            }
            out[0] = total / 150.0 / 1e6;
            out[1] = worst / 1e6;
            return out;
        });
        int cells = fx(() -> cellsInFirstRow(t));
        report(String.format("32,960 rows x 30 cols: show %d ms; vertical scroll step avg %.2f ms, worst %.2f ms; cells in a row %d; fixedCellSize %.0f",
                showMs, stepMs[0], stepMs[1], cells, fx(t::getFixedCellSize)));
        fx(() -> {
            ui.stage().close();
            return null;
        });
        report("  32,960 x 30 again " + showBreakdown(ui2(), res));
    }

    @Test
    void wideEmptyTableCanScrollSideways() throws Exception {
        Ui ui = open();
        QueryResult res = data(0, 279);
        long t0 = System.nanoTime();
        fx(() -> {
            ui.controller().setQueryResult(res);
            pulse(ui.root());
            return null;
        });
        long showMs = (System.nanoTime() - t0) / 1_000_000;
        TableView<QueryResultRow> t = fx(() -> table(ui));
        double[] bar = fx(() -> {
            pulse(t);
            Node ph = t.lookup(".grid-empty-placeholder");
            ScrollBar sb = null;
            if (ph instanceof Parent p) {
                for (Node n : p.getChildrenUnmodifiable()) if (n instanceof ScrollBar s) sb = s;
            }
            if (sb == null) return new double[]{-1, -1};
            sb.setValue(sb.getMax());
            Node header = t.lookup(".nested-column-header");
            return new double[]{sb.getMax(), header != null ? header.getTranslateX() : 0};
        });
        report(String.format("0 rows x 279 cols: show %d ms; placeholder bar max %.0f px, header moved to %.0f px", showMs, bar[0], bar[1]));
        fx(() -> {
            ui.stage().close();
            return null;
        });
        assertTrue(bar[0] > 0, "an empty wide table must have its own horizontal bar");
        assertEquals(-bar[0], bar[1], 1.0, "the header row must follow the bar");
        report("  0 x 279 again " + showBreakdown(ui2(), res));
    }

    @Test
    void wideTableScrollsSidewaysAndKeepsRowNumbers() throws Exception {
        Ui ui = open();
        QueryResult res = data(5_000, 279);
        long t0 = System.nanoTime();
        fx(() -> {
            ui.controller().setQueryResult(res);
            pulse(ui.root());
            return null;
        });
        long showMs = (System.nanoTime() - t0) / 1_000_000;
        TableView<QueryResultRow> t = fx(() -> table(ui));
        Object[] r = fx(() -> {
            ScrollBar sb = hbar(t);
            if (sb == null) return new Object[]{-1.0, -1.0, -1, "no bar"};
            long total = 0, worst = 0;
            int steps = 0;
            for (double x = 0; x <= sb.getMax() && steps < 120; x += 300, steps++) {
                long s = System.nanoTime();
                sb.setValue(x);
                pulse(t);
                long d = System.nanoTime() - s;
                total += d;
                worst = Math.max(worst, d);
            }
            sb.setValue(sb.getMax());
            pulse(t);
            String num = "none";
            for (Node n : t.lookupAll(".table-row-cell")) {
                if (n instanceof TableRow<?> row && !row.isEmpty()) {
                    for (Node k : row.getChildrenUnmodifiable()) {
                        if (k instanceof Label lb && lb.getStyleClass().contains("row-number-cell")) {
                            num = "'" + lb.getText() + "' visible=" + lb.isVisible() + " translateX=" + Math.round(lb.getTranslateX())
                                    + " bar=" + Math.round(sb.getValue()) + " aligned=" + (Math.abs(lb.getTranslateX() - sb.getValue()) < 2);
                        }
                    }
                    break;
                }
            }
            return new Object[]{total / (double) Math.max(1, steps) / 1e6, worst / 1e6, cellsInFirstRow(t), num};
        });
        report(String.format("5,000 rows x 279 cols: show %d ms; horizontal scroll step avg %.2f ms, worst %.2f ms; cells in a row %d (of 280); row number at far right: %s",
                showMs, (double) r[0], (double) r[1], (int) r[2], r[3]));
        fx(() -> {
            ui.stage().close();
            return null;
        });
        report("  5,000 x 279 again " + showBreakdown(ui2(), res));
        assertTrue((int) r[2] < 100, "only the columns in view should have cells, got " + r[2]);
        assertTrue(String.valueOf(r[3]).contains("visible=true"), "row number must stay visible: " + r[3]);
        assertTrue(String.valueOf(r[3]).contains("aligned=true"), "row number must follow the horizontal scroll: " + r[3]);
    }
    @Test
    void streamedRowsAreAppendedQuickly() throws Exception {
        Ui ui = open();
        QueryResult all = data(32_960, 30);
        fx(() -> {
            ui.controller().resetStreaming();
            ui.controller().streamResultStarted(0, all.getQuery(), all.getColumns());
            pulse(ui.root());
            return null;
        });
        long total = 0, worst = 0;
        int batches = 0;
        List<QueryResultRow> rows = all.getRows();
        for (int from = 0; from < rows.size(); ) {
            int size = from == 0 ? 200 : 2000;
            List<QueryResultRow> batch = List.copyOf(rows.subList(from, Math.min(rows.size(), from + size)));
            from += batch.size();
            long d = fx(() -> {
                long s = System.nanoTime();
                ui.controller().streamRows(0, batch);
                pulse(ui.root());
                return System.nanoTime() - s;
            });
            total += d;
            worst = Math.max(worst, d);
            batches++;
        }
        int shown = fx(() -> table(ui).getItems().size());
        fx(() -> {
            ui.controller().finishStreaming(List.of(all));
            pulse(ui.root());
            return null;
        });
        int after = fx(() -> table(ui).getItems().size());
        report(String.format("streaming 32,960 rows x 30 cols in %d batches: append avg %.2f ms, worst %.2f ms; rows in grid %d, after finish %d",
                batches, total / (double) batches / 1e6, worst / 1e6, shown, after));
        fx(() -> {
            ui.stage().close();
            return null;
        });
        assertEquals(32_960, shown);
        assertEquals(32_960, after);
    }
}
