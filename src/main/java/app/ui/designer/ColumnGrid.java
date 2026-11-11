package app.ui.designer;

import app.services.SettingsService;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.converter.IntegerStringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Visual TableView grid for editing columns in Table Designer (SCR-TD-001 / TD3).
 * Implements all 15 row operations (TD-ROW-01..15) with full multi-row selection,
 * Undo/Redo support, Excel TSV clipboard compatibility, and font-size scaling.
 */
public class ColumnGrid extends VBox {

    private static final Logger log = LoggerFactory.getLogger(ColumnGrid.class);

    private static final DataFormat COLUMN_TSV_FORMAT = DataFormat.PLAIN_TEXT;

    private final String engine;
    private final boolean isNewTable;
    private final TableDesignerUndoManager undoManager;
    private final Runnable onChangeCallback;

    private final ObservableList<ColumnDesignViewModel> items = FXCollections.observableArrayList();
    private final TableView<ColumnDesignViewModel> tableView = new TableView<>();

    private final List<ColumnDesignViewModel> internalClipboard = new ArrayList<>();

    // Filter for edit mode: "Chỉ hiện cột đã đổi"
    private final CheckBox filterChangedOnly = new CheckBox("Chỉ hiện cột đã đổi");

    public ColumnGrid(String engine, boolean isNewTable, TableDesignerUndoManager undoManager, Runnable onChangeCallback) {
        this.engine = engine != null ? engine.toLowerCase() : "sqlserver";
        this.isNewTable = isNewTable;
        this.undoManager = undoManager;
        this.onChangeCallback = onChangeCallback;

        getStyleClass().add("td-column-grid-pane");
        VBox.setVgrow(this, Priority.ALWAYS);

        HBox toolbar = buildRowToolbar();
        setupTableView();

        VBox.setVgrow(tableView, Priority.ALWAYS);
        getChildren().addAll(toolbar, tableView);

        // Listen for Settings changes to update grid row height
        applyGridTypography();
        SettingsService.getInstance().settingsProperty().addListener((obs, oldV, newV) -> applyGridTypography());
    }

    private void applyGridTypography() {
        var settings = SettingsService.getInstance().getCurrentSettings();
        // R-UI-11: a configuration grid follows the UI font ("Giao diện"), not the result-grid setting
        tableView.setFixedCellSize(app.ui.components.UiMetrics.em(1.85));
    }

    public ObservableList<ColumnDesignViewModel> getItems() {
        return items;
    }

    public TableView<ColumnDesignViewModel> getTableView() {
        return tableView;
    }

    public void setItems(List<ColumnDesign> columns) {
        items.clear();
        if (columns != null) {
            for (ColumnDesign col : columns) {
                items.add(ColumnDesignViewModel.fromDomain(col));
            }
        }
        if (items.isEmpty()) {
            addNewRowAtEnd();
        }
        tableView.setItems(items);
        if (!items.isEmpty()) {
            tableView.getSelectionModel().select(0);
        }
    }

