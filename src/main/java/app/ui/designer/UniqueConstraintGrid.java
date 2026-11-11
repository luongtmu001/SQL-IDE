package app.ui.designer;

import app.services.SettingsService;
import com.lltool.core.domain.tabledesigner.model.UniqueDesign;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
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
 * Grid view for managing Unique Constraints in Table Designer (SCR-TD-001 / TD3).
 */
public class UniqueConstraintGrid extends VBox {

    private final String engine;
    private final Runnable onChangeCallback;
    private final ObservableList<UniqueViewModel> items = FXCollections.observableArrayList();
    private final TableView<UniqueViewModel> tableView = new TableView<>();

    public UniqueConstraintGrid(String engine, Runnable onChangeCallback) {
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
        // R-UI-11: a configuration grid follows the UI font ("Giao diện"), not the result-grid setting
        tableView.setFixedCellSize(app.ui.components.UiMetrics.em(1.85));
    }

    private HBox buildToolbar() {
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.getStyleClass().add("td-row-toolbar");

        Button btnAdd = new Button("Thêm Unique");
        btnAdd.getStyleClass().add("button");
        btnAdd.setGraphic(app.ui.components.Icons.of("fth-plus"));
        btnAdd.setTooltip(new Tooltip("Thêm ràng buộc duy nhất mới"));
        btnAdd.setOnAction(e -> addNewUnique());

        Button btnDelete = new Button("Xóa");
        btnDelete.getStyleClass().add("button");
        btnDelete.setGraphic(app.ui.components.Icons.of("fth-trash-2"));
        btnDelete.setTooltip(new Tooltip("Xóa ràng buộc đang chọn"));
        btnDelete.setOnAction(e -> deleteSelected());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblHelp = new Label("Cột phân cách bằng dấu phẩy, vd: email, phone");
        lblHelp.getStyleClass().add("dialog-message");

        bar.getChildren().addAll(btnAdd, btnDelete, spacer, lblHelp);
        return bar;
    }

    private void setupTableView() {
        tableView.setEditable(true);
        tableView.setPlaceholder(new Label("Chưa có ràng buộc Unique nào. Bấm 'Thêm Unique' để tạo mới."));
        tableView.getStyleClass().add("td-column-table");

        // 1. Constraint Name
        TableColumn<UniqueViewModel, String> colName = new TableColumn<>("Tên Ràng buộc");
        colName.setPrefWidth(app.ui.components.UiMetrics.em(16.92));
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setOnEditCommit(e -> {
            e.getRowValue().setName(e.getNewValue());
            notifyChange();
        });

        // 2. Columns
        TableColumn<UniqueViewModel, String> colCols = new TableColumn<>("Các cột duy nhất");
        colCols.setPrefWidth(app.ui.components.UiMetrics.em(21.54));
        colCols.setCellValueFactory(data -> data.getValue().columnsStringProperty());
        colCols.setCellFactory(TextFieldTableCell.forTableColumn());
        colCols.setOnEditCommit(e -> {
            e.getRowValue().setColumnsString(e.getNewValue());
            notifyChange();
        });

        // 3. Clustered (SQL Server only)
        TableColumn<UniqueViewModel, Boolean> colClustered = new TableColumn<>("Clustered");
        colClustered.setPrefWidth(app.ui.components.UiMetrics.em(6.92));
        colClustered.setCellValueFactory(data -> data.getValue().clusteredProperty());
        colClustered.setCellFactory(CheckBoxTableCell.forTableColumn(colClustered));
        if ("postgresql".equalsIgnoreCase(engine)) {
            colClustered.setVisible(false);
        }

        tableView.getColumns().addAll(colName, colCols, colClustered);
        tableView.setItems(items);
    }

    private void addNewUnique() {
        String baseName = "UQ_Table_" + (items.size() + 1);
        UniqueViewModel vm = new UniqueViewModel(baseName, "", false);
        vm.clusteredProperty().addListener((obs, o, n) -> notifyChange());
        items.add(vm);
        tableView.getSelectionModel().select(vm);
        notifyChange();
    }

    private void deleteSelected() {
        UniqueViewModel sel = tableView.getSelectionModel().getSelectedItem();
        if (sel != null) {
            items.remove(sel);
            notifyChange();
        }
    }

    private void notifyChange() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    public void setItems(List<UniqueDesign> uniques) {
        items.clear();
        if (uniques != null) {
            for (UniqueDesign uq : uniques) {
                String colsStr = String.join(", ", uq.getColumns());
                UniqueViewModel vm = new UniqueViewModel(uq.getName(), colsStr, uq.isClustered());
                vm.clusteredProperty().addListener((obs, o, n) -> notifyChange());
                items.add(vm);
            }
        }
    }

    public List<UniqueDesign> toDomainUniques() {
        List<UniqueDesign> result = new ArrayList<>();
        for (UniqueViewModel vm : items) {
            UniqueDesign uq = new UniqueDesign(vm.getName());
            uq.setClustered(vm.isClustered());

            String colsStr = vm.getColumnsString();
            if (colsStr != null && !colsStr.isBlank()) {
                List<String> cols = Arrays.stream(colsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                uq.setColumns(cols);
            }
            result.add(uq);
        }
        return result;
    }

    public static class UniqueViewModel {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty columnsString = new SimpleStringProperty();
        private final BooleanProperty clustered = new SimpleBooleanProperty();

        public UniqueViewModel(String name, String cols, boolean clustered) {
            this.name.set(name);
            this.columnsString.set(cols);
            this.clustered.set(clustered);
        }

        public String getName() { return name.get(); }
        public void setName(String v) { name.set(v); }
        public StringProperty nameProperty() { return name; }

        public String getColumnsString() { return columnsString.get(); }
        public void setColumnsString(String v) { columnsString.set(v); }
        public StringProperty columnsStringProperty() { return columnsString; }

        public boolean isClustered() { return clustered.get(); }
        public void setClustered(boolean v) { clustered.set(v); }
        public BooleanProperty clusteredProperty() { return clustered; }
    }
}

