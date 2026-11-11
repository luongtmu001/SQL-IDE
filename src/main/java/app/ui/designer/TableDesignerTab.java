package app.ui.designer;

import app.controllers.EditorController;
import app.framework.backend.BackendFlags;
import app.framework.designer.TableDesignerService;
import app.framework.metadata.DesktopMetadataService;
import app.services.NotificationService;
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
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
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
    private final String initialSchema;
    private final String initialTableName;
    private final boolean isNewTable;
    private final String engine;
    private final TableDesign initialClonedDesign;

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
    private final Label lblValidation = new Label("Hợp lệ");

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
        rootContainer.getStyleClass().add("table-designer-root");
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

        // Load or create initial design
        loadInitialData();

        // Bind Undo/Redo button states
        btnUndo.disableProperty().bind(undoManager.canUndoProperty().not());
        btnRedo.disableProperty().bind(undoManager.canRedoProperty().not());

        // Keyboard handler for Save (Ctrl+S)
        rootContainer.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.S) {
                handleSave();
                e.consume();
            }
        });
        // Clean up debounce executor when tab is closed
        setOnClosed(e -> debounceExecutor.shutdownNow());
    }

    private HBox buildHeaderToolbar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 10, 4, 10));
        bar.getStyleClass().add("td-header-toolbar");

        Label lblSchema = new Label("Schema:");
        cbSchema.setEditable(true);
        cbSchema.setPrefWidth(120);
        cbSchema.setValue(initialSchema);
        cbSchema.setOnAction(e -> onDesignChanged());

        Label lblTable = new Label("Tên bảng:");
        txtTableName.setText(initialTableName);
        txtTableName.setPrefWidth(180);
        txtTableName.textProperty().addListener((obs, o, n) -> {
            setTabTitle(n != null && !n.isBlank() ? n : "Table");
            onDesignChanged();
        });

        // Save Button
        btnSave.getStyleClass().addAll("button", "accent");
        FontIcon saveIcon = new FontIcon("fth-save");
        saveIcon.setIconSize(13);
        btnSave.setGraphic(saveIcon);
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
        FontIcon extIcon = new FontIcon("fth-external-link");
        extIcon.setIconSize(13);
        btnOpenSql.setGraphic(extIcon);
        btnOpenSql.setTooltip(new Tooltip("Mở DDL / Migration script trong tab SQL"));
        btnOpenSql.setOnAction(e -> handleOpenInSqlTab());

        // Undo & Redo Buttons
        btnUndo.getStyleClass().addAll("button", "small");
        FontIcon undoIcon = new FontIcon("fth-rotate-ccw");
        undoIcon.setIconSize(13);
        btnUndo.setGraphic(undoIcon);
        btnUndo.setTooltip(new Tooltip("Hoàn tác (Ctrl+Z)"));
        btnUndo.setOnAction(e -> {
            if (undoManager.canUndo()) undoManager.undo();
            onDesignChanged();
        });

        btnRedo.getStyleClass().addAll("button", "small");
        FontIcon redoIcon = new FontIcon("fth-rotate-cw");
        redoIcon.setIconSize(13);
        btnRedo.setGraphic(redoIcon);
        btnRedo.setTooltip(new Tooltip("Làm lại (Ctrl+Y)"));
        btnRedo.setOnAction(e -> {
            if (undoManager.canRedo()) undoManager.redo();
            onDesignChanged();
        });

        // Refresh Button
        btnRefresh.getStyleClass().addAll("button", "small");
        FontIcon refIcon = new FontIcon("fth-refresh-cw");
        refIcon.setIconSize(13);
        btnRefresh.setGraphic(refIcon);
        btnRefresh.setTooltip(new Tooltip("Tải lại từ CSDL (F5)"));
        btnRefresh.setOnAction(e -> loadInitialData());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Toggle Inspector Button
        btnToggleInspector.getStyleClass().addAll("toggle-button", "small");
        btnToggleInspector.setSelected(true);
        FontIcon sideIcon = new FontIcon("fth-sidebar");
        sideIcon.setIconSize(13);
        btnToggleInspector.setGraphic(sideIcon);
        btnToggleInspector.setTooltip(new Tooltip("Ẩn/Hiện Inspector thuộc tính"));
        btnToggleInspector.setOnAction(e -> toggleInspector(btnToggleInspector.isSelected()));

        // Toggle DDL Preview Button
        btnToggleDdl.getStyleClass().addAll("toggle-button", "small");
        btnToggleDdl.setSelected(true);
        FontIcon ddlIcon = new FontIcon("fth-code");
        ddlIcon.setIconSize(13);
        btnToggleDdl.setGraphic(ddlIcon);
        btnToggleDdl.setTooltip(new Tooltip("Ẩn/Hiện xem trước DDL"));
        btnToggleDdl.setOnAction(e -> toggleDdlPreview(btnToggleDdl.isSelected()));

        bar.getChildren().addAll(
                lblSchema, cbSchema, lblTable, txtTableName,
                btnSave, btnOpenSql,
                new Separator(Orientation.VERTICAL),
                btnUndo, btnRedo, btnRefresh,
                spacer,
                btnToggleDdl, btnToggleInspector
        );

        return bar;
    }

    private HBox buildSubTabBar() {
        HBox bar = new HBox(4);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(2, 10, 2, 10));
        bar.getStyleClass().add("td-subtab-strip");

        ToggleGroup group = new ToggleGroup();
        ToggleButton tCol = subTabButton("Cột", "mdi2t-table-column", group, true);
        ToggleButton tIdx = subTabButton("Chỉ mục", "mdi2b-bookmark-outline", group, false);
        ToggleButton tFk = subTabButton("Khoá ngoại", "mdi2k-key-link", group, false);
        ToggleButton tUq = subTabButton("Ràng buộc duy nhất", "mdi2f-fingerprint", group, false);
        ToggleButton tChk = subTabButton("Ràng buộc kiểm tra", "mdi2c-check-circle-outline", group, false);
        ToggleButton tTrig = subTabButton("Trigger", "mdi2f-flash-outline", group, false);

        tCol.setOnAction(e -> switchSubTab(0));
        tIdx.setOnAction(e -> switchSubTab(1));
        tFk.setOnAction(e -> switchSubTab(2));
        tUq.setOnAction(e -> switchSubTab(3));
        tChk.setOnAction(e -> switchSubTab(4));
        tTrig.setOnAction(e -> switchSubTab(5));

        bar.getChildren().addAll(tCol, tIdx, tFk, tUq, tChk, tTrig);
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

    private VBox buildTriggerPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(24));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("td-constraint-grid-pane");
        VBox.setVgrow(box, Priority.ALWAYS);

        FontIcon trigIcon = new FontIcon("mdi2f-flash-outline");
        trigIcon.setIconSize(40);
        trigIcon.setStyle("-fx-icon-color: -color-fg-muted;");

        Label title = new Label("Quản lý Triggers");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;");

        Label desc = new Label("Triggers được quản lý độc lập qua các tệp kịch bản SQL chuyên biệt hoặc Object Explorer.");
        desc.setStyle("-fx-text-fill: -color-fg-muted;");

        box.getChildren().addAll(trigIcon, title, desc);
        return box;
    }

    private ToggleButton subTabButton(String text, String iconLit, ToggleGroup group, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.getStyleClass().add("td-subtab-button");
        btn.setToggleGroup(group);
        btn.setSelected(selected);
        if (iconLit != null) {
            FontIcon icon = new FontIcon(iconLit);
            icon.setIconSize(13);
            btn.setGraphic(icon);
        }
        btn.setOnAction(e -> {
            if (!btn.isSelected()) btn.setSelected(true); // Don't allow unselecting all
        });
        return btn;
    }

    private HBox buildFooter() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(3, 10, 3, 10));
        bar.getStyleClass().add("td-footer-bar");

        lblSummary.getStyleClass().add("td-footer-summary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lblValidation.getStyleClass().add("td-footer-validation");

        bar.getChildren().addAll(lblSummary, spacer, lblValidation);
        return bar;
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
                                onDesignChanged();
                            } else {
                                NotificationService.getInstance().showError("Lỗi tải bảng",
                                        res.getError().map(e -> e.message()).orElse("Không tải được metadata bảng."));
                            }
                        }));
            }
        }
    }

    private void onDesignChanged() {
        updateCurrentModel();
        updateSummary();
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

        // Check dirty status
        boolean dirty = isNewTable || (originalDesign != null && columnGrid.getItems().stream().anyMatch(ColumnDesignViewModel::isDirty));
        // Mark tab dirty (asterisk on title)
        dirtyProperty().set(dirty);
    }

    private void updateSummary() {
        int totalCols = columnGrid.getItems().size();
        long pkCount = columnGrid.getItems().stream().filter(ColumnDesignViewModel::isPrimaryKey).count();
        long changedCount = columnGrid.getItems().stream().filter(ColumnDesignViewModel::isDirty).count();

        if (isNewTable) {
            lblSummary.setText(totalCols + " cột | " + pkCount + " khóa chính");
        } else {
            lblSummary.setText(totalCols + " cột | " + pkCount + " khóa chính | " + changedCount + " thay đổi chưa lưu");
        }

        // Validate
        ValidationResult vr = validator.validate(currentDesign);
        if (vr.isValid()) {
            lblValidation.setText("Hợp lệ");
            lblValidation.getStyleClass().removeAll("td-validation-error");
            lblValidation.getStyleClass().add("td-validation-valid");
        } else {
            int errCount = vr.errors().size();
            lblValidation.setText(errCount + " lỗi cấu trúc: " + vr.errors().get(0).message());
            lblValidation.getStyleClass().removeAll("td-validation-valid");
            lblValidation.getStyleClass().add("td-validation-error");
        }
    }

    private void scheduleDdlPreviewUpdate() {
        if (pendingDdlTask != null && !pendingDdlTask.isDone()) {
            pendingDdlTask.cancel(false);
        }

        pendingDdlTask = debounceExecutor.schedule(() -> {
            try {
                DdlDialect dialect = designerService.getDialect(engine);
                String previewSql;

                if (isNewTable || originalDesign == null) {
                    previewSql = dialect.generateCreateTable(currentDesign);
                } else {
                    Result<MigrationPlan> planRes = designerService.generateMigrationPlan(originalDesign, currentDesign);
                    if (planRes.isSuccess()) {
                        previewSql = planRes.getDataOrThrow().generateFullMigrationSql(engine);
                        if (previewSql == null || previewSql.isBlank()) {
                            previewSql = "-- Không có thay đổi nào so với CSDL";
                        }
                    } else {
                        previewSql = "-- Lỗi kiểm tra tính hợp lệ:\n-- " + planRes.getError().map(e -> e.message()).orElse("Lỗi");
                    }
                }

                ddlPreviewPane.setSql(previewSql);
            } catch (Exception ex) {
                log.debug("DDL preview generation error: {}", ex.getMessage());
                ddlPreviewPane.setSql("-- Lỗi sinh DDL: " + ex.getMessage());
            }
        }, 200, TimeUnit.MILLISECONDS);
    }

    private void handleSave() {
        ConnectionProfileDto currentProf = profile != null ? app.framework.connection.ProfileLookup.byId(profile.id()) : null;
        if (currentProf == null && profile != null) currentProf = app.framework.connection.ProfileLookup.byName(profile.name());
        if (currentProf == null) currentProf = profile;
        if (BackendFlags.readOnlyAll() || (currentProf != null && currentProf.readOnly())) {
            var stage = app.application.AppContext.getInstance().getPrimaryStage();
            javafx.stage.Window owner = stage != null && stage.getScene() != null ? stage.getScene().getWindow() : null;
            boolean unlocked = app.ui.dialogs.ReadOnlyGuardDialog.show(owner, currentProf != null ? currentProf.name() : "");
            if (unlocked) {
                currentProf = app.framework.connection.ProfileLookup.byId(currentProf.id());
                if (currentProf == null && profile != null) currentProf = app.framework.connection.ProfileLookup.byName(profile.name());
                if (currentProf != null) this.profile = currentProf;
                btnSave.setTooltip(new Tooltip("Lưu thay đổi vào CSDL (Ctrl+S)"));
            } else {
                return;
            }
        } else if (currentProf != null) {
            this.profile = currentProf;
        }

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

        var stage = app.application.AppContext.getInstance().getPrimaryStage();
        Window owner = stage != null && stage.getScene() != null ? stage.getScene().getWindow() : null;

        Optional<SaveDesignDialog.SaveDesignResult> result = SaveDesignDialog.show(
                owner, currentDesign.getSchema(), currentDesign.getTable(), plan, migrationSql
        );

        if (result.isPresent()) {
            var res = result.get();
            if (res.action() == SaveDesignDialog.Action.EXECUTE) {
                ConnectionProfileDto execProfile = this.profile != null ? app.framework.connection.ProfileLookup.byId(this.profile.id()) : null;
                if (execProfile == null) execProfile = this.profile;
                Result<Void> applyRes = designerService.applyMigration(execProfile, catalog, plan, res.finalSql());
                if (applyRes.isSuccess()) {
                    NotificationService.getInstance().showSuccess("Lưu thành công", "Đã cập nhật cấu trúc bảng " + currentDesign.getTable());
                    dirtyProperty().set(false);
                    loadInitialData();
                } else {
                    String err = applyRes.getError().map(AppError::message).orElse("Không thể áp dụng thay đổi DDL.");
                    if (err.contains("READ_ONLY") || err.toLowerCase().contains("chỉ đọc") || err.toLowerCase().contains("read-only")) {
                        boolean unlocked = app.ui.dialogs.ReadOnlyGuardDialog.show(owner, execProfile != null ? execProfile.name() : "");
                        if (unlocked) {
                            ConnectionProfileDto fresh = app.framework.connection.ProfileLookup.byId(execProfile.id());
                            if (fresh != null) this.profile = fresh;
                            Result<Void> retryRes = designerService.applyMigration(this.profile, catalog, plan, res.finalSql());
                            if (retryRes.isSuccess()) {
                                NotificationService.getInstance().showSuccess("Lưu thành công", "Đã cập nhật cấu trúc bảng " + currentDesign.getTable());
                                dirtyProperty().set(false);
                                loadInitialData();
                                return;
                            }
                        }
                    }
                    NotificationService.getInstance().showError("Lỗi thực thi DDL", err);
                }
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

        // Find EditorController and open a new SQL tab
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
                String title = "Script_" + currentDesign.getTable() + ".sql";
                editorController.openNewTab(title, sql, profile != null ? profile.name() : "", catalog, currentDesign.getSchema());
            } else {
                NotificationService.getInstance().showSuccess("DDL Script", "Đã sinh script cho bảng " + currentDesign.getTable());
            }
        } catch (Exception ex) {
            log.warn("Error opening in SQL tab: {}", ex.getMessage());
        }
    }

    public void close() {
        debounceExecutor.shutdownNow();
    }
}
