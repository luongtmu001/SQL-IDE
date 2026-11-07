package app.ui.results;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * D4: several result sets stacked like SSMS / LLTool ({@code query.js}: createGridSplitter, rebalanceTabPanes).
 * <ul>
 *   <li>The grids fill the panel (no outer scroll). Each starts at max(140, height / count); the last open
 *       grid takes what is left.</li>
 *   <li>A 6px splitter between two open grids changes their heights (minimum 60px).</li>
 *   <li>A grid can be minimized: it shrinks to its 28px header and docks at the bottom; the open grids keep
 *       their order and share the space. Restoring puts it back in its place with its previous height.</li>
 * </ul>
 */
public final class StackedGridsPane extends Region {

    public static final double HEADER_H = 28;
    public static final double SPLITTER_H = 6;
    public static final double MIN_H = 60;
    public static final double LAST_MIN_H = 100;
    private static final double DEFAULT_MIN_H = 140;

    /** One grid: the whole pane (header + body) and the body that is hidden when minimized. */
    public static final class Entry {
        final VBox pane;
        final Node body;
        boolean minimized;
        /** Height chosen by default or by dragging; -1 = not decided yet. */
        double height = -1;

        Entry(VBox pane, Node body) {
            this.pane = pane;
            this.body = body;
        }

        public boolean isMinimized() {
            return minimized;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Region> splitters = new ArrayList<>();

    public StackedGridsPane() {
        getStyleClass().add("stacked-grids");
    }

    public Entry add(VBox pane, Node body) {
        Entry e = new Entry(pane, body);
        entries.add(e);
        getChildren().add(pane);
        requestLayout();
        return e;
    }

    public List<Entry> entries() {
        return entries;
    }

    public void setMinimized(Entry e, boolean minimized) {
        if (e.minimized == minimized) return;
        e.minimized = minimized;
        e.body.setVisible(!minimized);
        e.body.setManaged(!minimized);
        e.pane.getStyleClass().remove("minimized");
        if (minimized) e.pane.getStyleClass().add("minimized");
        requestLayout();
    }

    public void toggleMinimized(Entry e) {
        setMinimized(e, !e.minimized);
    }

    private Region splitter(int i) {
        while (splitters.size() <= i) {
            Region s = new Region();
            s.getStyleClass().add("grid-splitter");
            s.setCursor(Cursor.V_RESIZE);
            s.setManaged(false);
            installDrag(s);
            splitters.add(s);
            getChildren().add(s);
        }
        return splitters.get(i);
    }

    private static final String PREV = "prev", NEXT = "next", NEXT_IS_LAST = "nextIsLast";

    private void installDrag(Region s) {
        double[] start = new double[3]; // y, prev height, next height
        s.setOnMousePressed(e -> {
            Entry prev = (Entry) s.getProperties().get(PREV);
            Entry next = (Entry) s.getProperties().get(NEXT);
            if (prev == null) return;
            start[0] = e.getScreenY();
            start[1] = prev.pane.getHeight();
            start[2] = next != null ? next.pane.getHeight() : 0;
            s.getStyleClass().add("dragging");
            e.consume();
        });
        s.setOnMouseDragged(e -> {
            Entry prev = (Entry) s.getProperties().get(PREV);
            Entry next = (Entry) s.getProperties().get(NEXT);
            if (prev == null) return;
            double delta = e.getScreenY() - start[0];
            prev.height = Math.max(MIN_H, start[1] + delta);
            if (next != null && !Boolean.TRUE.equals(s.getProperties().get(NEXT_IS_LAST))) {
                next.height = Math.max(MIN_H, start[2] - delta);
            }
            requestLayout();
            e.consume();
        });
        s.setOnMouseReleased(e -> s.getStyleClass().remove("dragging"));
    }

    @Override
    protected void layoutChildren() {
        double x = snappedLeftInset();
        double w = getWidth() - x - snappedRightInset();
        double top = snappedTopInset();
        double h = getHeight() - top - snappedBottomInset();

        List<Entry> open = new ArrayList<>();
        List<Entry> minimized = new ArrayList<>();
        for (Entry e : entries) (e.minimized ? minimized : open).add(e);

        double avail = h - minimized.size() * HEADER_H - Math.max(0, open.size() - 1) * SPLITTER_H;
        double defaultH = Math.max(DEFAULT_MIN_H, Math.floor((h - Math.max(0, entries.size() - 1) * SPLITTER_H) / Math.max(1, entries.size())));
        for (Entry e : open) if (e.height < 0) e.height = defaultH;

        // the open grids above the last one may not leave the last less than its minimum: shrink them evenly
        double fixed = 0;
        for (int i = 0; i < open.size() - 1; i++) fixed += open.get(i).height;
        double room = avail - LAST_MIN_H;
        double scale = (open.size() > 1 && fixed > room && fixed > 0) ? Math.max(0, room) / fixed : 1;

        double y = top;
        int splitterNo = 0;
        for (int i = 0; i < open.size(); i++) {
            Entry e = open.get(i);
            boolean last = i == open.size() - 1;
            double ph = last ? Math.max(open.size() == 1 ? MIN_H : LAST_MIN_H, top + avail + Math.max(0, open.size() - 1) * SPLITTER_H - y)
                    : Math.max(MIN_H, e.height * scale);
            e.pane.resizeRelocate(x, y, w, ph);
            y += ph;
            if (!last) {
                Region s = splitter(splitterNo++);
                s.getProperties().put(PREV, e);
                s.getProperties().put(NEXT, open.get(i + 1));
                s.getProperties().put(NEXT_IS_LAST, i + 1 == open.size() - 1);
                s.setVisible(true);
                s.resizeRelocate(x, y, w, SPLITTER_H);
                s.toFront();
                y += SPLITTER_H;
            }
        }
        for (int i = splitterNo; i < splitters.size(); i++) {
            splitters.get(i).setVisible(false);
            splitters.get(i).getProperties().clear();
        }
        // minimized grids: header only, docked at the bottom in their original order
        double my = top + h - minimized.size() * HEADER_H;
        for (Entry e : minimized) {
            e.pane.resizeRelocate(x, my, w, HEADER_H);
            my += HEADER_H;
        }
    }

    @Override
    protected double computePrefHeight(double width) {
        return 200;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 0;
    }
}