    public List<ColumnDesign> toDomainColumns() {
        return items.stream()
                .map(ColumnDesignViewModel::toDomain)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Toolbar
    // -------------------------------------------------------------------------
    private HBox buildRowToolbar() {
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("td-row-toolbar");

        // Thêm cột ▾
        MenuButton btnAddMenu = new MenuButton("Thêm cột");
        btnAddMenu.getStyleClass().add("button");
        btnAddMenu.setGraphic(app.ui.components.Icons.of("fth-plus"));

        MenuItem itemAddEnd = new MenuItem("Thêm ở cuối (Ctrl+N)");
        itemAddEnd.setOnAction(e -> addNewRowAtEnd());

        MenuItem itemInsAbove = new MenuItem("Chèn phía trên (Ctrl+Insert)");
        itemInsAbove.setOnAction(e -> insertRowAbove());

        MenuItem itemInsBelow = new MenuItem("Chèn phía dưới (Ctrl+Shift+Insert)");
        itemInsBelow.setOnAction(e -> insertRowBelow());

        MenuItem itemAdd5 = new MenuItem("Thêm 5 dòng");
        itemAdd5.setOnAction(e -> addMultipleRows(5));

        MenuItem itemAdd10 = new MenuItem("Thêm 10 dòng");
        itemAdd10.setOnAction(e -> addMultipleRows(10));

        btnAddMenu.getItems().addAll(itemAddEnd, itemInsAbove, itemInsBelow, new SeparatorMenuItem(), itemAdd5, itemAdd10);

        Button btnDup = new Button("Nhân bản");
        btnDup.getStyleClass().add("button");
        btnDup.setGraphic(app.ui.components.Icons.of("mdi2c-content-duplicate"));
        btnDup.setTooltip(new Tooltip("Nhân bản cột đang chọn"));
        btnDup.setOnAction(e -> duplicateSelectedRows());

        Button btnDel = new Button("Xoá");
        btnDel.getStyleClass().add("button");
        btnDel.setGraphic(app.ui.components.Icons.of("mdi2t-table-row-remove"));
        btnDel.setTooltip(new Tooltip("Xoá các cột đang chọn (Delete)"));
        btnDel.setOnAction(e -> deleteSelectedRows());

        Button btnUp = new Button();
        btnUp.getStyleClass().add("button");
        btnUp.setGraphic(app.ui.components.Icons.of("fth-arrow-up"));
        btnUp.setTooltip(new Tooltip("Di chuyển lên (Alt + Mũi tên lên)"));
        btnUp.setOnAction(e -> moveSelectedRowsUp());

        Button btnDown = new Button();
        btnDown.getStyleClass().add("button");
        btnDown.setGraphic(app.ui.components.Icons.of("fth-arrow-down"));
        btnDown.setTooltip(new Tooltip("Di chuyển xuống (Alt + Mũi tên xuống)"));
        btnDown.setOnAction(e -> moveSelectedRowsDown());

        Button btnPk = new Button("Khoá chính");
        btnPk.getStyleClass().add("button");
        btnPk.setGraphic(app.ui.components.Icons.of("mdi2k-key"));
        btnPk.setTooltip(new Tooltip("Đặt/bỏ khoá chính cho cột đang chọn"));
        btnPk.setOnAction(e -> togglePrimaryKeyOnSelected());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(
                btnAddMenu,
                btnDup, btnDel,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                btnUp, btnDown,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                btnPk,
                spacer
        );

        if (!isNewTable) {
            filterChangedOnly.setOnAction(e -> applyFilterChangedOnly(filterChangedOnly.isSelected()));
            bar.getChildren().add(filterChangedOnly);
        }

        return bar;
    }

    private Button toolButton(String tooltipText, String iconLiteral, Runnable action) {
        Button btn = new Button();
        btn.getStyleClass().add("button");
        if (iconLiteral != null) {
            btn.setGraphic(app.ui.components.Icons.of(iconLiteral, app.ui.components.IconScale.MD));
        } else {
            btn.setText("Thêm N dòng");
        }
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setAccessibleText(tooltipText);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    // -------------------------------------------------------------------------
    // TableView Setup
    // -------------------------------------------------------------------------
    private void setupTableView() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableView.setPlaceholder(new Label("Không có cột nào"));

        // 1. Status Column (28px)
        TableColumn<ColumnDesignViewModel, String> colStatus = new TableColumn<>("");
        colStatus.setMinWidth(app.ui.components.UiMetrics.em(2.15));
        colStatus.setMaxWidth(app.ui.components.UiMetrics.em(2.62));
        colStatus.setPrefWidth(app.ui.components.UiMetrics.em(2.15));
        colStatus.setResizable(false);
        colStatus.setSortable(false);
        colStatus.setCellValueFactory(cell -> cell.getValue().statusMarkerProperty());
        colStatus.setCellFactory(col -> new TableCell<>() {
            // R-UI-15: the row state is an icon, not a text symbol
            private final org.kordamp.ikonli.javafx.FontIcon statusIcon = app.ui.components.Icons.of("fth-circle", app.ui.components.IconScale.SM);

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setAlignment(Pos.CENTER);
                getStyleClass().removeAll("td-status-added", "td-status-modified", "td-status-renamed", "td-status-deleted");
                String literal = null;
                String styleClass = null;
                String hint = null;
                if (!empty && item != null) {
                    switch (item) {
                        case ColumnDesignViewModel.STATUS_ADDED -> { literal = "fth-plus"; styleClass = "td-status-added"; hint = "Cột mới"; }
                        case ColumnDesignViewModel.STATUS_MODIFIED -> { literal = "fth-edit-2"; styleClass = "td-status-modified"; hint = "Đã sửa"; }
                        case ColumnDesignViewModel.STATUS_RENAMED -> { literal = "fth-refresh-cw"; styleClass = "td-status-renamed"; hint = "Đã đổi tên"; }
                        case ColumnDesignViewModel.STATUS_DELETED -> { literal = "fth-trash-2"; styleClass = "td-status-deleted"; hint = "Sẽ bị xoá"; }
                        default -> { }
                    }
                }
                if (literal == null) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    statusIcon.setIconLiteral(literal);
                    getStyleClass().add(styleClass);
                    setGraphic(statusIcon);
                    setTooltip(new Tooltip(hint));
                }
            }
        });

        // 2. PK Column (34px)
        TableColumn<ColumnDesignViewModel, Boolean> colPk = new TableColumn<>("PK");
        colPk.setMinWidth(app.ui.components.UiMetrics.em(2.62));
        colPk.setMaxWidth(app.ui.components.UiMetrics.em(3.08));
        colPk.setPrefWidth(app.ui.components.UiMetrics.em(2.62));
        colPk.setResizable(false);
        colPk.setSortable(false);
        colPk.setCellValueFactory(cell -> cell.getValue().primaryKeyProperty());
        colPk.setCellFactory(col -> new TableCell<>() {
            private final FontIcon icon = app.ui.components.Icons.of("mdi2k-key", app.ui.components.IconScale.MD);
            {
                setAlignment(Pos.CENTER);
                setOnMouseClicked(e -> {
                    if (!isEmpty() && getTableRow() != null) {
                        ColumnDesignViewModel item = getTableRow().getItem();
                        if (item != null) {
                            boolean nextVal = !item.isPrimaryKey();
                            executePropertyChange("Đặt khóa chính", item, () -> item.setPrimaryKey(nextVal), () -> item.setPrimaryKey(!nextVal));
                        }
                    }
                });
            }
            @Override
            protected void updateItem(Boolean pk, boolean empty) {
                super.updateItem(pk, empty);
                if (empty || pk == null || !pk) {
                    setGraphic(null);
                } else {
                    icon.getStyleClass().add("td-pk-icon-active");
                    setGraphic(icon);
                }
            }
        });

