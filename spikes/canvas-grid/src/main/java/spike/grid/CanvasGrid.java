package spike.grid;

import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * S1: the smallest grid that paints itself. One Canvas the size of the view; every repaint draws only the
 * rows and columns in view (header row, frozen "#" column, cells). No node per cell, so the cost of a
 * scroll step depends on the view size, not on the number of rows or columns.
 * S2 adds selection, column resize and auto-fit.
 */
public final class CanvasGrid extends Region {

    public static final double ROW_H = 24;
    public static final double HEADER_H = 26;
    public static final double NUM_W = 50;

    private final Canvas canvas = new Canvas();
    private final ScrollBar vbar = new ScrollBar();
    private final ScrollBar hbar = new ScrollBar();

    private List<SpikeData.Column> columns = List.of();
    private List<Object[]> rows = List.of();
    private double[] colX = new double[0];   // left edge of each data column, content coordinates
    private double[] colW = new double[0];
    private double contentW;

    private final Font cellFont = Font.font("Segoe UI", 11.5);
    private final Font nullFont = Font.font("Segoe UI", FontWeight.NORMAL, FontPosture.ITALIC, 11.5);
    private final Font headerFont = Font.font("Segoe UI", FontWeight.BOLD, 11.5);
    private final Font typeFont = Font.font("Segoe UI", FontWeight.NORMAL, FontPosture.ITALIC, 9.5);

    private final Color bg = Color.web("#ffffff");
    private final Color bgAlt = Color.web("#f6f8fa");
    private final Color headerBg = Color.web("#f6f8fa");
    private final Color grid = Color.web("#d8dee4");
    private final Color text = Color.web("#1f2328");
    private final Color muted = Color.web("#59636e");
    private final Color number = Color.web("#2563eb");

    /** Paint requests are folded into one paint per pulse. */
    private boolean paintQueued;
    private long paints;
    private long paintNanos;

