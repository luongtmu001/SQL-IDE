package app.ui.designer;

import app.services.SettingsService;
import com.lltool.core.domain.tabledesigner.model.IndexColumn;
import com.lltool.core.domain.tabledesigner.model.IndexDesign;
import com.lltool.core.domain.tabledesigner.valueobject.IndexType;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Grid view for managing Table Indexes in Table Designer (SCR-TD-001 / TD3).
 */
public class IndexGrid extends VBox {

    private final String engine;
    private final Runnable onChangeCallback;
    private final ObservableList<IndexViewModel> items = FXCollections.observableArrayList();
    private final TableView<IndexViewModel> tableView = new TableView<>();

    public IndexGrid(String engine, Runnable onChangeCallback) {
        this.engine = engine != null ? engine.toLowerCase() : "sqlserver";
        this.onChangeCallback = onChangeCallback;

        getStyleClass().add("td-constraint-grid-pane");
        VBox.setVgrow(this, Priority.ALWAYS);

        HBox toolbar = buildToolbar();
        setupTableView();

        VBox.setVgrow(tableView, Priority.ALWAYS);
        getChildren().addAll(toolbar, tableView);

        applyGridTypography();
        SettingsService.getInstance().settingsProperty().addListener((obs, oldV, newV) -> applyGridTypography());
    }

    private void applyGridTypography() {
        var settings = SettingsService.getInstance().getCurrentSettings();
        int fontSize = settings != null ? settings.getGridFontSize() : 12;
        double rowHeight = Math.max(24.0, fontSize * 1.8 + 6.0);
        tableView.setFixedCellSize(rowHeight);
    }

    private HBox buildToolbar() {
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.getStyleClass().add("td-row-toolbar");

        Button btnAdd = new Button("Thêm chỉ mục");
        btnAdd.getStyleClass().addAll("button", "small");
        FontIcon addIcon = new FontIcon("fth-plus");
        addIcon.setIconSize(13);
        btnAdd.setGraphic(addIcon);
        btnAdd.setTooltip(new Tooltip("Thêm chỉ mục mới"));
        btnAdd.setOnAction(e -> addNewIndex());

        Button btnDelete = new Button("Xóa");
        btnDelete.getStyleClass().addAll("button", "small");
        FontIcon delIcon = new FontIcon("fth-trash-2");
        delIcon.setIconSize(13);
        btnDelete.setGraphic(delIcon);
        btnDelete.setTooltip(new Tooltip("Xóa index đang chọn"));
        btnDelete.setOnAction(e -> deleteSelectedIndex());

        Button btnUp = new Button();
        btnUp.getStyleClass().addAll("button", "small");
        FontIcon upIcon = new FontIcon("fth-arrow-up");
        upIcon.setIconSize(13);
        btnUp.setGraphic(upIcon);
        btnUp.setTooltip(new Tooltip("Di chuyển lên"));
        btnUp.setOnAction(e -> moveSelected(-1));

        Button btnDown = new Button();
        btnDown.getStyleClass().addAll("button", "small");
        FontIcon downIcon = new FontIcon("fth-arrow-down");
        downIcon.setIconSize(13);
        btnDown.setGraphic(downIcon);
        btnDown.setTooltip(new Tooltip("Di chuyển xuống"));
        btnDown.setOnAction(e -> moveSelected(1));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblHelp = new Label("Cột phân cách bằng dấu phẩy, vd: col1, col2 DESC");
        lblHelp.getStyleClass().add("dialog-message");

        bar.getChildren().addAll(btnAdd, btnDelete, new Separator(), btnUp, btnDown, spacer, lblHelp);
        return bar;
    }

    private void setupTableView() {
        tableView.setEditable(true);
        tableView.setPlaceholder(new Label("Chưa có chỉ mục nào. Bấm 'Thêm Index' để tạo mới."));
        tableView.getStyleClass().add("td-column-table");

        // 1. Index Name
        TableColumn<IndexViewModel, String> colName = new TableColumn<>("Tên Index");
        colName.setPrefWidth(200);
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setOnEditCommit(e -> {
            e.getRowValue().setName(e.getNewValue());
            notifyChange();
        });

        // 2. Index Type
        TableColumn<IndexViewModel, IndexType> colType = new TableColumn<>("Kiểu Index");
        colType.setPrefWidth(140);
        colType.setCellValueFactory(data -> data.getValue().indexTypeProperty());
        colType.setCellFactory(ComboBoxTableCell.forTableColumn(IndexType.values()));
        colType.setOnEditCommit(e -> {
            e.getRowValue().setIndexType(e.getNewValue());
            notifyChange();
        });

        // 3. Unique Checkbox
        TableColumn<IndexViewModel, Boolean> colUnique = new TableColumn<>("Unique");
        colUnique.setPrefWidth(75);
        colUnique.setCellValueFactory(data -> data.getValue().uniqueProperty());
        colUnique.setCellFactory(CheckBoxTableCell.forTableColumn(colUnique));

        // 4. Clustered Checkbox (SQL Server only)
        TableColumn<IndexViewModel, Boolean> colClustered = new TableColumn<>("Clustered");
        colClustered.setPrefWidth(85);
        colClustered.setCellValueFactory(data -> data.getValue().clusteredProperty());
        colClustered.setCellFactory(CheckBoxTableCell.forTableColumn(colClustered));
        if ("postgresql".equalsIgnoreCase(engine)) {
            colClustered.setVisible(false);
        }

        // 5. Columns
        TableColumn<IndexViewModel, String> colCols = new TableColumn<>("Các cột tham gia");
        colCols.setPrefWidth(220);
        colCols.setCellValueFactory(data -> data.getValue().columnsStringProperty());
        colCols.setCellFactory(TextFieldTableCell.forTableColumn());
        colCols.setOnEditCommit(e -> {
            e.getRowValue().setColumnsString(e.getNewValue());
            notifyChange();
        });

        // 6. Filter Predicate
        TableColumn<IndexViewModel, String> colFilter = new TableColumn<>("Điều kiện lọc");
        colFilter.setPrefWidth(200);
        colFilter.setCellValueFactory(data -> data.getValue().filterPredicateProperty());
        colFilter.setCellFactory(TextFieldTableCell.forTableColumn());
        colFilter.setOnEditCommit(e -> {
            e.getRowValue().setFilterPredicate(e.getNewValue());
            notifyChange();
        });

        tableView.getColumns().addAll(colName, colType, colUnique, colClustered, colCols, colFilter);
        tableView.setItems(items);
    }

