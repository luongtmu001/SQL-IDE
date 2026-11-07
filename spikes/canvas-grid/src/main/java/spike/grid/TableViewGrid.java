package spike.grid;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * The reference: a TableView built the way SQL-IDE's ResultsController builds its grid today
 * (fixed 24px rows, "#" column, header = name + type + filter button + tooltips, pseudo-class cells).
 */
public final class TableViewGrid {

    private static final PseudoClass NULL = PseudoClass.getPseudoClass("null-value");
    private static final PseudoClass NUMBER = PseudoClass.getPseudoClass("number");

    private TableViewGrid() {
    }

    public static TableView<Object[]> build(SpikeData data) {
        TableView<Object[]> t = new TableView<>();
        t.getStyleClass().add("results-table");
        t.setFixedCellSize(24);
        t.getSelectionModel().setCellSelectionEnabled(true);
        t.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        t.setPlaceholder(new Label("0 dòng"));

        List<TableColumn<Object[], ?>> cols = new ArrayList<>();
        TableColumn<Object[], Integer> num = new TableColumn<>("#");
        num.setPrefWidth(50);
        num.setSortable(false);
        num.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : Integer.toString(getIndex() + 1));
            }
        });
        num.setCellValueFactory(cd -> null);
        cols.add(num);

        for (int i = 0; i < data.columns.size(); i++) {
            final int ci = i;
            SpikeData.Column meta = data.columns.get(i);
            TableColumn<Object[], Object> c = new TableColumn<>(meta.name());
            double minW = Math.max(38, Math.min(160, meta.name().length() * 8 + 36));
            c.setMinWidth(minW);
            c.setPrefWidth(Math.max(minW, 120));
            c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()[ci]));

            Label name = new Label(meta.name());
            name.getStyleClass().add("col-header-name");
            name.setMinWidth(0);
            name.setTooltip(new Tooltip(meta.name() + " (" + meta.type() + ")"));
            Label type = new Label(meta.type());
            type.getStyleClass().add("col-header-type");
            type.setMinWidth(0);
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            Button filter = new Button("▾");
            filter.getStyleClass().add("col-header-filter-btn");
            filter.setFocusTraversable(false);
            filter.setTooltip(new Tooltip("Lọc [" + meta.name() + "]"));
            HBox header = new HBox(4, name, type, gap, filter);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setMinWidth(0);
            header.prefWidthProperty().bind(c.widthProperty().subtract(12));
            header.maxWidthProperty().bind(c.widthProperty().subtract(12));
            c.setGraphic(header);

            c.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    boolean isNull = !empty && item == null;
                    boolean isNum = item instanceof Number;
                    setText(empty ? null : SpikeData.text(item));
                    setAlignment(isNum ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                    pseudoClassStateChanged(NULL, isNull);
                    pseudoClassStateChanged(NUMBER, isNum);
                }
            });
            cols.add(c);
        }
        t.getColumns().setAll(cols);
        t.getItems().setAll(data.rows);
        return t;
    }
}