        // 3. Name Column (22%)
        TableColumn<ColumnDesignViewModel, String> colName = new TableColumn<>("Tên cột");
        colName.setMinWidth(app.ui.components.UiMetrics.em(8.46));
        colName.prefWidthProperty().bind(tableView.widthProperty().subtract(75).multiply(0.22));
        colName.setCellValueFactory(cell -> cell.getValue().nameProperty());
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setOnEditCommit(e -> {
            ColumnDesignViewModel row = e.getRowValue();
            String oldVal = e.getOldValue();
            String newVal = e.getNewValue() != null ? e.getNewValue().trim() : "";
            if (!Objects.equals(oldVal, newVal)) {
                executePropertyChange("Đổi tên cột", row, () -> row.setName(newVal), () -> row.setName(oldVal));
            }
        });

        // 4. Data Type Column (16%)
        TableColumn<ColumnDesignViewModel, String> colType = new TableColumn<>("Kiểu dữ liệu");
        colType.setMinWidth(app.ui.components.UiMetrics.em(6.92));
        colType.prefWidthProperty().bind(tableView.widthProperty().subtract(75).multiply(0.16));
        colType.setCellValueFactory(cell -> cell.getValue().dataTypeProperty());
        List<String> types = getSupportedDataTypes();
        colType.setCellFactory(col -> new ComboBoxTableCell(types));
        colType.setOnEditCommit(e -> {
            ColumnDesignViewModel row = e.getRowValue();
            String oldVal = e.getOldValue();
            String newVal = e.getNewValue();
            if (!Objects.equals(oldVal, newVal)) {
                executePropertyChange("Đổi kiểu dữ liệu", row, () -> {
                    row.setDataType(newVal);
                    adjustDefaultLengthAndScale(row, newVal);
                }, () -> {
                    row.setDataType(oldVal);
                });
            }
        });

        // 5. Length Column (9%)
        TableColumn<ColumnDesignViewModel, String> colLen = new TableColumn<>("Độ dài / Precision");
        colLen.setMinWidth(app.ui.components.UiMetrics.em(5.38));
        colLen.prefWidthProperty().bind(tableView.widthProperty().subtract(75).multiply(0.09));
        colLen.setCellValueFactory(cell -> {
            Integer l = cell.getValue().getLength();
            return Bindings.createStringBinding(() -> l == null ? "" : (l == -1 ? "max" : String.valueOf(l)));
        });
        colLen.setCellFactory(TextFieldTableCell.forTableColumn());
        colLen.setOnEditCommit(e -> {
            ColumnDesignViewModel row = e.getRowValue();
            Integer oldVal = row.getLength();
            String input = e.getNewValue() != null ? e.getNewValue().trim() : "";
            Integer newVal = null;
            if ("max".equalsIgnoreCase(input) || "-1".equals(input)) {
                newVal = -1;
            } else if (!input.isEmpty()) {
                try {
                    newVal = Integer.parseInt(input);
                } catch (NumberFormatException ignored) {}
            }
            final Integer resolvedVal = newVal;
            if (!Objects.equals(oldVal, resolvedVal)) {
                executePropertyChange("Đổi độ dài", row, () -> row.setLength(resolvedVal), () -> row.setLength(oldVal));
            }
        });

        // 6. Scale Column (7%)
        TableColumn<ColumnDesignViewModel, String> colScale = new TableColumn<>("Scale");
        colScale.setMinWidth(app.ui.components.UiMetrics.em(3.46));
        colScale.prefWidthProperty().bind(tableView.widthProperty().subtract(75).multiply(0.07));
        colScale.setCellValueFactory(cell -> {
            Integer s = cell.getValue().getScale();
            return Bindings.createStringBinding(() -> s == null ? "" : String.valueOf(s));
        });
        colScale.setCellFactory(TextFieldTableCell.forTableColumn());
        colScale.setOnEditCommit(e -> {
            ColumnDesignViewModel row = e.getRowValue();
            Integer oldVal = row.getScale();
            String input = e.getNewValue() != null ? e.getNewValue().trim() : "";
            Integer newVal = null;
            if (!input.isEmpty()) {
                try {
                    newVal = Integer.parseInt(input);
                } catch (NumberFormatException ignored) {}
            }
            final Integer resolvedVal = newVal;
            if (!Objects.equals(oldVal, resolvedVal)) {
                executePropertyChange("Đổi scale", row, () -> row.setScale(resolvedVal), () -> row.setScale(oldVal));
            }
        });

