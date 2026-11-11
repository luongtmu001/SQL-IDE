package app.ui.designer;

import app.services.SettingsService;
import com.lltool.core.domain.tabledesigner.model.ForeignKeyColumnMapping;
import com.lltool.core.domain.tabledesigner.model.ForeignKeyDesign;
import com.lltool.core.domain.tabledesigner.valueobject.ForeignKeyAction;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Grid view for managing Foreign Key constraints in Table Designer (SCR-TD-001 / TD3).
 */
public class ForeignKeyGrid extends VBox {

    private final String engine;
    private final Runnable onChangeCallback;
    private final ObservableList<ForeignKeyViewModel> items = FXCollections.observableArrayList();
    private final TableView<ForeignKeyViewModel> tableView = new TableView<>();

    public ForeignKeyGrid(String engine, Runnable onChangeCallback) {
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

        Button btnAdd = new Button("Thêm Khoá ngoại");
        btnAdd.getStyleClass().add("button");
        btnAdd.setGraphic(app.ui.components.Icons.of("fth-plus"));
        btnAdd.setTooltip(new Tooltip("Thêm ràng buộc khoá ngoại mới"));
        btnAdd.setOnAction(e -> addNewFk());

        Button btnDelete = new Button("Xóa");
        btnDelete.getStyleClass().add("button");
        btnDelete.setGraphic(app.ui.components.Icons.of("fth-trash-2"));
        btnDelete.setTooltip(new Tooltip("Xóa khóa ngoại đang chọn"));
        btnDelete.setOnAction(e -> deleteSelectedFk());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblHelp = new Label("Cột ánh xạ phân cách bằng dấu phẩy, vd: order_id -> id");
        lblHelp.getStyleClass().add("dialog-message");

        bar.getChildren().addAll(btnAdd, btnDelete, spacer, lblHelp);
        return bar;
    }

