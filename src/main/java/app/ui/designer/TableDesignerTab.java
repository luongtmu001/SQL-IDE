package app.ui.designer;

import app.controllers.EditorController;
import app.framework.backend.BackendFlags;
import app.framework.designer.TableDesignerService;
import app.framework.metadata.DesktopMetadataService;
import app.services.NotificationService;
import app.ui.components.Badge;
import app.ui.components.EmptyState;
import app.ui.components.Icons;
import app.ui.components.IconScale;
import app.ui.components.SegmentedToggle;
import app.ui.components.WorkspaceFooter;
import app.ui.dialogs.SaveDesignDialog;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.tabledesigner.TableDesignValidator;
import com.lltool.core.application.tabledesigner.dialect.DdlDialect;
import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;
import com.lltool.core.common.validation.ValidationResult;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tabledesigner.model.TriggerDesign;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Main Table Designer Tab component (SCR-TD-001 / TD3).
 * Integrates into the EditorTabStrip, supporting New Table, Edit Table, and Copy Table workflows.
 */
public class TableDesignerTab extends SqlEditorTab {

    private static final Logger log = LoggerFactory.getLogger(TableDesignerTab.class);

    private ConnectionProfileDto profile;
    private final String catalog;
    private String initialSchema;
    private String initialTableName;
    private boolean isNewTable;
    private final String engine;
    private TableDesign initialClonedDesign;
    private final app.framework.async.LoadTask<Void> applyMigrationTask = new app.framework.async.LoadTask<>();

    private final TableDesignerUndoManager undoManager = new TableDesignerUndoManager();
    private final TableDesignValidator validator = new TableDesignValidator();
    private final TableDesignerService designerService = TableDesignerService.getInstance();

    private TableDesign originalDesign;
    private TableDesign currentDesign;

    // UI Layout Components
    private final VBox rootContainer = new VBox();
    private final ComboBox<String> cbSchema = new ComboBox<>();
    private final TextField txtTableName = new TextField();
    private final Button btnSave = new Button("Lưu (Ctrl+S)");
    private final Button btnOpenSql = new Button("Mở trong tab SQL");
    private final Button btnUndo = new Button();
    private final Button btnRedo = new Button();
    private final Button btnRefresh = new Button();
    private final ToggleButton btnToggleInspector = new ToggleButton();
    private final ToggleButton btnToggleDdl = new ToggleButton();
    private final app.ui.components.IconButton btnToggleWindow = new app.ui.components.IconButton("fth-external-link", "Mở dạng cửa sổ riêng");

    private ColumnGrid columnGrid;
    private IndexGrid indexGrid;
    private ForeignKeyGrid foreignKeyGrid;
    private UniqueConstraintGrid uniqueGrid;
    private CheckConstraintGrid checkGrid;
    private VBox triggerPane;

    private ColumnInspector columnInspector;
    private DdlPreviewPane ddlPreviewPane;

    private SplitPane mainSplit;
    private SplitPane leftSplit;

    private final Label lblSummary = new Label();
    private final Badge badgeValidation = new Badge("Hợp lệ", Badge.Variant.SUCCESS);
    private SegmentedToggle<String> subTabToggle;
    private final WorkspaceFooter workspaceFooter = new WorkspaceFooter();