        // 7. Not Null Column (8%)
        TableColumn<ColumnDesignViewModel, Boolean> colNn = new TableColumn<>("Not Null");
        colNn.setMinWidth(app.ui.components.UiMetrics.em(4.23));
        colNn.prefWidthProperty().bind(tableView.widthProperty().subtract(75).multiply(0.08));
        colNn.setCellValueFactory(cell -> cell.getValue().notNullProperty());
        colNn.setCellFactory(col -> new CheckBoxTableCell<>(idx -> {
            ColumnDesignViewModel row = items.get(idx);
            return row.notNullProperty();
        }));

        // 8. Default Column (16%)
        TableColumn<ColumnDesignViewModel, String> colDef = new TableColumn<>("Mặc định");
        colDef.setMinWidth(app.ui.components.UiMetrics.em(6.92));
        colDef.prefWidthProperty().bind(tableView.widthProperty().subtract(75).multiply(0.16));
        colDef.setCellValueFactory(cell -> cell.getValue().defaultValueProperty());
        colDef.setCellFactory(TextFieldTableCell.forTableColumn());
        colDef.setOnEditCommit(e -> {
            ColumnDesignViewModel row = e.getRowValue();
            String oldVal = e.getOldValue();
            String newVal = e.getNewValue();
            if (!Objects.equals(oldVal, newVal)) {
                executePropertyChange("Đổi giá trị mặc định", row, () -> row.setDefaultValue(newVal), () -> row.setDefaultValue(oldVal));
            }
        });

        // 9. Comment Column (remainder / 22%)
        TableColumn<ColumnDesignViewModel, String> colComment = new TableColumn<>("Mô tả");
        colComment.setMinWidth(app.ui.components.UiMetrics.em(6.92));
        colComment.prefWidthProperty().bind(tableView.widthProperty().subtract(75).multiply(0.22));
        colComment.setCellValueFactory(cell -> cell.getValue().commentProperty());
        colComment.setCellFactory(TextFieldTableCell.forTableColumn());
        colComment.setOnEditCommit(e -> {
            ColumnDesignViewModel row = e.getRowValue();
            String oldVal = e.getOldValue();
            String newVal = e.getNewValue();
            if (!Objects.equals(oldVal, newVal)) {
                executePropertyChange("Đổi mô tả", row, () -> row.setComment(newVal), () -> row.setComment(oldVal));
            }
        });

        tableView.getColumns().addAll(colStatus, colPk, colName, colType, colLen, colScale, colNn, colDef, colComment);