    private void setupTableView() {
        tableView.setEditable(true);
        tableView.setPlaceholder(new Label("Chưa có khóa ngoại nào. Bấm 'Thêm Khóa ngoại' để tạo mới."));
        tableView.getStyleClass().add("td-column-table");

        // 1. FK Name
        TableColumn<ForeignKeyViewModel, String> colName = new TableColumn<>("Tên Khóa ngoại");
        colName.setPrefWidth(app.ui.components.UiMetrics.em(13.85));
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setOnEditCommit(e -> {
            e.getRowValue().setName(e.getNewValue());
            notifyChange();
        });

        // 2. Source Column(s)
        TableColumn<ForeignKeyViewModel, String> colSource = new TableColumn<>("Cột bảng hiện tại");
        colSource.setPrefWidth(app.ui.components.UiMetrics.em(12.31));
        colSource.setCellValueFactory(data -> data.getValue().sourceColumnsProperty());
        colSource.setCellFactory(TextFieldTableCell.forTableColumn());
        colSource.setOnEditCommit(e -> {
            e.getRowValue().setSourceColumns(e.getNewValue());
            notifyChange();
        });

        // 3. Target Schema
        TableColumn<ForeignKeyViewModel, String> colTargetSchema = new TableColumn<>("Schema đích");
        colTargetSchema.setPrefWidth(app.ui.components.UiMetrics.em(8.46));
        colTargetSchema.setCellValueFactory(data -> data.getValue().targetSchemaProperty());
        colTargetSchema.setCellFactory(TextFieldTableCell.forTableColumn());
        colTargetSchema.setOnEditCommit(e -> {
            e.getRowValue().setTargetSchema(e.getNewValue());
            notifyChange();
        });

        // 4. Target Table
        TableColumn<ForeignKeyViewModel, String> colTargetTable = new TableColumn<>("Bảng tham chiếu");
        colTargetTable.setPrefWidth(app.ui.components.UiMetrics.em(12.31));
        colTargetTable.setCellValueFactory(data -> data.getValue().targetTableProperty());
        colTargetTable.setCellFactory(TextFieldTableCell.forTableColumn());
        colTargetTable.setOnEditCommit(e -> {
            e.getRowValue().setTargetTable(e.getNewValue());
            notifyChange();
        });

        // 5. Target Column(s)
        TableColumn<ForeignKeyViewModel, String> colTargetCols = new TableColumn<>("Cột tham chiếu");
        colTargetCols.setPrefWidth(app.ui.components.UiMetrics.em(12.31));
        colTargetCols.setCellValueFactory(data -> data.getValue().targetColumnsProperty());
        colTargetCols.setCellFactory(TextFieldTableCell.forTableColumn());
        colTargetCols.setOnEditCommit(e -> {
            e.getRowValue().setTargetColumns(e.getNewValue());
            notifyChange();
        });

        // 6. On Delete
        TableColumn<ForeignKeyViewModel, ForeignKeyAction> colOnDelete = new TableColumn<>("On Delete");
        colOnDelete.setPrefWidth(app.ui.components.UiMetrics.em(9.62));
        colOnDelete.setCellValueFactory(data -> data.getValue().onDeleteProperty());
        colOnDelete.setCellFactory(ComboBoxTableCell.forTableColumn(
                ForeignKeyAction.NO_ACTION, ForeignKeyAction.CASCADE, ForeignKeyAction.SET_NULL, ForeignKeyAction.SET_DEFAULT, ForeignKeyAction.RESTRICT
        ));
        colOnDelete.setOnEditCommit(e -> {
            e.getRowValue().setOnDelete(e.getNewValue());
            notifyChange();
        });

        // 7. On Update
        TableColumn<ForeignKeyViewModel, ForeignKeyAction> colOnUpdate = new TableColumn<>("On Update");
        colOnUpdate.setPrefWidth(app.ui.components.UiMetrics.em(9.62));
        colOnUpdate.setCellValueFactory(data -> data.getValue().onUpdateProperty());
        colOnUpdate.setCellFactory(ComboBoxTableCell.forTableColumn(
                ForeignKeyAction.NO_ACTION, ForeignKeyAction.CASCADE, ForeignKeyAction.SET_NULL, ForeignKeyAction.SET_DEFAULT, ForeignKeyAction.RESTRICT
        ));
        colOnUpdate.setOnEditCommit(e -> {
            e.getRowValue().setOnUpdate(e.getNewValue());
            notifyChange();
        });

        tableView.getColumns().addAll(colName, colSource, colTargetSchema, colTargetTable, colTargetCols, colOnDelete, colOnUpdate);
        tableView.setItems(items);
    }

    private void addNewFk() {
        String baseName = "FK_Table_Ref_" + (items.size() + 1);
        String defaultSchema = "postgresql".equalsIgnoreCase(engine) ? "public" : "dbo";
        ForeignKeyViewModel vm = new ForeignKeyViewModel(baseName, "", defaultSchema, "", "", ForeignKeyAction.NO_ACTION, ForeignKeyAction.NO_ACTION);
        items.add(vm);
        tableView.getSelectionModel().select(vm);
        notifyChange();
    }

