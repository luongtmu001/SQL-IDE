package app.ui.results;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * D4: body of a result grid with 0 rows. JavaFX shows no horizontal scroll bar for an empty TableView, so a
 * wide empty table (e.g. 279 columns) could not be scrolled to see its columns. This placeholder says
 * "0 dòng" and carries its own horizontal bar that moves the column headers (LLTool shows the header row
 * of an empty result and lets it scroll).
 */
public final class EmptyGridPlaceholder extends VBox {

    private final TableView<?> table;
    private final ScrollBar bar = new ScrollBar();

    public EmptyGridPlaceholder(TableView<?> table, String text) {
        this.table = table;
        getStyleClass().add("grid-empty-placeholder");
        setAlignment(Pos.TOP_CENTER);
        setFillWidth(true);

        Label label = new Label(text);
        label.getStyleClass().add("grid-no-rows");
        Region gap = new Region();
        VBox.setVgrow(gap, Priority.ALWAYS);

        bar.setOrientation(Orientation.HORIZONTAL);
        bar.setMin(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.valueProperty().addListener((o, a, b) -> moveHeaders(b.doubleValue()));
        addEventFilter(ScrollEvent.SCROLL, e -> {
            double d = e.getDeltaX() != 0 ? -e.getDeltaX() : (e.isShiftDown() ? -e.getDeltaY() : 0);
            if (d != 0 && bar.isVisible()) {
                bar.setValue(Math.max(0, Math.min(bar.getMax(), bar.getValue() + d)));
                e.consume();
            }
        });
        getChildren().addAll(label, gap, bar);
    }

    private double columnsWidth() {
        double w = 0;
        for (TableColumn<?, ?> c : table.getVisibleLeafColumns()) w += c.getWidth();
        return w;
    }

    private void moveHeaders(double x) {
        Node root = table.lookup(".nested-column-header");
        if (root != null) root.setTranslateX(-x);
    }

    @Override
    protected void layoutChildren() {
        double viewport = getWidth();
        double overflow = Math.max(0, columnsWidth() - viewport);
        bar.setMax(overflow);
        bar.setVisibleAmount(overflow <= 0 ? 0 : overflow * viewport / Math.max(1, columnsWidth()));
        bar.setUnitIncrement(40);
        bar.setBlockIncrement(Math.max(40, viewport * 0.8));
        boolean show = overflow > 0;
        bar.setVisible(show);
        bar.setManaged(show);
        if (bar.getValue() > overflow) bar.setValue(overflow);
        super.layoutChildren();
    }
}