        // Row Strikethrough style for deleted rows
        tableView.setRowFactory(tv -> {
            TableRow<ColumnDesignViewModel> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("deleted"), newItem.isDeleted());
                    newItem.deletedProperty().addListener((o, ov, nv) ->
                            row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("deleted"), Boolean.TRUE.equals(nv)));
                }
            });
            return row;
        });

        // Context Menu
        setupContextMenu();

        // Keyboard Handlers
        setupKeyHandlers();
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem mAdd = new MenuItem("Thêm dòng ở cuối (Ctrl+N)");
        mAdd.setOnAction(e -> addNewRowAtEnd());

        MenuItem mInsAbove = new MenuItem("Chèn dòng phía trên (Ctrl+Insert)");
        mInsAbove.setOnAction(e -> insertRowAbove());

        MenuItem mInsBelow = new MenuItem("Chèn dòng phía dưới (Ctrl+Shift+Insert)");
        mInsBelow.setOnAction(e -> insertRowBelow());

        MenuItem mDup = new MenuItem("Nhân bản dòng (Ctrl+D)");
        mDup.setOnAction(e -> duplicateSelectedRows());

        MenuItem mCopy = new MenuItem("Sao chép (Ctrl+C)");
        mCopy.setOnAction(e -> copySelectedRows());

        MenuItem mCut = new MenuItem("Cắt (Ctrl+X)");
        mCut.setOnAction(e -> cutSelectedRows());

        MenuItem mPaste = new MenuItem("Dán (Ctrl+V)");
        mPaste.setOnAction(e -> pasteRows());

        MenuItem mDel = new MenuItem("Xoá");
        mDel.setOnAction(e -> deleteSelectedRows());

        MenuItem mPk = new MenuItem("Đặt / bỏ khoá chính");
        mPk.setOnAction(e -> togglePrimaryKeyOnSelected());

        MenuItem mNn = new MenuItem("Bật / tắt Not Null");
        mNn.setOnAction(e -> toggleNotNullOnSelected());

        MenuItem mAll = new MenuItem("Chọn tất cả (Ctrl+A)");
        mAll.setOnAction(e -> selectAllRows());

        menu.getItems().addAll(
                mAdd, mInsAbove, mInsBelow, new SeparatorMenuItem(),
                mDup, mCopy, mCut, mPaste, new SeparatorMenuItem(),
                mDel, new SeparatorMenuItem(),
                mPk, mNn, new SeparatorMenuItem(),
                mAll
        );

        tableView.setContextMenu(menu);
    }

    private void setupKeyHandlers() {
        tableView.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.N) {
                addNewRowAtEnd();
                e.consume();
            } else if (e.isControlDown() && !e.isShiftDown() && e.getCode() == KeyCode.INSERT) {
                insertRowAbove();
                e.consume();
            } else if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.INSERT) {
                insertRowBelow();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.D) {
                duplicateSelectedRows();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.C) {
                copySelectedRows();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.X) {
                cutSelectedRows();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.V) {
                pasteRows();
                e.consume();
            } else if (e.isAltDown() && e.getCode() == KeyCode.UP) {
                moveSelectedRowsUp();
                e.consume();
            } else if (e.isAltDown() && e.getCode() == KeyCode.DOWN) {
                moveSelectedRowsDown();
                e.consume();
            } else if (e.isAltDown() && e.getCode() == KeyCode.HOME) {
                moveSelectedRowsToTop();
                e.consume();
            } else if (e.isAltDown() && e.getCode() == KeyCode.END) {
                moveSelectedRowsToBottom();
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE) {
                deleteSelectedRows();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.A) {
                selectAllRows();
                e.consume();
            } else if (e.getCode() == KeyCode.SPACE) {
                toggleNotNullOnSelected();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                if (undoManager.canUndo()) undoManager.undo();
                notifyChanged();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.Y) {
                if (undoManager.canRedo()) undoManager.redo();
                notifyChanged();
                e.consume();
            }
        });
    }

    private void notifyChanged() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    private void executePropertyChange(String desc, ColumnDesignViewModel row, Runnable doAction, Runnable undoAction) {
        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                doAction.run();
                row.updateStatusMarker();
                notifyChanged();
            }
            @Override
            public void undo() {
                undoAction.run();
                row.updateStatusMarker();
                notifyChanged();
            }
            @Override
            public String description() {
                return desc;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Row Operations (TD-ROW-01 .. TD-ROW-15)
    // -------------------------------------------------------------------------

    /** TD-ROW-01: Thêm dòng ở cuối */
    public void addNewRowAtEnd() {
        String name = generateUniqueColumnName("Column");
        String defaultType = "postgresql".equalsIgnoreCase(engine) ? "varchar" : "varchar";
        ColumnDesignViewModel row = ColumnDesignViewModel.newColumn(name, defaultType);
        row.setLength(50);

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                items.add(row);
                tableView.getSelectionModel().clearAndSelect(items.size() - 1);
                tableView.scrollTo(row);
                notifyChanged();
            }
            @Override
            public void undo() {
                items.remove(row);
                notifyChanged();
            }
            @Override
            public String description() {
                return "Thêm cột " + name;
            }
        });
    }

    /** TD-ROW-02: Chèn dòng phía trên */
    public void insertRowAbove() {
        int idx = tableView.getSelectionModel().getSelectedIndex();
        if (idx < 0) idx = 0;
        final int targetIdx = idx;

        String name = generateUniqueColumnName("Column");
        ColumnDesignViewModel row = ColumnDesignViewModel.newColumn(name, "varchar");
        row.setLength(50);

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                items.add(targetIdx, row);
                tableView.getSelectionModel().clearAndSelect(targetIdx);
                notifyChanged();
            }
            @Override
            public void undo() {
                items.remove(row);
                notifyChanged();
            }
            @Override
            public String description() {
                return "Chèn cột phía trên";
            }
        });
    }

    /** TD-ROW-03: Chèn dòng phía dưới */
    public void insertRowBelow() {
        int idx = tableView.getSelectionModel().getSelectedIndex();
        if (idx < 0) idx = items.size() - 1;
        final int targetIdx = Math.min(items.size(), idx + 1);

        String name = generateUniqueColumnName("Column");
        ColumnDesignViewModel row = ColumnDesignViewModel.newColumn(name, "varchar");
        row.setLength(50);

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                items.add(targetIdx, row);
                tableView.getSelectionModel().clearAndSelect(targetIdx);
                notifyChanged();
            }
            @Override
            public void undo() {
                items.remove(row);
                notifyChanged();
            }
            @Override
            public String description() {
                return "Chèn cột phía dưới";
            }
        });
    }

    /** TD-ROW-04: Thêm nhiều dòng (1-100) */
    public void addMultipleRows(int count) {
        if (count <= 0) return;
        List<ColumnDesignViewModel> newRows = new ArrayList<>();
        int insertPos = tableView.getSelectionModel().getSelectedIndex();
        if (insertPos < 0) insertPos = items.size();
        else insertPos++;
        final int startPos = insertPos;

        for (int i = 0; i < count; i++) {
            String name = generateUniqueColumnName("Column");
            ColumnDesignViewModel row = ColumnDesignViewModel.newColumn(name, "varchar");
            row.setLength(50);
            newRows.add(row);
        }

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                items.addAll(startPos, newRows);
                tableView.getSelectionModel().clearSelection();
                for (int i = 0; i < newRows.size(); i++) {
                    tableView.getSelectionModel().select(startPos + i);
                }
                notifyChanged();
            }
            @Override
            public void undo() {
                items.removeAll(newRows);
                notifyChanged();
            }
            @Override
            public String description() {
                return "Thêm " + count + " cột";
            }
        });
    }

    /** TD-ROW-05: Nhân bản dòng (Ctrl+D) - Sửa lỗi B1 */
    public void duplicateSelectedRows() {
        List<ColumnDesignViewModel> selected = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        int lastIdx = -1;
        for (ColumnDesignViewModel s : selected) {
            int idx = items.indexOf(s);
            if (idx > lastIdx) lastIdx = idx;
        }
        final int insertAt = lastIdx + 1;

        List<ColumnDesignViewModel> duplicates = new ArrayList<>();
        for (ColumnDesignViewModel src : selected) {
            String newName = generateUniqueColumnName(src.getName() + "_copy");
            ColumnDesignViewModel dup = src.duplicate(newName);
            duplicates.add(dup);
        }

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                items.addAll(insertAt, duplicates);
                tableView.getSelectionModel().clearSelection();
                for (int i = 0; i < duplicates.size(); i++) {
                    tableView.getSelectionModel().select(insertAt + i);
                }
                notifyChanged();
            }
            @Override
            public void undo() {
                items.removeAll(duplicates);
                notifyChanged();
            }
            @Override
            public String description() {
                return "Nhân bản " + duplicates.size() + " cột";
            }
        });
    }

    /** TD-ROW-06: Sao chép / Cắt (Ctrl+C / Ctrl+X) */
    public void copySelectedRows() {
        List<ColumnDesignViewModel> selected = tableView.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;

        internalClipboard.clear();
        internalClipboard.addAll(selected);

        // Put TSV on system clipboard
        StringBuilder tsv = new StringBuilder();
        tsv.append("Name\tDataType\tLength\tScale\tNotNull\tDefault\tComment\n");
        for (ColumnDesignViewModel col : selected) {
            tsv.append(col.getName()).append("\t")
                    .append(col.getDataType()).append("\t")
                    .append(col.getLength() != null ? col.getLength() : "").append("\t")
                    .append(col.getScale() != null ? col.getScale() : "").append("\t")
                    .append(col.isNotNull()).append("\t")
                    .append(col.getDefaultValue() != null ? col.getDefaultValue() : "").append("\t")
                    .append(col.getComment() != null ? col.getComment() : "").append("\n");
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(tsv.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    public void cutSelectedRows() {
        copySelectedRows();
        deleteSelectedRows();
    }

    /** TD-ROW-07: Dán dòng (Ctrl+V) - Sửa lỗi B14 */
    public void pasteRows() {
        List<ColumnDesignViewModel> toPaste = new ArrayList<>();

        // Check system clipboard for TSV
        String tsv = Clipboard.getSystemClipboard().getString();
        if (tsv != null && !tsv.isBlank() && tsv.contains("\t")) {
            String[] lines = tsv.split("\r?\n");
            int startLine = (lines[0].toLowerCase().contains("name") && lines[0].toLowerCase().contains("datatype")) ? 1 : 0;
            for (int i = startLine; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t", -1);
                String name = generateUniqueColumnName(parts.length > 0 && !parts[0].isBlank() ? parts[0] : "Column");
                String type = parts.length > 1 && !parts[1].isBlank() ? parts[1].toLowerCase() : "varchar";
                ColumnDesignViewModel row = ColumnDesignViewModel.newColumn(name, type);
                if (parts.length > 2 && !parts[2].isBlank()) {
                    try { row.setLength(Integer.parseInt(parts[2])); } catch (Exception ignored) {}
                }
                if (parts.length > 3 && !parts[3].isBlank()) {
                    try { row.setScale(Integer.parseInt(parts[3])); } catch (Exception ignored) {}
                }
                if (parts.length > 4) {
                    row.setNotNull("true".equalsIgnoreCase(parts[4]) || "1".equals(parts[4]));
                }
                if (parts.length > 5 && !parts[5].isBlank()) {
                    row.setDefaultValue(parts[5]);
                }
                if (parts.length > 6 && !parts[6].isBlank()) {
                    row.setComment(parts[6]);
                }
                toPaste.add(row);
            }
        } else if (!internalClipboard.isEmpty()) {
            for (ColumnDesignViewModel src : internalClipboard) {
                String name = generateUniqueColumnName(src.getName() + "_copy");
                toPaste.add(src.duplicate(name));
            }
        }

        if (toPaste.isEmpty()) return;

        int insertPos = tableView.getSelectionModel().getSelectedIndex();
        if (insertPos < 0) insertPos = items.size();
        else insertPos++;
        final int targetPos = insertPos;

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                items.addAll(targetPos, toPaste);
                tableView.getSelectionModel().clearSelection();
                for (int i = 0; i < toPaste.size(); i++) {
                    tableView.getSelectionModel().select(targetPos + i);
                }
                notifyChanged();
            }
            @Override
            public void undo() {
                items.removeAll(toPaste);
                notifyChanged();
            }
            @Override
            public String description() {
                return "Dán " + toPaste.size() + " cột";
            }
        });
    }

    /** TD-ROW-08: Di chuyển lên / xuống (Alt+↑ / Alt+↓) */
    public void moveSelectedRowsUp() {
        List<Integer> indices = new ArrayList<>(tableView.getSelectionModel().getSelectedIndices());
        if (indices.isEmpty() || indices.contains(0)) return; // Already at top
        Collections.sort(indices);

        List<ColumnDesignViewModel> moved = new ArrayList<>();
        for (int idx : indices) {
            moved.add(items.get(idx));
        }

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                for (int idx : indices) {
                    ColumnDesignViewModel item = items.remove(idx);
                    items.add(idx - 1, item);
                }
                tableView.getSelectionModel().clearSelection();
                for (ColumnDesignViewModel m : moved) {
                    tableView.getSelectionModel().select(m);
                }
                notifyChanged();
            }
            @Override
            public void undo() {
                for (int i = indices.size() - 1; i >= 0; i--) {
                    int originalIdx = indices.get(i);
                    ColumnDesignViewModel item = items.remove(originalIdx - 1);
                    items.add(originalIdx, item);
                }
                tableView.getSelectionModel().clearSelection();
                for (ColumnDesignViewModel m : moved) {
                    tableView.getSelectionModel().select(m);
                }
                notifyChanged();
            }
            @Override
            public String description() {
                return "Di chuyển cột lên";
            }
        });
    }

    public void moveSelectedRowsDown() {
        List<Integer> indices = new ArrayList<>(tableView.getSelectionModel().getSelectedIndices());
        if (indices.isEmpty() || indices.contains(items.size() - 1)) return; // Already at bottom
        indices.sort(Collections.reverseOrder());

        List<ColumnDesignViewModel> moved = new ArrayList<>();
        for (int idx : indices) {
            moved.add(items.get(idx));
        }

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                for (int idx : indices) {
                    ColumnDesignViewModel item = items.remove(idx);
                    items.add(idx + 1, item);
                }
                tableView.getSelectionModel().clearSelection();
                for (ColumnDesignViewModel m : moved) {
                    tableView.getSelectionModel().select(m);
                }
                notifyChanged();
            }
            @Override
            public void undo() {
                for (int i = indices.size() - 1; i >= 0; i--) {
                    int originalIdx = indices.get(i);
                    ColumnDesignViewModel item = items.remove(originalIdx + 1);
                    items.add(originalIdx, item);
                }
                tableView.getSelectionModel().clearSelection();
                for (ColumnDesignViewModel m : moved) {
                    tableView.getSelectionModel().select(m);
                }
                notifyChanged();
            }
            @Override
            public String description() {
                return "Di chuyển cột xuống";
            }
        });
    }

    /** TD-ROW-09: Lên đầu / Xuống cuối (Alt+Home / Alt+End) */
    public void moveSelectedRowsToTop() {
        List<ColumnDesignViewModel> selected = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        List<ColumnDesignViewModel> previousOrder = new ArrayList<>(items);

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                items.removeAll(selected);
                items.addAll(0, selected);
                tableView.getSelectionModel().clearSelection();
                for (ColumnDesignViewModel s : selected) {
                    tableView.getSelectionModel().select(s);
                }
                notifyChanged();
            }
            @Override
            public void undo() {
                items.setAll(previousOrder);
                tableView.getSelectionModel().clearSelection();
                for (ColumnDesignViewModel s : selected) {
                    tableView.getSelectionModel().select(s);
                }
                notifyChanged();
            }
            @Override
            public String description() {
                return "Đưa lên đầu bảng";
            }
        });
    }

    public void moveSelectedRowsToBottom() {
        List<ColumnDesignViewModel> selected = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        List<ColumnDesignViewModel> previousOrder = new ArrayList<>(items);

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                items.removeAll(selected);
                items.addAll(selected);
                tableView.getSelectionModel().clearSelection();
                for (ColumnDesignViewModel s : selected) {
                    tableView.getSelectionModel().select(s);
                }
                notifyChanged();
            }
            @Override
            public void undo() {
                items.setAll(previousOrder);
                tableView.getSelectionModel().clearSelection();
                for (ColumnDesignViewModel s : selected) {
                    tableView.getSelectionModel().select(s);
                }
                notifyChanged();
            }
            @Override
            public String description() {
                return "Đưa xuống cuối bảng";
            }
        });
    }

    /** TD-ROW-11: Xóa dòng (Delete) */
    public void deleteSelectedRows() {
        List<ColumnDesignViewModel> selected = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        if (isNewTable) {
            // New table: directly remove from list
            Map<Integer, ColumnDesignViewModel> removed = new LinkedHashMap<>();
            for (ColumnDesignViewModel s : selected) {
                removed.put(items.indexOf(s), s);
            }

            undoManager.execute(new TableDesignerUndoManager.Command() {
                @Override
                public void execute() {
                    items.removeAll(selected);
                    notifyChanged();
                }
                @Override
                public void undo() {
                    removed.forEach((idx, col) -> {
                        if (idx >= 0 && idx <= items.size()) {
                            items.add(idx, col);
                        } else {
                            items.add(col);
                        }
                    });
                    notifyChanged();
                }
                @Override
                public String description() {
                    return "Xóa " + selected.size() + " cột";
                }
            });
        } else {
            // Edit table: toggle deleted flag (strikethrough)
            undoManager.execute(new TableDesignerUndoManager.Command() {
                @Override
                public void execute() {
                    for (ColumnDesignViewModel col : selected) {
                        col.setDeleted(!col.isDeleted());
                    }
                    notifyChanged();
                }
                @Override
                public void undo() {
                    for (ColumnDesignViewModel col : selected) {
                        col.setDeleted(!col.isDeleted());
                    }
                    notifyChanged();
                }
                @Override
                public String description() {
                    return "Đánh dấu xóa " + selected.size() + " cột";
                }
            });
        }
    }

    /** TD-ROW-12: Chọn tất cả (Ctrl+A) - Sửa lỗi B14 */
    public void selectAllRows() {
        tableView.getSelectionModel().selectAll();
    }

    /** TD-ROW-13: Đặt / bỏ Khóa chính */
    public void togglePrimaryKeyOnSelected() {
        List<ColumnDesignViewModel> selected = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        boolean allPk = selected.stream().allMatch(ColumnDesignViewModel::isPrimaryKey);
        boolean target = !allPk;

        Map<ColumnDesignViewModel, Boolean> prevStates = new HashMap<>();
        for (ColumnDesignViewModel s : selected) {
            prevStates.put(s, s.isPrimaryKey());
        }

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                for (ColumnDesignViewModel s : selected) {
                    s.setPrimaryKey(target);
                }
                notifyChanged();
            }
            @Override
            public void undo() {
                prevStates.forEach(ColumnDesignViewModel::setPrimaryKey);
                notifyChanged();
            }
            @Override
            public String description() {
                return "Đổi khóa chính";
            }
        });
    }

    /** TD-ROW-14: Bật / tắt Not Null (Space) */
    public void toggleNotNullOnSelected() {
        List<ColumnDesignViewModel> selected = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        boolean allNn = selected.stream().allMatch(ColumnDesignViewModel::isNotNull);
        boolean target = !allNn;

        Map<ColumnDesignViewModel, Boolean> prevStates = new HashMap<>();
        for (ColumnDesignViewModel s : selected) {
            prevStates.put(s, s.isNotNull());
        }

        undoManager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                for (ColumnDesignViewModel s : selected) {
                    if (!s.isPrimaryKey()) { // Primary key must stay Not Null
                        s.setNotNull(target);
                    }
                }
                notifyChanged();
            }
            @Override
            public void undo() {
                prevStates.forEach(ColumnDesignViewModel::setNotNull);
                notifyChanged();
            }
            @Override
            public String description() {
                return "Đổi Not Null";
            }
        });
    }

    private void applyFilterChangedOnly(boolean changedOnly) {
        // Filter view logic
        if (!changedOnly) {
            tableView.setItems(items);
        } else {
            ObservableList<ColumnDesignViewModel> filtered = items.filtered(ColumnDesignViewModel::isDirty);
            tableView.setItems(filtered);
        }
    }

    private String generateUniqueColumnName(String prefix) {
        Set<String> existing = items.stream()
                .map(col -> col.getName().toLowerCase())
                .collect(Collectors.toSet());
        String name = prefix;
        int counter = 1;
        while (existing.contains(name.toLowerCase())) {
            name = prefix + "_" + counter++;
        }
        return name;
    }

    private List<String> getSupportedDataTypes() {
        if ("postgresql".equalsIgnoreCase(engine)) {
            return List.of(
                    "serial", "bigserial", "smallserial",
                    "integer", "bigint", "smallint",
                    "varchar", "char", "text",
                    "numeric", "decimal", "real", "double precision",
                    "boolean", "timestamp", "timestamptz", "date", "time", "timetz",
                    "uuid", "json", "jsonb", "bytea"
            );
        } else {
            return List.of(
                    "int", "bigint", "smallint", "tinyint",
                    "varchar", "nvarchar", "char", "nchar",
                    "decimal", "numeric", "float", "real", "bit",
                    "datetime2", "datetime", "date", "time",
                    "uniqueidentifier", "varbinary", "binary",
                    "text", "ntext", "image", "xml"
            );
        }
    }

    private void adjustDefaultLengthAndScale(ColumnDesignViewModel row, String dataType) {
        if (dataType == null) return;
        String t = dataType.toLowerCase();
        if (t.contains("varchar") || t.contains("nvarchar") || t.contains("char")) {
            if (row.getLength() == null) row.setLength(50);
        } else if (t.equals("decimal") || t.equals("numeric")) {
            if (row.getLength() == null) row.setLength(18);
            if (row.getScale() == null) row.setScale(2);
        } else if (t.equals("int") || t.equals("integer") || t.equals("bigint") || t.equals("serial")
                || t.equals("bit") || t.equals("boolean") || t.equals("date")) {
            row.setLength(null);
            row.setScale(null);
        }
    }

    // -------------------------------------------------------------------------
    // Custom TableCell with ComboBox
    // -------------------------------------------------------------------------
    private static class ComboBoxTableCell extends TableCell<ColumnDesignViewModel, String> {
        private final ComboBox<String> comboBox;

        public ComboBoxTableCell(List<String> items) {
            comboBox = new ComboBox<>(FXCollections.observableArrayList(items));
            comboBox.setEditable(true);
            comboBox.setMaxWidth(Double.MAX_VALUE);
            comboBox.setOnAction(e -> {
                if (isEditing()) {
                    commitEdit(comboBox.getValue());
                }
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            comboBox.setValue(getItem());
            setGraphic(comboBox);
            setText(null);
            Platform.runLater(comboBox::requestFocus);
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else if (isEditing()) {
                comboBox.setValue(item);
                setGraphic(comboBox);
                setText(null);
            } else {
                setText(item);
                setGraphic(null);
            }
        }
    }
}