    private void deleteSelectedFk() {
        ForeignKeyViewModel sel = tableView.getSelectionModel().getSelectedItem();
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

    public void setItems(List<ForeignKeyDesign> fks) {
        items.clear();
        if (fks != null) {
            for (ForeignKeyDesign fk : fks) {
                String srcCols = fk.getColumnMappings().stream()
                        .map(ForeignKeyColumnMapping::getSourceColumn)
                        .collect(Collectors.joining(", "));
                String tgtCols = fk.getColumnMappings().stream()
                        .map(ForeignKeyColumnMapping::getTargetColumn)
                        .collect(Collectors.joining(", "));

                ForeignKeyViewModel vm = new ForeignKeyViewModel(
                        fk.getName(),
                        srcCols,
                        fk.getTargetSchema(),
                        fk.getTargetTable(),
                        tgtCols,
                        fk.getOnDelete(),
                        fk.getOnUpdate()
                );
                items.add(vm);
            }
        }
    }

    public List<ForeignKeyDesign> toDomainForeignKeys() {
        List<ForeignKeyDesign> result = new ArrayList<>();
        for (ForeignKeyViewModel vm : items) {
            ForeignKeyDesign fk = new ForeignKeyDesign(vm.getName());
            fk.setTargetSchema(vm.getTargetSchema());
            fk.setTargetTable(vm.getTargetTable());
            fk.setOnDelete(vm.getOnDelete());
            fk.setOnUpdate(vm.getOnUpdate());

            String[] src = (vm.getSourceColumns() != null) ? vm.getSourceColumns().split(",") : new String[0];
            String[] tgt = (vm.getTargetColumns() != null) ? vm.getTargetColumns().split(",") : new String[0];

            List<ForeignKeyColumnMapping> mappings = new ArrayList<>();
            int count = Math.min(src.length, tgt.length);
            for (int i = 0; i < count; i++) {
                String s = src[i].trim();
                String t = tgt[i].trim();
                if (!s.isEmpty() && !t.isEmpty()) {
                    mappings.add(new ForeignKeyColumnMapping(s, t));
                }
            }
            fk.setColumnMappings(mappings);
            result.add(fk);
        }
        return result;
    }

    public static class ForeignKeyViewModel {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty sourceColumns = new SimpleStringProperty();
        private final StringProperty targetSchema = new SimpleStringProperty();
        private final StringProperty targetTable = new SimpleStringProperty();
        private final StringProperty targetColumns = new SimpleStringProperty();
        private final ObjectProperty<ForeignKeyAction> onDelete = new SimpleObjectProperty<>(ForeignKeyAction.NO_ACTION);
        private final ObjectProperty<ForeignKeyAction> onUpdate = new SimpleObjectProperty<>(ForeignKeyAction.NO_ACTION);

        public ForeignKeyViewModel(String name, String src, String tgtSchema, String tgtTable, String tgtCols,
                                   ForeignKeyAction onDel, ForeignKeyAction onUpd) {
            this.name.set(name);
            this.sourceColumns.set(src);
            this.targetSchema.set(tgtSchema);
            this.targetTable.set(tgtTable);
            this.targetColumns.set(tgtCols);
            this.onDelete.set(onDel != null ? onDel : ForeignKeyAction.NO_ACTION);
            this.onUpdate.set(onUpd != null ? onUpd : ForeignKeyAction.NO_ACTION);
        }

        public String getName() { return name.get(); }
        public void setName(String v) { name.set(v); }
        public StringProperty nameProperty() { return name; }

        public String getSourceColumns() { return sourceColumns.get(); }
        public void setSourceColumns(String v) { sourceColumns.set(v); }
        public StringProperty sourceColumnsProperty() { return sourceColumns; }

        public String getTargetSchema() { return targetSchema.get(); }
        public void setTargetSchema(String v) { targetSchema.set(v); }
        public StringProperty targetSchemaProperty() { return targetSchema; }

        public String getTargetTable() { return targetTable.get(); }
        public void setTargetTable(String v) { targetTable.set(v); }
        public StringProperty targetTableProperty() { return targetTable; }

        public String getTargetColumns() { return targetColumns.get(); }
        public void setTargetColumns(String v) { targetColumns.set(v); }
        public StringProperty targetColumnsProperty() { return targetColumns; }

        public ForeignKeyAction getOnDelete() { return onDelete.get(); }
        public void setOnDelete(ForeignKeyAction v) { onDelete.set(v); }
        public ObjectProperty<ForeignKeyAction> onDeleteProperty() { return onDelete; }

        public ForeignKeyAction getOnUpdate() { return onUpdate.get(); }
        public void setOnUpdate(ForeignKeyAction v) { onUpdate.set(v); }
        public ObjectProperty<ForeignKeyAction> onUpdateProperty() { return onUpdate; }
    }
}