    public CanvasGrid() {
        getStyleClass().add("canvas-grid");
        vbar.setOrientation(Orientation.VERTICAL);
        hbar.setOrientation(Orientation.HORIZONTAL);
        getChildren().addAll(canvas, vbar, hbar);
        vbar.valueProperty().addListener((o, a, b) -> requestPaint());
        hbar.valueProperty().addListener((o, a, b) -> requestPaint());
        addEventHandler(ScrollEvent.SCROLL, e -> {
            boolean sideways = e.isShiftDown() || Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY());
            double d = sideways ? (e.getDeltaX() != 0 ? e.getDeltaX() : e.getDeltaY()) : e.getDeltaY();
            ScrollBar bar = sideways ? hbar : vbar;
            bar.setValue(clamp(bar.getValue() - d, 0, bar.getMax()));
            e.consume();
        });
    }

    public void setData(List<SpikeData.Column> columns, List<Object[]> rows) {
        this.columns = columns;
        this.rows = rows;
        colX = new double[columns.size()];
        colW = new double[columns.size()];
        double x = 0;
        for (int i = 0; i < columns.size(); i++) {
            String n = columns.get(i).name();
            double minW = Math.max(38, Math.min(160, n.length() * 8 + 36));
            colX[i] = x;
            colW[i] = Math.max(minW, 120);
            x += colW[i];
        }
        contentW = x;
        vbar.setValue(0);
        hbar.setValue(0);
        requestLayout();
        requestPaint();
    }

    /** Rows were added at the end (streaming): only the scroll range and the view change. */
    public void rowsAppended() {
        requestLayout();
        requestPaint();
    }

    public ScrollBar verticalBar() {
        return vbar;
    }

    public ScrollBar horizontalBar() {
        return hbar;
    }

    public long paintCount() {
        return paints;
    }

    public double averagePaintMs() {
        return paints == 0 ? 0 : paintNanos / (double) paints / 1e6;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        double sb = 14;
        double viewW = w - sb, viewH = h - sb;
        canvas.setWidth(Math.max(0, viewW));
        canvas.setHeight(Math.max(0, viewH));
        canvas.relocate(0, 0);
        vbar.resizeRelocate(viewW, 0, sb, viewH);
        hbar.resizeRelocate(0, viewH, viewW, sb);

        double bodyH = Math.max(0, viewH - HEADER_H);
        double totalH = rows.size() * ROW_H;
        vbar.setMax(Math.max(0, totalH - bodyH));
        vbar.setVisibleAmount(totalH <= 0 ? 0 : vbar.getMax() * bodyH / totalH);
        vbar.setUnitIncrement(ROW_H * 3);
        vbar.setBlockIncrement(Math.max(ROW_H, bodyH - ROW_H));

        double bodyW = Math.max(0, viewW - NUM_W);
        hbar.setMax(Math.max(0, contentW - bodyW));
        hbar.setVisibleAmount(contentW <= 0 ? 0 : hbar.getMax() * bodyW / contentW);
        hbar.setUnitIncrement(40);
        hbar.setBlockIncrement(Math.max(40, bodyW * 0.8));
        vbar.setVisible(vbar.getMax() > 0);   // no bar when everything fits (e.g. 0 rows)
        hbar.setVisible(hbar.getMax() > 0);
        if (vbar.getValue() > vbar.getMax()) vbar.setValue(vbar.getMax());
        if (hbar.getValue() > hbar.getMax()) hbar.setValue(hbar.getMax());
        paint();
    }

    private void requestPaint() {
        if (paintQueued) return;
        paintQueued = true;
        javafx.application.Platform.runLater(() -> {
            paintQueued = false;
            paint();
        });
    }

    /** Draws the view: header, frozen row numbers, visible cells. */
    public void paint() {
        long t0 = System.nanoTime();
        double w = canvas.getWidth(), h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(bg);
        g.fillRect(0, 0, w, h);
        if (w <= 0 || h <= 0) return;

        double sx = hbar.getValue(), sy = vbar.getValue();
        double bodyH = h - HEADER_H;
        int firstRow = (int) Math.floor(sy / ROW_H);
        int lastRow = Math.min(rows.size() - 1, (int) Math.floor((sy + bodyH) / ROW_H));
        int firstCol = firstVisibleColumn(sx);
        double right = sx + (w - NUM_W);

        g.setTextBaseline(VPos.CENTER);

        // body cells (clipped to the area right of "#" and below the header)
        g.save();
        g.beginPath();
        g.rect(NUM_W, HEADER_H, w - NUM_W, bodyH);
        g.clip();
        for (int r = Math.max(0, firstRow); r <= lastRow; r++) {
            double y = HEADER_H + r * ROW_H - sy;
            g.setFill(r % 2 == 1 ? bgAlt : bg);
            g.fillRect(NUM_W, y, w - NUM_W, ROW_H);
            Object[] row = rows.get(r);
            for (int c = firstCol; c < columns.size() && colX[c] < right; c++) {
                double x = NUM_W + colX[c] - sx;
                drawCell(g, row[c], x, y, colW[c]);
            }
            g.setStroke(grid);
            g.setLineWidth(1);
            g.strokeLine(NUM_W, y + ROW_H - 0.5, w, y + ROW_H - 0.5);
        }
        // vertical grid lines
        g.setStroke(grid);
        for (int c = firstCol; c < columns.size() && colX[c] < right; c++) {
            double x = NUM_W + colX[c] + colW[c] - sx - 0.5;
            g.strokeLine(x, HEADER_H, x, HEADER_H + Math.min(bodyH, rows.size() * ROW_H - sy));
        }
        g.restore();

        // frozen "#" column
        g.setFill(headerBg);
        g.fillRect(0, HEADER_H, NUM_W, bodyH);
        g.setFont(cellFont);
        g.setFill(muted);
        g.setTextAlign(TextAlignment.RIGHT);
        for (int r = Math.max(0, firstRow); r <= lastRow; r++) {
            double y = HEADER_H + r * ROW_H - sy;
            g.fillText(Integer.toString(r + 1), NUM_W - 6, y + ROW_H / 2);
        }
        g.setStroke(grid);
        g.strokeLine(NUM_W - 0.5, HEADER_H, NUM_W - 0.5, h);

        // header row
        g.setFill(headerBg);
        g.fillRect(0, 0, w, HEADER_H);
        g.save();
        g.beginPath();
        g.rect(NUM_W, 0, w - NUM_W, HEADER_H);
        g.clip();
        for (int c = firstCol; c < columns.size() && colX[c] < right; c++) {
            double x = NUM_W + colX[c] - sx;
            drawHeader(g, columns.get(c), x, colW[c]);
        }
        g.restore();
        g.setFont(headerFont);
        g.setFill(muted);
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("#", NUM_W / 2, HEADER_H / 2);
        g.setStroke(grid);
        g.strokeLine(0, HEADER_H - 0.5, w, HEADER_H - 0.5);
        if (rows.isEmpty()) {
            g.setFont(nullFont);
            g.setFill(muted);
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("0 dòng", w / 2, HEADER_H + 24);
        }
        paints++;
        paintNanos += System.nanoTime() - t0;
    }

    private void drawCell(GraphicsContext g, Object v, double x, double y, double cw) {
        g.save();
        g.beginPath();
        g.rect(x, y, cw - 1, ROW_H);
        g.clip();
        if (v == null) {
            g.setFont(nullFont);
            g.setFill(muted);
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText("NULL", x + 6, y + ROW_H / 2);
        } else if (v instanceof Number) {
            g.setFont(cellFont);
            g.setFill(number);
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText(SpikeData.text(v), x + cw - 6, y + ROW_H / 2);
        } else {
            g.setFont(cellFont);
            g.setFill(text);
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(SpikeData.text(v), x + 6, y + ROW_H / 2);
        }
        g.restore();
    }

    private void drawHeader(GraphicsContext g, SpikeData.Column col, double x, double cw) {
        g.save();
        g.beginPath();
        g.rect(x, 0, cw - 1, HEADER_H);
        g.clip();
        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(headerFont);
        g.setFill(text);
        g.fillText(col.name(), x + 6, HEADER_H / 2);
        g.setFont(typeFont);
        g.setFill(muted);
        g.fillText(col.type(), x + 10 + col.name().length() * 7, HEADER_H / 2 + 1);
        g.fillText("▾", x + cw - 14, HEADER_H / 2);   // filter mark (S2: real button area)
        g.restore();
        g.setStroke(grid);
        g.strokeLine(x + cw - 0.5, 4, x + cw - 0.5, HEADER_H - 4);
    }

    /** First column whose right edge is past the scroll offset (binary search). */
    private int firstVisibleColumn(double sx) {
        int lo = 0, hi = colX.length - 1, ans = colX.length;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (colX[mid] + colW[mid] > sx) {
                ans = mid;
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return ans;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
