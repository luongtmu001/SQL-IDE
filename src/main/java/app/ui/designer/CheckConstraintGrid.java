package app.ui.designer;

import app.services.SettingsService;
import com.lltool.core.domain.tabledesigner.model.CheckDesign;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid view for managing Check Constraints in Table Designer (SCR-TD-001 / TD3).
 */
public class CheckConstraintGrid extends VBox {

    private final String engine;
    private final Runnable onChangeCallback;
    private final ObservableList<CheckViewModel> items = FXCollections.observableArrayList();
    private final TableView<CheckViewModel> tableView = new TableView<>();

    public CheckConstraintGrid(String engine, Runnable onChangeCallback) {
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

        Button btnAdd = new Button("Thêm Check");
        btnAdd.getStyleClass().add("button");
        btnAdd.setGraphic(app.ui.components.Icons.of("fth-plus"));
        btnAdd.setTooltip(new Tooltip("Thêm ràng buộc kiểm tra mới"));
        btnAdd.setOnAction(e -> addNewCheck());

        Button btnDelete = new Button("Xóa");
        btnDelete.getStyleClass().add("button");
        btnDelete.setGraphic(app.ui.components.Icons.of("fth-trash-2"));
        btnDelete.setTooltip(new Tooltip("Xóa ràng buộc đang chọn"));
        btnDelete.setOnAction(e -> deleteSelected());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblHelp = new Label("Nhập biểu thức kiểm tra SQL, vd: [Age] >= 18 HOẶC age >= 18");
        lblHelp.getStyleClass().add("dialog-message");

        bar.getChildren().addAll(btnAdd, btnDelete, spacer, lblHelp);
        return bar;
    }

    private void setupTableView() {
        tableView.setEditable(true);
        tableView.setPlaceholder(new Label("Chưa có ràng buộc Check nào. Bấm 'Thêm Check' để tạo mới."));
        tableView.getStyleClass().add("td-column-table");

        // 1. Constraint Name
        TableColumn<CheckViewModel, String> colName = new TableColumn<>("Tên Ràng buộc");
        colName.setPrefWidth(app.ui.components.UiMetrics.em(16.92));
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setOnEditCommit(e -> {
            e.getRowValue().setName(e.getNewValue());
            notifyChange();
        });

        // 2. Check Expression
        TableColumn<CheckViewModel, String> colClause = new TableColumn<>("Biểu thức kiểm tra");
        colClause.setPrefWidth(app.ui.components.UiMetrics.em(29.23));
        colClause.setCellValueFactory(data -> data.getValue().clauseProperty());
        colClause.setCellFactory(TextFieldTableCell.forTableColumn());
        colClause.setOnEditCommit(e -> {
            e.getRowValue().setClause(e.getNewValue());
            notifyChange();
        });

        tableView.getColumns().addAll(colName, colClause);
        tableView.setItems(items);
    }

    private void addNewCheck() {
        String baseName = "CK_Table_" + (items.size() + 1);
        CheckViewModel vm = new CheckViewModel(baseName, "");
        items.add(vm);
        tableView.getSelectionModel().select(vm);
        notifyChange();
    }

    private void deleteSelected() {
        CheckViewModel sel = tableView.getSelectionModel().getSelectedItem();
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

    public void setItems(List<CheckDesign> checks) {
        items.clear();
        if (checks != null) {
            for (CheckDesign ck : checks) {
                items.add(new CheckViewModel(ck.getName(), ck.getCheckClause()));
            }
        }
    }

    public List<CheckDesign> toDomainChecks() {
        List<CheckDesign> result = new ArrayList<>();
        for (CheckViewModel vm : items) {
            result.add(new CheckDesign(vm.getName(), vm.getClause()));
        }
        return result;
    }

    public static class CheckViewModel {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty clause = new SimpleStringProperty();

        public CheckViewModel(String name, String clause) {
            this.name.set(name);
            this.clause.set(clause);
        }

        public String getName() { return name.get(); }
        public void setName(String v) { name.set(v); }
        public StringProperty nameProperty() { return name; }

        public String getClause() { return clause.get(); }
        public void setClause(String v) { clause.set(v); }
        public StringProperty clauseProperty() { return clause; }
    }
}

