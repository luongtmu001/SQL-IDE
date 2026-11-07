package app.ui.results;

import app.models.QueryResultRow;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;

import java.util.function.BiConsumer;

/**
 * D4 (lazy grid): a result row that draws its own row number.
 *
 * <p>With {@code TableView.setFixedCellSize} JavaFX only creates cells for the columns in view (horizontal
 * virtualization, like LLTool's grid), but it then also drops the cell of the "#" column once it is scrolled
 * out of view, so a frozen "#" column made of cells vanished (T11). The number is therefore a label owned
 * by the row: it sits over the (empty) "#" column, follows the horizontal scroll offset so it stays at the
 * left edge, and is never virtualized away.
 */
public final class ResultGridRow extends TableRow<QueryResultRow> {

    private final Label number = new Label();
    private final TableColumn<QueryResultRow, ?> numberColumn;

    /**
     * @param onNumberClick called with (row index, mouse event) when the number is clicked (selects the row)
     */
    public ResultGridRow(TableView<QueryResultRow> table, TableColumn<QueryResultRow, ?> numberColumn,
                         BiConsumer<Integer, MouseEvent> onNumberClick) {
        this.numberColumn = numberColumn;
        number.getStyleClass().add("row-number-cell");
        number.setAlignment(Pos.CENTER_RIGHT);
        number.setMinWidth(0);
        number.setManaged(false);
        number.setViewOrder(-1); // drawn above the cells without reordering the row's children
        number.translateXProperty().bind(FrozenColumnSupport.offset(table));
        number.visibleProperty().bind(numberColumn.visibleProperty().and(emptyProperty().not()));
        number.setOnMouseClicked(e -> {
            if (!isEmpty() && onNumberClick != null) onNumberClick.accept(getIndex(), e);
            e.consume();
        });
    }

    @Override
    protected void updateItem(QueryResultRow item, boolean empty) {
        super.updateItem(item, empty);
        number.setText(empty || item == null ? "" : Integer.toString(item.getRowNumber()));
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        // the row skin may replace its children (setAll of the cells): put the label back. No toFront(): it
        // reordered the children on every horizontal step and made the row lay out again (viewOrder instead)
        if (number.getParent() != this) {
            getChildren().add(number);
        }
        number.resizeRelocate(snappedLeftInset(), 0, numberColumn.getWidth(), getHeight());
    }
}