    private void addNewIndex() {
        String baseName = "IX_Table_" + (items.size() + 1);
        IndexViewModel vm = new IndexViewModel(baseName, "", false, false, IndexType.NONCLUSTERED, "");
        vm.uniqueProperty().addListener((obs, o, n) -> notifyChange());
        vm.clusteredProperty().addListener((obs, o, n) -> notifyChange());
        items.add(vm);
        tableView.getSelectionModel().select(vm);
        notifyChange();
    }

    private void deleteSelectedIndex() {
        IndexViewModel sel = tableView.getSelectionModel().getSelectedItem();
        if (sel != null) {
            items.remove(sel);
            notifyChange();
        }
    }

    private void moveSelected(int delta) {
        int idx = tableView.getSelectionModel().getSelectedIndex();
        if (idx < 0) return;
        int target = idx + delta;
        if (target >= 0 && target < items.size()) {
            IndexViewModel item = items.remove(idx);
            items.add(target, item);
            tableView.getSelectionModel().select(target);
            notifyChange();
        }
    }

    private void notifyChange() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    public void setItems(List<IndexDesign> indexes) {
        items.clear();
        if (indexes != null) {
            for (IndexDesign idx : indexes) {
                String colsStr = idx.getColumns().stream()
                        .map(c -> c.getColumnName() + (c.isDescending() ? " DESC" : ""))
                        .collect(Collectors.joining(", "));
                IndexViewModel vm = new IndexViewModel(
                        idx.getName(),
                        colsStr,
                        idx.isUnique(),
                        idx.isClustered(),
                        idx.getIndexType(),
                        idx.getFilterPredicate()
                );
                vm.uniqueProperty().addListener((obs, o, n) -> notifyChange());
                vm.clusteredProperty().addListener((obs, o, n) -> notifyChange());
                items.add(vm);
            }
        }
    }

    public List<IndexDesign> toDomainIndexes() {
        List<IndexDesign> result = new ArrayList<>();
        for (IndexViewModel vm : items) {
            IndexDesign id = new IndexDesign(vm.getName());
            id.setIndexType(vm.getIndexType());
            id.setUnique(vm.isUnique());
            id.setClustered(vm.isClustered());
            id.setFilterPredicate(vm.getFilterPredicate());

            String colsStr = vm.getColumnsString();
            if (colsStr != null && !colsStr.isBlank()) {
                String[] parts = colsStr.split(",");
                for (String part : parts) {
                    String clean = part.trim();
                    if (!clean.isEmpty()) {
                        boolean desc = clean.toUpperCase().endsWith(" DESC");
                        String colName = desc ? clean.substring(0, clean.length() - 5).trim() : clean;
                        if (colName.toUpperCase().endsWith(" ASC")) {
                            colName = colName.substring(0, colName.length() - 4).trim();
                        }
                        id.getColumns().add(new IndexColumn(null, colName, desc));
                    }
                }
            }
            result.add(id);
        }
        return result;
    }

    public static class IndexViewModel {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty columnsString = new SimpleStringProperty();
        private final BooleanProperty unique = new SimpleBooleanProperty();
        private final BooleanProperty clustered = new SimpleBooleanProperty();
        private final ObjectProperty<IndexType> indexType = new SimpleObjectProperty<>(IndexType.NONCLUSTERED);
        private final StringProperty filterPredicate = new SimpleStringProperty();

        public IndexViewModel(String name, String cols, boolean unique, boolean clustered, IndexType type, String filter) {
            this.name.set(name);
            this.columnsString.set(cols);
            this.unique.set(unique);
            this.clustered.set(clustered);
            this.indexType.set(type != null ? type : IndexType.NONCLUSTERED);
            this.filterPredicate.set(filter);
        }

        public String getName() { return name.get(); }
        public void setName(String v) { name.set(v); }
        public StringProperty nameProperty() { return name; }

        public String getColumnsString() { return columnsString.get(); }
        public void setColumnsString(String v) { columnsString.set(v); }
        public StringProperty columnsStringProperty() { return columnsString; }

        public boolean isUnique() { return unique.get(); }
        public void setUnique(boolean v) { unique.set(v); }
        public BooleanProperty uniqueProperty() { return unique; }

        public boolean isClustered() { return clustered.get(); }
        public void setClustered(boolean v) { clustered.set(v); }
        public BooleanProperty clusteredProperty() { return clustered; }

        public IndexType getIndexType() { return indexType.get(); }
        public void setIndexType(IndexType v) { indexType.set(v); }
        public ObjectProperty<IndexType> indexTypeProperty() { return indexType; }

        public String getFilterPredicate() { return filterPredicate.get(); }
        public void setFilterPredicate(String v) { filterPredicate.set(v); }
        public StringProperty filterPredicateProperty() { return filterPredicate; }
    }
}