    // Debounce executor for Live DDL generation
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "td-ddl-debounce");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingDdlTask;

    public TableDesignerTab(ConnectionProfileDto profile, String catalog, String schema, String tableName,
                            boolean isNewTable, Consumer<SqlEditorTabAction> actionHandler) {
        this(profile, catalog, schema, tableName, isNewTable, null, actionHandler);
    }

    public TableDesignerTab(ConnectionProfileDto profile, String catalog, String schema, String tableName,
                            TableDesign initialClonedDesign, Consumer<SqlEditorTabAction> actionHandler) {
        this(profile, catalog,
                (initialClonedDesign != null && initialClonedDesign.getSchema() != null) ? initialClonedDesign.getSchema() : schema,
                (initialClonedDesign != null && initialClonedDesign.getTable() != null) ? initialClonedDesign.getTable() : tableName,
                true, initialClonedDesign, actionHandler);
    }

    private TableDesignerTab(ConnectionProfileDto profile, String catalog, String schema, String tableName,
                             boolean isNewTable, TableDesign initialClonedDesign, Consumer<SqlEditorTabAction> actionHandler) {
        super(
                buildTabTitle(initialClonedDesign != null ? initialClonedDesign.getTable() : tableName, isNewTable),
                "",
                null,
                profile != null ? profile.name() : "",
                catalog,
                schema,
                actionHandler
        );

        this.profile = profile;
        this.catalog = catalog;
        this.initialClonedDesign = initialClonedDesign;
        this.initialSchema = (schema != null && !schema.isBlank()) ? schema :
                (profile != null && profile.databaseType() != null && profile.databaseType().toLowerCase().contains("postgre") ? "public" : "dbo");
        this.initialTableName = (tableName != null && !tableName.isBlank()) ? tableName : "Table_1";
        this.isNewTable = isNewTable;
        this.engine = (profile != null && profile.databaseType() != null && profile.databaseType().toLowerCase().contains("postgre")) ? "postgresql" : "sqlserver";

        // Tab Icon: mdi2t-table-multiple for copy, mdi2t-table-plus for new table, mdi2t-table-edit for existing table
        if (initialClonedDesign != null) {
            setFileIconLiteral("mdi2t-table-multiple");
        } else {
            setFileIconLiteral(isNewTable ? "mdi2t-table-plus" : "mdi2t-table-edit");
        }
        setTabContentType(TabContentType.TABLE_DESIGNER);

        initializeDesigner();
    }

    private static String buildTabTitle(String tableName, boolean isNewTable) {
        if (isNewTable) {
            return (tableName != null && !tableName.isBlank()) ? tableName : "Table_1";
        }
        return tableName != null ? tableName : "Table";
    }

    private void initializeDesigner() {
        if (profile != null) {
            app.framework.state.GlobalDesktopState.getInstance().setActiveConnection(profile.name());
            if (catalog != null && !catalog.isBlank()) {
                app.framework.state.GlobalDesktopState.getInstance().setActiveCatalog(catalog);
            }
            if (initialSchema != null && !initialSchema.isBlank()) {
                app.framework.state.GlobalDesktopState.getInstance().setActiveSchema(initialSchema);
            }
        }
        rootContainer.getStyleClass().addAll("table-designer-root", "workspace-root");
        var cssRes = getClass().getResource("/css/table-designer.css");
        if (cssRes != null) {
            rootContainer.getStylesheets().add(cssRes.toExternalForm());
        }

        // 1. Header Toolbar
        HBox headerToolbar = buildHeaderToolbar();

        // 2. Sub-tab Bar
        HBox subTabBar = buildSubTabBar();

        // 3. Main Workspace
        columnGrid = new ColumnGrid(engine, isNewTable, undoManager, this::onDesignChanged);
        indexGrid = new IndexGrid(engine, this::onDesignChanged);
        foreignKeyGrid = new ForeignKeyGrid(engine, this::onDesignChanged);
        uniqueGrid = new UniqueConstraintGrid(engine, this::onDesignChanged);
        checkGrid = new CheckConstraintGrid(engine, this::onDesignChanged);
        triggerPane = buildTriggerPane();

        columnInspector = new ColumnInspector(engine, this::onDesignChanged);
        ddlPreviewPane = new DdlPreviewPane();

        // Connect Grid selection to Inspector
        columnGrid.getTableView().getSelectionModel().selectedItemProperty().addListener((obs, oldCol, newCol) -> {
            columnInspector.setColumn(newCol);
        });

        leftSplit = new SplitPane();
        leftSplit.setOrientation(Orientation.VERTICAL);
        leftSplit.getItems().addAll(columnGrid, ddlPreviewPane);
        leftSplit.setDividerPositions(0.72);

        mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.getItems().addAll(leftSplit, columnInspector);
        mainSplit.setDividerPositions(0.78);
        VBox.setVgrow(mainSplit, Priority.ALWAYS);

        // 4. Footer Status Bar
        HBox footer = buildFooter();

        rootContainer.getChildren().addAll(headerToolbar, subTabBar, mainSplit, footer);

        // Set as custom content of SqlEditorTab
        String tabKey = "table_designer_" + (initialClonedDesign != null ? "copy_" : (isNewTable ? "new_" : "edit_")) + catalog + "_" + initialSchema + "_" + initialTableName;
        setCustomContent(tabKey, rootContainer);

        btnToggleWindow.setOnAction(e -> app.ui.window.FeatureWindowManager.getInstance().toggleWindow(
                this, rootContainer, btnToggleWindow, getTabTitle(), "fth-edit-3"));

        // Load or create initial design
        loadInitialData();

        // Bind Undo/Redo button states
        btnUndo.disableProperty().bind(undoManager.canUndoProperty().not());
        btnRedo.disableProperty().bind(undoManager.canRedoProperty().not());

        // Keyboard handler for Save (Ctrl+S), Undo (Ctrl+Z), Redo (Ctrl+Y)
        rootContainer.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getTarget() instanceof TextInputControl) {
                return;
            }
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.S) {
                handleSave();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.Z) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                    onDesignChanged();
                }
                e.consume();
            } else if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.Y) {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                    onDesignChanged();
                }
                e.consume();
            }
        });
        // Clean up debounce executor when tab is closed
        setOnClosed(e -> debounceExecutor.shutdownNow());
    }

    private HBox buildHeaderToolbar() {
        // WorkspaceToolbar: labels are never cut; when the tab is narrow, low-priority buttons move to "…"
        app.ui.components.WorkspaceToolbar bar = new app.ui.components.WorkspaceToolbar();
        bar.getStyleClass().add("td-header-toolbar");

        Label lblSchema = new Label("Schema:");
        cbSchema.setEditable(true);
        cbSchema.setPrefWidth(app.ui.components.UiMetrics.em(9.23));
        cbSchema.setValue(initialSchema);
        cbSchema.setOnAction(e -> onDesignChanged());

        Label lblTable = new Label("Tên bảng:");
        txtTableName.setText(initialTableName);
        txtTableName.setPrefWidth(app.ui.components.UiMetrics.em(13.85));
        txtTableName.textProperty().addListener((obs, o, n) -> {
            setTabTitle(n != null && !n.isBlank() ? n : "Table");
            onDesignChanged();
        });

        // Save Button
        btnSave.getStyleClass().addAll("button", "accent");
        btnSave.setGraphic(Icons.of("fth-save"));
        btnSave.setOnAction(e -> handleSave());

        // Guardrail: if Read-Only, prompt user to switch to writable when saving
        boolean readOnly = BackendFlags.readOnlyAll() || (profile != null && profile.readOnly());
        if (readOnly) {
            btnSave.setTooltip(new Tooltip("Kết nối đang ở chế độ CHỈ ĐỌC (Bấm để chuyển sang cho phép ghi)"));
        } else {
            btnSave.setTooltip(new Tooltip("Lưu thay đổi vào CSDL (Ctrl+S)"));
        }

        // Open in SQL Tab Button
        btnOpenSql.getStyleClass().addAll("button");
        btnOpenSql.setGraphic(Icons.of("fth-external-link"));
        btnOpenSql.setTooltip(new Tooltip("Mở DDL / Migration script trong tab SQL"));
        btnOpenSql.setOnAction(e -> handleOpenInSqlTab());

        // Undo & Redo Buttons
        btnUndo.getStyleClass().addAll("button");
        btnUndo.setGraphic(Icons.of("fth-rotate-ccw"));
        btnUndo.setTooltip(new Tooltip("Hoàn tác (Ctrl+Z)"));
        btnUndo.setOnAction(e -> {
            if (undoManager.canUndo()) undoManager.undo();
            onDesignChanged();
        });

        btnRedo.getStyleClass().addAll("button");
        btnRedo.setGraphic(Icons.of("fth-rotate-cw"));
        btnRedo.setTooltip(new Tooltip("Làm lại (Ctrl+Y)"));
        btnRedo.setOnAction(e -> {
            if (undoManager.canRedo()) undoManager.redo();
            onDesignChanged();
        });

        // Refresh Button
        btnRefresh.getStyleClass().addAll("button");
        btnRefresh.setGraphic(Icons.of("fth-refresh-cw"));
        btnRefresh.setTooltip(new Tooltip("Tải lại từ CSDL (F5)"));
        btnRefresh.setOnAction(e -> {
            if (dirtyProperty().get()) {
                var stage = app.application.AppContext.getInstance().getPrimaryStage();
                Window owner = stage != null && stage.getScene() != null ? stage.getScene().getWindow() : null;
                boolean discard = app.ui.dialogs.ConfirmDialog.show(
                        owner,
                        "Bỏ thay đổi chưa lưu?",
                        "Bảng đang có thay đổi chưa lưu.",
                        "Bạn có chắc chắn muốn tải lại từ CSDL và hủy mọi thay đổi hiện tại không?",
                        "Bỏ thay đổi",
                        true
                );
                if (!discard) return;
            }
            undoManager.clear();
            loadInitialData();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Toggle Inspector Button
        btnToggleInspector.getStyleClass().addAll("toggle-button", "toolbar-toggle");
        btnToggleInspector.setSelected(true);
        btnToggleInspector.setGraphic(Icons.of("fth-sidebar"));
        btnToggleInspector.setTooltip(new Tooltip("Ẩn/Hiện Inspector thuộc tính"));
        btnToggleInspector.setOnAction(e -> toggleInspector(btnToggleInspector.isSelected()));

        // Toggle DDL Preview Button
        btnToggleDdl.getStyleClass().addAll("toggle-button", "toolbar-toggle");
        btnToggleDdl.setSelected(true);
        btnToggleDdl.setGraphic(Icons.of("fth-code"));
        btnToggleDdl.setTooltip(new Tooltip("Ẩn/Hiện xem trước DDL"));
        btnToggleDdl.setOnAction(e -> toggleDdlPreview(btnToggleDdl.isSelected()));

        bar.addLeft(lblSchema);
        bar.addLeft(cbSchema);
        bar.addLeft(lblTable);
        bar.addLeft(txtTableName);
        bar.addLeft(btnSave);
        bar.addLeft(btnOpenSql, app.ui.components.WorkspaceToolbar.Priority.LOW);
        bar.addLeft(new Separator(Orientation.VERTICAL));
        bar.addLeft(btnUndo);
        bar.addLeft(btnRedo);
        bar.addLeft(btnRefresh, app.ui.components.WorkspaceToolbar.Priority.LOW);
        bar.addRight(btnToggleDdl);
        bar.addRight(btnToggleInspector);
        bar.addRight(btnToggleWindow);

        return bar;
    }

    public VBox getContentRoot() {
        return rootContainer;
    }

    public app.ui.components.IconButton getToggleWindowButton() {
        return btnToggleWindow;
    }

    private HBox buildSubTabBar() {
        HBox bar = new HBox(4);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(2, 10, 2, 10));
        bar.getStyleClass().add("td-subtab-strip");

        subTabToggle = new SegmentedToggle<>();
        subTabToggle.addItem("col", "Cột", "mdi2t-table-column");
        subTabToggle.addItem("idx", "Chỉ mục", "mdi2b-bookmark-outline");
        subTabToggle.addItem("fk", "Khoá ngoại", "mdi2k-key-link");
        subTabToggle.addItem("uq", "Ràng buộc duy nhất", "mdi2f-fingerprint");
        subTabToggle.addItem("chk", "Ràng buộc kiểm tra", "mdi2c-check-circle-outline");
        subTabToggle.addItem("trig", "Trigger", "mdi2f-flash-outline");

        subTabToggle.select("col");
        subTabToggle.selectedValueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                switch (newV) {
                    case "idx" -> switchSubTab(1);
                    case "fk" -> switchSubTab(2);
                    case "uq" -> switchSubTab(3);
                    case "chk" -> switchSubTab(4);
                    case "trig" -> switchSubTab(5);
                    default -> switchSubTab(0);
                }
            }
        });

        bar.getChildren().add(subTabToggle);
        return bar;
    }

    private void switchSubTab(int index) {
        javafx.scene.Node targetView;
        boolean showInspector = false;
        switch (index) {
            case 1 -> targetView = indexGrid;
            case 2 -> targetView = foreignKeyGrid;
            case 3 -> targetView = uniqueGrid;
            case 4 -> targetView = checkGrid;
            case 5 -> targetView = triggerPane;
            default -> {
                targetView = columnGrid;
                showInspector = btnToggleInspector.isSelected();
            }
        }
        if (!leftSplit.getItems().isEmpty()) {
            leftSplit.getItems().set(0, targetView);
        }
        toggleInspector(showInspector);
    }

    private final TableView<TriggerDesign> triggerTableView = new TableView<>();
    private final Button btnOpenTriggerSql = new Button("Mở định nghĩa trong tab SQL");

    private VBox buildTriggerPane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        box.getStyleClass().add("td-constraint-grid-pane");
        VBox.setVgrow(box, Priority.ALWAYS);

        TableColumn<TriggerDesign, String> nameCol = new TableColumn<>("Tên Trigger");
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
        nameCol.setPrefWidth(app.ui.components.UiMetrics.em(15.38));

        TableColumn<TriggerDesign, String> timingCol = new TableColumn<>("Thời điểm");
        timingCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getTiming() != null ? cd.getValue().getTiming() : "AFTER"));
        timingCol.setPrefWidth(app.ui.components.UiMetrics.em(9.23));

        TableColumn<TriggerDesign, String> eventsCol = new TableColumn<>("Sự kiện");
        eventsCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getEvents() != null && !cd.getValue().getEvents().isEmpty()
                        ? String.join(", ", cd.getValue().getEvents())
                        : ""));
        eventsCol.setPrefWidth(app.ui.components.UiMetrics.em(13.85));

        TableColumn<TriggerDesign, String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty("Bật"));
        statusCol.setPrefWidth(app.ui.components.UiMetrics.em(7.69));

        triggerTableView.getColumns().setAll(nameCol, timingCol, eventsCol, statusCol);
        triggerTableView.setPlaceholder(new EmptyState(
                "mdi2f-flash-outline",
                "Chưa có Trigger nào",
                "Bảng này hiện chưa có trigger nào được định nghĩa."
        ));
        VBox.setVgrow(triggerTableView, Priority.ALWAYS);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        btnOpenTriggerSql.getStyleClass().add("button");
        btnOpenTriggerSql.setGraphic(Icons.of("fth-external-link"));
        btnOpenTriggerSql.setDisable(true);

        triggerTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            btnOpenTriggerSql.setDisable(newV == null || newV.getDefinition() == null || newV.getDefinition().isBlank());
        });

        btnOpenTriggerSql.setOnAction(e -> {
            TriggerDesign selected = triggerTableView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getDefinition() != null && !selected.getDefinition().isBlank()) {
                openSqlInNewTab("Trigger_" + selected.getName() + ".sql", selected.getDefinition());
            }
        });

        Label note = new Label("Danh sách trigger chỉ đọc. Chỉnh sửa trigger được thực hiện qua kịch bản SQL.");
        note.getStyleClass().add("text-secondary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        actions.getChildren().addAll(btnOpenTriggerSql, spacer, note);
        box.getChildren().addAll(triggerTableView, actions);
        return box;
    }

    private WorkspaceFooter buildFooter() {
        lblSummary.getStyleClass().add("td-footer-summary");
        workspaceFooter.addSection(lblSummary);
        workspaceFooter.setRightNode(badgeValidation);
        return workspaceFooter;
    }

    private void toggleInspector(boolean visible) {
        if (!visible) {
            mainSplit.getItems().remove(columnInspector);
        } else if (!mainSplit.getItems().contains(columnInspector)) {
            mainSplit.getItems().add(columnInspector);
            mainSplit.setDividerPositions(0.78);
        }
    }

    private void toggleDdlPreview(boolean visible) {
        if (!visible) {
            leftSplit.getItems().remove(ddlPreviewPane);
        } else if (!leftSplit.getItems().contains(ddlPreviewPane)) {
            leftSplit.getItems().add(ddlPreviewPane);
            leftSplit.setDividerPositions(0.72);
        }
    }

    // -------------------------------------------------------------------------
    // Data Loading & State Management
    // -------------------------------------------------------------------------
    private void loadInitialData() {
        // Load schemas for Schema ComboBox
        if (profile != null) {
            DesktopMetadataService.getInstance().fetchSchemas(profile, catalog, schemas -> {
                if (schemas != null && !schemas.isEmpty()) {
                    cbSchema.setItems(FXCollections.observableArrayList(schemas));
                    if (!schemas.contains(cbSchema.getValue())) {
                        cbSchema.setValue(initialSchema);
                    }
                }
            });
        }

        if (initialClonedDesign != null) {
            // Copy table: use preset cloned TableDesign
            originalDesign = null;
            currentDesign = initialClonedDesign;
            txtTableName.setText(currentDesign.getTable());
            cbSchema.setValue(currentDesign.getSchema());
            columnGrid.setItems(currentDesign.getColumns());
            indexGrid.setItems(currentDesign.getIndexes());
            foreignKeyGrid.setItems(currentDesign.getForeignKeys());
            uniqueGrid.setItems(currentDesign.getUniques());
            checkGrid.setItems(currentDesign.getChecks());
            triggerTableView.setItems(FXCollections.observableArrayList(currentDesign.getTriggers()));
            onDesignChanged();
        } else if (isNewTable) {
            // New table: instantiate initial TableDesign
            originalDesign = null;
            currentDesign = TableDesign.newTable(engine, initialSchema, initialTableName);
            columnGrid.setItems(currentDesign.getColumns());
            indexGrid.setItems(currentDesign.getIndexes());
            foreignKeyGrid.setItems(currentDesign.getForeignKeys());
            uniqueGrid.setItems(currentDesign.getUniques());
            checkGrid.setItems(currentDesign.getChecks());
            triggerTableView.setItems(FXCollections.observableArrayList(currentDesign.getTriggers()));
            onDesignChanged();
        } else {
            // Edit table: load existing design via service
            if (profile != null) {
                designerService.loadTableDesignAsync(profile, catalog, initialSchema, initialTableName)
                        .thenAccept(res -> Platform.runLater(() -> {
                            if (res.isSuccess()) {
                                originalDesign = res.getDataOrThrow();
                                currentDesign = originalDesign.deepCopy();
                                txtTableName.setText(currentDesign.getTable());
                                cbSchema.setValue(currentDesign.getSchema());
                                columnGrid.setItems(currentDesign.getColumns());
                                indexGrid.setItems(currentDesign.getIndexes());
                                foreignKeyGrid.setItems(currentDesign.getForeignKeys());
                                uniqueGrid.setItems(currentDesign.getUniques());
                                checkGrid.setItems(currentDesign.getChecks());
                                triggerTableView.setItems(FXCollections.observableArrayList(currentDesign.getTriggers()));
                                onDesignChanged();
                            } else {
                                NotificationService.getInstance().showError("Lỗi tải bảng",
                                        res.getError().map(e -> e.message()).orElse("Không tải được metadata bảng."));
                            }
                        }));
            } else if (originalDesign != null) {
                currentDesign = originalDesign.deepCopy();
                txtTableName.setText(currentDesign.getTable());
                cbSchema.setValue(currentDesign.getSchema());
                columnGrid.setItems(currentDesign.getColumns());
                indexGrid.setItems(currentDesign.getIndexes());
                foreignKeyGrid.setItems(currentDesign.getForeignKeys());
                uniqueGrid.setItems(currentDesign.getUniques());
                checkGrid.setItems(currentDesign.getChecks());
                triggerTableView.setItems(FXCollections.observableArrayList(currentDesign.getTriggers()));
                onDesignChanged();
            }
        }
    }

    private void onDesignChanged() {
        updateCurrentModel();
        scheduleDdlPreviewUpdate();
    }

    private void updateCurrentModel() {
        if (currentDesign == null) {
            currentDesign = new TableDesign(engine, cbSchema.getValue(), txtTableName.getText());
        }
        currentDesign.setSchema(cbSchema.getValue());
        currentDesign.setTable(txtTableName.getText());
        currentDesign.setColumns(columnGrid.toDomainColumns());
        currentDesign.setIndexes(indexGrid.toDomainIndexes());
        currentDesign.setForeignKeys(foreignKeyGrid.toDomainForeignKeys());
        currentDesign.setUniques(uniqueGrid.toDomainUniques());
        currentDesign.setChecks(checkGrid.toDomainChecks());
    }

    private void updateSummary(MigrationPlan plan) {
        int totalCols = columnGrid.getItems().size();
        long pkCount = columnGrid.getItems().stream().filter(ColumnDesignViewModel::isPrimaryKey).count();

        if (isNewTable) {
            lblSummary.setText(totalCols + " cột | " + pkCount + " khóa chính | Bảng mới");
        } else {
            int changeCount = plan != null ? plan.statements().size() : (int) columnGrid.getItems().stream().filter(ColumnDesignViewModel::isDirty).count();
            lblSummary.setText(totalCols + " cột | " + pkCount + " khóa chính | " + changeCount + " thay đổi chưa lưu");
        }

        // Validate
        ValidationResult vr = validator.validate(currentDesign);
        if (vr.isValid()) {
            badgeValidation.setText("Hợp lệ");
            badgeValidation.setVariant(Badge.Variant.SUCCESS);
            badgeValidation.setOnMouseClicked(null);
            badgeValidation.setCursor(Cursor.DEFAULT);
        } else {
            int errCount = vr.errors().size();
            badgeValidation.setText(errCount + " lỗi cấu trúc");
            badgeValidation.setVariant(Badge.Variant.DANGER);
            badgeValidation.setCursor(Cursor.HAND);
            badgeValidation.setOnMouseClicked(e -> {
                if (subTabToggle != null) {
                    subTabToggle.select("col");
                }
                NotificationService.getInstance().showWarning("Lỗi cấu trúc", vr.errors().get(0).message());
            });
        }
    }

    private void scheduleDdlPreviewUpdate() {
        if (pendingDdlTask != null && !pendingDdlTask.isDone()) {
            pendingDdlTask.cancel(false);
        }

        final TableDesign clonedCurrent = currentDesign != null ? currentDesign.deepCopy() : null;
        final TableDesign clonedOriginal = originalDesign != null ? originalDesign.deepCopy() : null;
        final boolean newTableSnapshot = isNewTable;

        pendingDdlTask = debounceExecutor.schedule(() -> {
            try {
                DdlDialect dialect = designerService.getDialect(engine);
                String previewSql;
                MigrationPlan plan = null;

                if (newTableSnapshot || clonedOriginal == null) {
                    previewSql = clonedCurrent != null ? dialect.generateCreateTable(clonedCurrent) : "";
                } else {
                    Result<MigrationPlan> planRes = designerService.generateMigrationPlan(clonedOriginal, clonedCurrent);
                    if (planRes.isSuccess()) {
                        plan = planRes.getDataOrThrow();
                        previewSql = plan.generateFullMigrationSql(engine);
                        if (previewSql == null || previewSql.isBlank()) {
                            previewSql = "-- Không có thay đổi nào so với CSDL";
                        }
                    } else {
                        previewSql = "-- Lỗi kiểm tra tính hợp lệ:\n-- " + planRes.getError().map(e -> e.message()).orElse("Lỗi");
                    }
                }

                final MigrationPlan finalPlan = plan;
                final String finalPreview = previewSql;
                Platform.runLater(() -> {
                    ddlPreviewPane.setSql(finalPreview);
                    boolean dirty = newTableSnapshot || (finalPlan != null && finalPlan.hasChanges());
                    dirtyProperty().set(dirty);
                    updateSummary(finalPlan);
                });
            } catch (Exception ex) {
                log.debug("DDL preview generation error: {}", ex.getMessage());
                Platform.runLater(() -> {
                    ddlPreviewPane.setSql("-- Lỗi sinh DDL: " + ex.getMessage());
                });
            }
        }, 200, TimeUnit.MILLISECONDS);
    }

    private void handleSave() {
        var stage = app.application.AppContext.getInstance().getPrimaryStage();
        Window owner = stage != null && stage.getScene() != null ? stage.getScene().getWindow() : null;

        if (!app.framework.connection.WriteGuard.ensureWritable(profile, owner)) {
            return;
        }

        ConnectionProfileDto currentProf = profile != null ? app.framework.connection.ProfileLookup.byId(profile.id()) : null;
        if (currentProf == null && profile != null) currentProf = app.framework.connection.ProfileLookup.byName(profile.name());
        if (currentProf != null) this.profile = currentProf;
        btnSave.setTooltip(new Tooltip("Lưu thay đổi vào CSDL (Ctrl+S)"));

        updateCurrentModel();
        ValidationResult vr = validator.validate(currentDesign);
        if (!vr.isValid()) {
            NotificationService.getInstance().showError("Lỗi cấu trúc",
                    "Không thể lưu. " + vr.errors().get(0).message());
            return;
        }

        DdlDialect dialect = designerService.getDialect(engine);
        MigrationPlan plan = null;
        String migrationSql;
        if (isNewTable || originalDesign == null) {
            migrationSql = dialect.generateCreateTable(currentDesign);
        } else {
            Result<MigrationPlan> planRes = designerService.generateMigrationPlan(originalDesign, currentDesign);
            if (planRes.isFailure()) {
                NotificationService.getInstance().showError("Lỗi cấu trúc", planRes.getError().map(AppError::message).orElse("Lỗi"));
                return;
            }
            plan = planRes.getDataOrThrow();
            migrationSql = plan.generateFullMigrationSql(engine);
            if (migrationSql == null || migrationSql.isBlank()) {
                NotificationService.getInstance().showInfo("Table Designer", "Không có thay đổi nào so với CSDL hiện tại.");
                return;
            }
        }

        Optional<SaveDesignDialog.SaveDesignResult> result = SaveDesignDialog.show(
                owner, currentDesign.getSchema(), currentDesign.getTable(), plan, migrationSql
        );

        if (result.isPresent()) {
            var res = result.get();
            if (res.action() == SaveDesignDialog.Action.EXECUTE) {
                final MigrationPlan finalPlan = plan;
                btnSave.setDisable(true);

                applyMigrationTask.execute(
                        ctx -> {
                            ConnectionProfileDto execProfile = this.profile != null ? app.framework.connection.ProfileLookup.byId(this.profile.id()) : null;
                            if (execProfile == null) execProfile = this.profile;
                            Result<Void> applyRes = designerService.applyMigration(execProfile, catalog, finalPlan, res.finalSql());
                            if (applyRes.isFailure()) {
                                String code = applyRes.getError().map(AppError::code).orElse("DDL_ERROR");
                                if ("READ_ONLY_GUARDRAIL".equals(code) || "READ_ONLY_PROFILE".equals(code)) {
                                    throw new RuntimeException("READ_ONLY:" + applyRes.getError().get().message());
                                }
                                throw new RuntimeException(applyRes.getError().map(AppError::message).orElse("Không thể áp dụng thay đổi DDL."));
                            }
                            return null;
                        },
                        success -> {
                            btnSave.setDisable(false);
                            NotificationService.getInstance().showSuccess("Lưu thành công", "Đã cập nhật cấu trúc bảng " + currentDesign.getTable());
                            // TD-1: transition new/copy table to edit mode
                            isNewTable = false;
                            initialClonedDesign = null;
                            initialSchema = currentDesign.getSchema();
                            initialTableName = currentDesign.getTable();
                            if (BackendFlags.mock(BackendFlags.Area.METADATA) || profile == null) {
                                originalDesign = currentDesign.deepCopy();
                            }
                            setTabTitle(initialTableName);
                            dirtyProperty().set(false);
                            loadInitialData();
                        },
                        error -> {
                            btnSave.setDisable(false);
                            String msg = error != null && error.getMessage() != null ? error.getMessage() : "Lỗi thực thi DDL";
                            if (msg.startsWith("READ_ONLY:")) {
                                boolean unlocked = app.framework.connection.WriteGuard.ensureWritable(profile, owner);
                                if (unlocked) {
                                    ConnectionProfileDto fresh = app.framework.connection.ProfileLookup.byId(profile.id());
                                    if (fresh != null) this.profile = fresh;
                                    handleSave();
                                    return;
                                }
                                msg = msg.substring("READ_ONLY:".length());
                            }
                            NotificationService.getInstance().showError("Lỗi thực thi DDL", msg);
                        }
                );
            } else if (res.action() == SaveDesignDialog.Action.OPEN_IN_SQL_TAB) {
                handleOpenInSqlTab();
            }
        }
    }

    private void handleOpenInSqlTab() {
        updateCurrentModel();
        DdlDialect dialect = designerService.getDialect(engine);
        String sql;
        if (isNewTable || originalDesign == null) {
            sql = dialect.generateCreateTable(currentDesign);
        } else {
            Result<MigrationPlan> planRes = designerService.generateMigrationPlan(originalDesign, currentDesign);
            sql = planRes.isSuccess() ? planRes.getDataOrThrow().generateFullMigrationSql(engine) : dialect.generateCreateTable(currentDesign);
        }

        String title = "Script_" + currentDesign.getTable() + ".sql";
        openSqlInNewTab(title, sql);
    }

    private void openSqlInNewTab(String title, String sql) {
        try {
            EditorController editorController = null;
            var stage = app.application.AppContext.getInstance().getPrimaryStage();
            if (stage != null && stage.getScene() != null) {
                var ec = stage.getScene().getUserData();
                if (ec instanceof EditorController controller) {
                    editorController = controller;
                }
            }
            if (editorController != null) {
                editorController.openNewTab(title, sql, profile != null ? profile.name() : "", catalog, currentDesign != null ? currentDesign.getSchema() : initialSchema);
            } else {
                NotificationService.getInstance().showSuccess("SQL Script", "Đã sinh kịch bản cho: " + title);
            }
        } catch (Exception ex) {
            log.warn("Error opening in SQL tab: {}", ex.getMessage());
        }
    }

    public void close() {
        debounceExecutor.shutdownNow();
    }
}
