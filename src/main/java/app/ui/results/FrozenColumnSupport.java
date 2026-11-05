package app.ui.results;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.TableColumnHeader;

/**
 * Keeps one leading column (the row number "#") in place while the grid scrolls horizontally
 * (user request 2026-11-05). TableView has no frozen columns: the column's header and cells are
 * shifted by the horizontal scroll offset and drawn above the other cells.
 */
public final class FrozenColumnSupport {

    private static final String OFFSET_KEY = FrozenColumnSupport.class.getName() + ".offset";
    private static final String HOOKED_KEY = FrozenColumnSupport.class.getName() + ".hooked";

    private FrozenColumnSupport() {
    }

    /** Horizontal scroll offset of the table, in pixels. */
    public static DoubleProperty offset(TableView<?> table) {
        return (DoubleProperty) table.getProperties().computeIfAbsent(OFFSET_KEY, k -> new SimpleDoubleProperty(0));
    }

    /** Call from the frozen column's cell: follows the scroll and stays on top. */
    public static void freezeCell(TableCell<?, ?> cell, TableView<?> table) {
        if (!cell.translateXProperty().isBound()) cell.translateXProperty().bind(offset(table));
        cell.toFront();
        // stacked / tabbed grids get their skin late: (re)hook from the first laid-out cells
        if (!Boolean.TRUE.equals(table.getProperties().get(READY_KEY)) && cell.getTableColumn() != null) {
            TableColumn<?, ?> col = cell.getTableColumn();
            Platform.runLater(() -> hook(table, col));
        }
    }

    private static final String READY_KEY = FrozenColumnSupport.class.getName() + ".ready";

    public static void install(TableView<?> table, TableColumn<?, ?> column) {
        table.getProperties().remove(READY_KEY);
        Runnable hook = () -> hook(table, column);
        table.skinProperty().addListener((o, a, b) -> Platform.runLater(hook));
        table.getColumns().addListener((ListChangeListener<TableColumn<?, ?>>) c -> Platform.runLater(hook));
        table.widthProperty().addListener((o, a, b) -> Platform.runLater(hook));
        Platform.runLater(hook);
    }

    private static void hook(TableView<?> table, TableColumn<?, ?> column) {
        DoubleProperty off = offset(table);
        boolean barFound = false, headerFound = false;
        for (Node n : table.lookupAll(".scroll-bar")) {
            // only the grid's own horizontal bar (not one of a parent ScrollPane)
            if (n instanceof ScrollBar sb && sb.getOrientation() == Orientation.HORIZONTAL && isInside(sb, table)) {
                barFound = true;
                if (!sb.getProperties().containsKey(HOOKED_KEY) || !off.isBound()) {
                    sb.getProperties().put(HOOKED_KEY, Boolean.TRUE);
                    off.unbind();
                    off.bind(sb.valueProperty());
                }
            }
        }
        for (Node n : table.lookupAll(".column-header")) {
            if (n instanceof TableColumnHeader h && h.getTableColumn() == column) {
                headerFound = true;
                if (!h.translateXProperty().isBound()) h.translateXProperty().bind(off);
                if (!h.getStyleClass().contains("frozen-header")) h.getStyleClass().add("frozen-header");
                h.toFront();
            }
        }
        if (barFound && headerFound) table.getProperties().put(READY_KEY, Boolean.TRUE);
    }

    private static boolean isInside(Node n, TableView<?> table) {
        for (Node p = n.getParent(); p != null; p = p.getParent()) {
            if (p == table) return true;
            if (p instanceof TableView<?>) return false;
        }
        return false;
    }
}
