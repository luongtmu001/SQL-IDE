package app.ui.dataeditor;

import app.controllers.EditorController;
import app.framework.backend.BackendFlags;
import app.framework.connection.ConnectionSessionManager;
import app.framework.designer.TableDesignerService;
import app.framework.session.CredentialResolver;
import app.services.NotificationService;
import app.ui.components.Badge;
import app.ui.components.EmptyState;
import app.ui.components.PaneHeader;
import app.ui.components.SearchField;
import app.ui.components.WorkspaceFooter;
import app.ui.components.WorkspaceToolbar;
import app.ui.dialogs.PreviewDataChangesDialog;
import app.ui.dialogs.ReadOnlyGuardDialog;
import app.ui.editor.EmbeddedMonaco;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.tableeditor.TableDataEditorService;
import com.lltool.core.common.result.Result;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tableeditor.model.DataChangeBatch;
import com.lltool.core.domain.tableeditor.model.DataRowChange;
import com.lltool.core.domain.tableeditor.model.TableEditorMetadata;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Modern SSMS Query Designer & Edit Rows Table Data Editor Tab (Phase 3 - Slice 2).
 * Integrates:
 * 1. Toolbar with Run (F5), Save (Ctrl+S), Discard, Add Row, Delete Row, Duplicate, Top limits, Quick Search.
 * 2. Toggleable Criteria Pane (Filters, Operators, Sort, Order).
 * 3. Toggleable SQL Pane (Live synchronized SELECT query according to dialect).
 * 4. Data Grid Pane (ALWAYS visible) with sticky row status column, inline editing, dirty cell highlighting, Ctrl+0 for [NULL].
 * 5. Slide-out Schema & Constraint Inspector Drawer.
 * 6. Footer status bar with row navigator, change metrics, active cell readout, editable badge.
 */
public class TableDataEditorTab extends SqlEditorTab {

    private static final Logger log = LoggerFactory.getLogger(TableDataEditorTab.class);

    private ConnectionProfileDto profile;
    private final String catalog;
    private final String schema;
    private final String tableName;
    private final String engine;

    private final TableDataEditorService dataEditorService = new TableDataEditorService();
    private final TableDesignerService designerService = TableDesignerService.getInstance();

    private TableDesign loadedTableDesign;
    private TableEditorMetadata metadata;
    private final ObservableList<ObservableDataRow> masterItems = FXCollections.observableArrayList();
    private final FilteredList<ObservableDataRow> filteredItems = new FilteredList<>(masterItems, p -> true);
    private final TableView<ObservableDataRow> tableView = new TableView<>();

    // Panes
    private final CriteriaGrid criteriaGrid = new CriteriaGrid();
    private final VBox sqlPane = new VBox();
    private EmbeddedMonaco monacoSql;
    private final VBox gridContainer = new VBox();
    private final SplitPane vSplitPane = new SplitPane();
    private ConstraintInspectorPane inspectorPane;

    // Controls
    private final ToggleButton toggleCriteria = new ToggleButton("Bộ lọc");
    private final ToggleButton toggleSql = new ToggleButton("SQL");
    private final ToggleButton toggleInspector = new ToggleButton("Thông tin bảng");
    private final ComboBox<String> cbLimit = new ComboBox<>();
    private final SearchField txtQuickSearch = new SearchField("Tìm nhanh trong dữ liệu...");
    private final Button btnSave = new Button("Lưu (0)");
    private final Badge lblPkBadge = Badge.neutral("PK: —");

    // Status Bar & Footer
    private final WorkspaceFooter workspaceFooter = new WorkspaceFooter();
    private final Label lblRowNav = new Label("Dòng 0 / 0");
    private final Label lblSummary = new Label("0 dòng");
    private final Label lblDirty = new Label("0 thay đổi");
    private final Label lblActiveCell = new Label("Ô đang chọn: —");
    private final Label lblReadOnly = new Label();
    private final Label lblGridStats = new Label();

    public TableDataEditorTab(
            ConnectionProfileDto profile,
            String catalog,
            String schema,
            String tableName,
            Consumer<SqlEditorTabAction> actionHandler) {

        super(
                tableName != null ? tableName : "Data",
                "",
                null,
                profile != null ? profile.name() : "",
                catalog,
                schema,
                actionHandler
        );

        this.profile = profile;
        this.catalog = catalog;
        this.schema = (schema != null && !schema.isBlank()) ? schema :
                (profile != null && profile.databaseType() != null && profile.databaseType().toLowerCase().contains("postgre") ? "public" : "dbo");
        this.tableName = tableName != null ? tableName : "";
        this.engine = (profile != null && profile.databaseType() != null && profile.databaseType().toLowerCase().contains("postgre")) ? "postgresql" : "sqlserver";

        setFileIconLiteral("mdi2d-database-edit");
        initializeView();
    }

    private void initializeView() {
        VBox root = new VBox();
        root.getStyleClass().add("td-data-editor-root");

        var cssRes = getClass().getResource("/css/table-data-editor.css");
        if (cssRes != null) {
            root.getStylesheets().add(cssRes.toExternalForm());
        }

        // 1. Top Toolbar
        WorkspaceToolbar toolbar = buildToolbar();

        // 2. Build Panes
        criteriaGrid.setMaxHeight(180);
        buildSqlPane();
        buildGridContainer();

        inspectorPane = new ConstraintInspectorPane(() -> {
            toggleInspector.setSelected(false);
            updateDrawerVisibility();
        });
        inspectorPane.setVisible(false);
        inspectorPane.setManaged(false);

        // Vertical SplitPane holds Criteria (optional), SQL (optional), and Grid (ALWAYS present)
        vSplitPane.setOrientation(Orientation.VERTICAL);
        rebuildSplitPaneItems();

        BorderPane centerArea = new BorderPane();
        centerArea.setCenter(vSplitPane);
        centerArea.setRight(inspectorPane);
        VBox.setVgrow(centerArea, Priority.ALWAYS);

        // 3. Footer Bar
        WorkspaceFooter footer = buildFooter();

        root.getChildren().addAll(toolbar, centerArea, footer);

        String tabKey = "table_data_editor_" + catalog + "_" + schema + "_" + tableName;
        setCustomContent(tabKey, root);

        // Criteria change listener to update SQL live
        criteriaGrid.setOnChanged(this::syncLiveSql);

        // Load data async on open
        loadDataAsync();

        // Keyboard Shortcuts
        root.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.S) {
                handleSave();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.N) {
                handleAddRow();
                e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.DELETE) {
                handleDeleteRow();
                e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.F5) {
                loadDataAsync();
                e.consume();
            }
        });
    }

    private WorkspaceToolbar buildToolbar() {
        WorkspaceToolbar bar = new WorkspaceToolbar();

        // Left controls
        Button btnRun = new Button("Chạy");
        btnRun.getStyleClass().addAll("button", "accent", "small");
        FontIcon playIcon = new FontIcon("fth-play");
        playIcon.setIconSize(12);
        btnRun.setGraphic(playIcon);
        btnRun.setTooltip(new Tooltip("Thực thi truy vấn nạp dữ liệu với các tiêu chí hiện tại (F5 / Alt+X)"));
        btnRun.setOnAction(e -> loadDataAsync());

        btnSave.getStyleClass().addAll("button", "success", "small");
        FontIcon saveIcon = new FontIcon("fth-save");
        saveIcon.setIconSize(12);
        btnSave.setGraphic(saveIcon);
        btnSave.setTooltip(new Tooltip("Lưu các thay đổi xuống CSDL (Ctrl+S)"));
        btnSave.setOnAction(e -> handleSave());

        Button btnDiscard = new Button("Hoàn tác");
        btnDiscard.getStyleClass().addAll("button", "small");
        FontIcon discardIcon = new FontIcon("fth-rotate-ccw");
        discardIcon.setIconSize(12);
        btnDiscard.setGraphic(discardIcon);
        btnDiscard.setTooltip(new Tooltip("Hủy bỏ thay đổi chưa lưu và nạp lại từ CSDL"));
        btnDiscard.setOnAction(e -> handleDiscard());

        Button btnAddRow = new Button("Thêm dòng");
        btnAddRow.getStyleClass().addAll("button", "small");
        FontIcon addIcon = new FontIcon("fth-plus");
        addIcon.setIconSize(12);
        btnAddRow.setGraphic(addIcon);
        btnAddRow.setTooltip(new Tooltip("Thêm một dòng mới ở cuối (Ctrl+N)"));
        btnAddRow.setOnAction(e -> handleAddRow());

        Button btnDeleteRow = new Button("Xoá dòng");
        btnDeleteRow.getStyleClass().addAll("button", "small");
        FontIcon delIcon = new FontIcon("fth-trash-2");
        delIcon.setIconSize(12);
        btnDeleteRow.setGraphic(delIcon);
        btnDeleteRow.setTooltip(new Tooltip("Đánh dấu xoá dòng đang chọn"));
        btnDeleteRow.setOnAction(e -> handleDeleteRow());

        Button btnDuplicate = new Button("Nhân bản");
        btnDuplicate.getStyleClass().addAll("button", "small");
        FontIcon dupIcon = new FontIcon("fth-copy");
        dupIcon.setIconSize(12);
        btnDuplicate.setGraphic(dupIcon);
        btnDuplicate.setTooltip(new Tooltip("Nhân bản dòng đang chọn"));
        btnDuplicate.setOnAction(e -> handleDuplicateRow());

        bar.addLeft(btnRun);
        bar.addLeft(btnSave);
        bar.addLeft(btnDiscard);
        bar.addLeft(new Separator(Orientation.VERTICAL));
        bar.addLeft(btnAddRow);
        bar.addLeft(btnDeleteRow);
        bar.addLeft(btnDuplicate, WorkspaceToolbar.Priority.LOW);

        // Center controls
        toggleCriteria.setText("Bộ lọc");
        toggleCriteria.getStyleClass().addAll("button", "small");
        FontIcon critIcon = new FontIcon("fth-filter");
        critIcon.setIconSize(12);
        toggleCriteria.setGraphic(critIcon);
        toggleCriteria.setSelected(false);
        toggleCriteria.setTooltip(new Tooltip("Ẩn/Hiện Criteria Pane (Điều kiện lọc & Sắp xếp)"));
        toggleCriteria.setOnAction(e -> rebuildSplitPaneItems());

        toggleSql.setText("SQL");
        toggleSql.getStyleClass().addAll("button", "small");
        FontIcon sqlIcon = new FontIcon("fth-code");
        sqlIcon.setIconSize(12);
        toggleSql.setGraphic(sqlIcon);
        toggleSql.setSelected(false);
        toggleSql.setTooltip(new Tooltip("Ẩn/Hiện SQL Pane (Kịch bản truy vấn SELECT)"));
        toggleSql.setOnAction(e -> rebuildSplitPaneItems());

        cbLimit.setItems(FXCollections.observableArrayList("50 dòng", "100 dòng", "200 dòng", "500 dòng", "1000 dòng", "Tất cả"));
        cbLimit.setValue("200 dòng");
        cbLimit.setPrefWidth(105);
        cbLimit.getStyleClass().add("small");
        cbLimit.setOnAction(e -> {
            syncLiveSql();
            loadDataAsync();
        });

        txtQuickSearch.setPrefWidth(180);
        txtQuickSearch.textProperty().addListener((obs, oldV, newV) -> filterGrid(newV));

        bar.addCenter(toggleCriteria);
        bar.addCenter(toggleSql);
        bar.addCenter(new Separator(Orientation.VERTICAL));
        bar.addCenter(new Label("Top:"));
        bar.addCenter(cbLimit);
        bar.addCenter(txtQuickSearch);

        // Right controls
        Button btnScript = new Button("Xem DML");
        btnScript.getStyleClass().addAll("button", "small");
        FontIcon scriptIcon = new FontIcon("fth-file-text");
        scriptIcon.setIconSize(12);
        btnScript.setGraphic(scriptIcon);
        btnScript.setTooltip(new Tooltip("Xem trước kịch bản DML chuẩn bị thực thi"));
        btnScript.setOnAction(e -> handlePreviewDml());

        toggleInspector.getStyleClass().addAll("button", "small");
        FontIcon infoIcon = new FontIcon("fth-info");
        infoIcon.setIconSize(12);
        toggleInspector.setGraphic(infoIcon);
        toggleInspector.setTooltip(new Tooltip("Mở ngăn kéo tra cứu cấu trúc bảng và ràng buộc"));
        toggleInspector.setOnAction(e -> updateDrawerVisibility());

        bar.addRight(lblPkBadge);
        bar.addRight(btnScript, WorkspaceToolbar.Priority.LOW);
        bar.addRight(toggleInspector);

        return bar;
    }

    private void buildSqlPane() {
        sqlPane.getStyleClass().add("tde-sql-pane");
        sqlPane.setPrefHeight(120);
        sqlPane.setMinHeight(80);

        String dialect = engine.equalsIgnoreCase("postgresql") ? "PostgreSQL" : "Microsoft SQL Server";
        PaneHeader header = new PaneHeader("Câu truy vấn", "fth-code", "Dialect: " + dialect);

        String modelId = "tde_sql_" + schema + "_" + tableName;
        monacoSql = new EmbeddedMonaco(modelId, "sql", "-- Đang tạo câu truy vấn...");
        monacoSql.setReadOnly(true);
        monacoSql.setShowStatusBar(false);
        VBox.setVgrow(monacoSql.node(), Priority.ALWAYS);

        sqlPane.getChildren().addAll(header, monacoSql.node());
    }

    private void buildGridContainer() {
        gridContainer.getStyleClass().add("tde-grid-pane");
        VBox.setVgrow(gridContainer, Priority.ALWAYS);

        PaneHeader miniHeader = new PaneHeader("Dữ liệu", "fth-grid", "(Nhấn đúp hoặc Enter để sửa ô, Ctrl+0 gán [NULL], Delete để đánh dấu xóa)");
        miniHeader.setRightNode(lblGridStats);

        setupTableView();
        VBox.setVgrow(tableView, Priority.ALWAYS);

        gridContainer.getChildren().addAll(miniHeader, tableView);
    }

    private void setupTableView() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.setPlaceholder(new EmptyState("fth-inbox", "Chưa có dữ liệu", "Bấm 'Chạy' để nạp dữ liệu từ cơ sở dữ liệu."));
        tableView.setItems(filteredItems);

        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ObservableDataRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-inserted", "row-modified", "row-deleted");
                if (!empty && item != null) {
                    switch (item.getState()) {
                        case INSERTED -> getStyleClass().add("row-inserted");
                        case MODIFIED -> getStyleClass().add("row-modified");
                        case DELETED -> getStyleClass().add("row-deleted");
                        default -> {}
                    }
                }
            }
        });

        // Row navigation and active cell listener
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (newRow != null) {
                int rowIdx = masterItems.indexOf(newRow) + 1;
                lblRowNav.setText("Dòng " + rowIdx + " / " + masterItems.size());
            }
        });

        tableView.getFocusModel().focusedCellProperty().addListener((obs, oldCell, newCell) -> {
            if (newCell != null && newCell.getTableColumn() != null) {
                String colText = newCell.getTableColumn().getText();
                lblActiveCell.setText("Ô đang chọn: [Dòng " + (newCell.getRow() + 1) + ", Cột: " + colText + "]");
            }
        });
    }

    private void rebuildSplitPaneItems() {
        vSplitPane.getItems().clear();

        if (toggleCriteria.isSelected()) {
            vSplitPane.getItems().add(criteriaGrid);
        }
        if (toggleSql.isSelected()) {
            vSplitPane.getItems().add(sqlPane);
        }

        // GRID IS ALWAYS IN THE SPLIT PANE AND ALWAYS VISIBLE!
        vSplitPane.getItems().add(gridContainer);
    }

    private void updateDrawerVisibility() {
        boolean show = toggleInspector.isSelected();
        inspectorPane.setVisible(show);
        inspectorPane.setManaged(show);
    }

    private WorkspaceFooter buildFooter() {
        // Row Navigator buttons
        Button btnFirst = new Button();
        btnFirst.getStyleClass().addAll("button", "small");
        btnFirst.setGraphic(new FontIcon("fth-chevrons-left"));
        btnFirst.setOnAction(e -> { if (!masterItems.isEmpty()) tableView.getSelectionModel().select(0); });

        Button btnPrev = new Button();
        btnPrev.getStyleClass().addAll("button", "small");
        btnPrev.setGraphic(new FontIcon("fth-chevron-left"));
        btnPrev.setOnAction(e -> tableView.getSelectionModel().selectPrevious());

        Button btnNext = new Button();
        btnNext.getStyleClass().addAll("button", "small");
        btnNext.setGraphic(new FontIcon("fth-chevron-right"));
        btnNext.setOnAction(e -> tableView.getSelectionModel().selectNext());

        Button btnLast = new Button();
        btnLast.getStyleClass().addAll("button", "small");
        btnLast.setGraphic(new FontIcon("fth-chevrons-right"));
        btnLast.setOnAction(e -> { if (!masterItems.isEmpty()) tableView.getSelectionModel().select(masterItems.size() - 1); });

        HBox navBox = new HBox(3, btnFirst, btnPrev, lblRowNav, btnNext, btnLast);
        navBox.setAlignment(Pos.CENTER_LEFT);

        lblSummary.getStyleClass().add("td-data-status-label");
        lblDirty.getStyleClass().add("td-data-status-label");
        lblActiveCell.getStyleClass().add("td-data-status-label");

        boolean isReadOnly = BackendFlags.readOnlyAll() || (profile != null && profile.readOnly());
        lblReadOnly.setText(isReadOnly ? "Chỉ đọc" : "Cho phép sửa");
        lblReadOnly.getStyleClass().setAll("badge", isReadOnly ? "badge-warning" : "badge-success");
        lblReadOnly.setVisible(false);

        workspaceFooter.addSection(navBox);
        workspaceFooter.addSection(lblSummary);
        workspaceFooter.addSection(lblDirty);
        workspaceFooter.addSection(lblActiveCell);
        workspaceFooter.setRightNode(lblReadOnly);

        return workspaceFooter;
    }

    private void syncLiveSql() {
        int limit = parseLimit();
        boolean isPg = engine.equalsIgnoreCase("postgresql");
        String where = criteriaGrid.buildWhereClause(isPg);
        String orderBy = criteriaGrid.buildOrderByClause(isPg);

        String sql = dataEditorService.buildSelectQuery(engine, schema, tableName, null, where, orderBy, limit, 0);
        if (monacoSql != null) {
            monacoSql.setText(sql);
        }
    }

    private void filterGrid(String filterText) {
        if (filterText == null || filterText.isBlank()) {
            filteredItems.setPredicate(p -> true);
        } else {
            String lower = filterText.toLowerCase();
            filteredItems.setPredicate(row -> {
                for (Object val : row.getCurrentValues().values()) {
                    if (val != null && String.valueOf(val).toLowerCase().contains(lower)) {
                        return true;
                    }
                }
                return false;
            });
        }
        updateSummary();
    }

    private void loadDataAsync() {
        if (profile == null) return;
        tableView.setPlaceholder(new Label("Đang truy vấn CSDL..."));

        int limit = parseLimit();
        boolean isPg = engine.equalsIgnoreCase("postgresql");
        String where = criteriaGrid.buildWhereClause(isPg);
        String orderBy = criteriaGrid.buildOrderByClause(isPg);

        CompletableFuture.runAsync(() -> {
            try {
                if (BackendFlags.mock(BackendFlags.Area.METADATA)) {
                    if (loadedTableDesign == null) {
                        loadedTableDesign = designerService.createMockTableDesign(engine, catalog, schema, tableName);
                        metadata = TableEditorMetadata.fromTableDesign(loadedTableDesign);
                    }
                    List<String> columnNames = loadedTableDesign.getColumns().stream()
                            .map(com.lltool.core.domain.tabledesigner.model.ColumnDesign::getName)
                            .toList();
                    List<ObservableDataRow> loadedRows = new ArrayList<>();
                    int count = Math.min(limit, 30);
                    for (int rowIdx = 1; rowIdx <= count; rowIdx++) {
                        Map<String, Object> rowVals = new LinkedHashMap<>();
                        for (var col : loadedTableDesign.getColumns()) {
                            String name = col.getName();
                            Object val;
                            if ("Id".equalsIgnoreCase(name)) {
                                val = rowIdx;
                            } else if ("CustomerCode".equalsIgnoreCase(name) || "customer_code".equalsIgnoreCase(name)) {
                                val = "CUST-" + String.format("%04d", rowIdx);
                            } else if ("FullName".equalsIgnoreCase(name) || "full_name".equalsIgnoreCase(name)) {
                                val = (rowIdx == 1) ? "Công ty Cổ phần Giải pháp Công nghệ Thông tin Toàn cầu Việt Nam" : "Khách hàng " + rowIdx;
                            } else if ("Email".equalsIgnoreCase(name) || "email".equalsIgnoreCase(name)) {
                                val = (rowIdx == 2) ? null : "customer" + rowIdx + "@example.com";
                            } else if ("PhoneNumber".equalsIgnoreCase(name) || "phone_number".equalsIgnoreCase(name)) {
                                val = (rowIdx == 3) ? null : "09012345" + String.format("%02d", rowIdx);
                            } else if ("Balance".equalsIgnoreCase(name) || "balance".equalsIgnoreCase(name)) {
                                val = 1500000.50 * rowIdx;
                            } else if ("Status".equalsIgnoreCase(name) || "status".equalsIgnoreCase(name)) {
                                val = (rowIdx % 5 == 0) ? "INACTIVE" : "ACTIVE";
                            } else if ("CreatedAt".equalsIgnoreCase(name) || "created_at".equalsIgnoreCase(name)) {
                                val = "2026-11-11 10:00:00";
                            } else {
                                val = "Value " + rowIdx;
                            }
                            rowVals.put(name, val);
                        }
                        loadedRows.add(new ObservableDataRow(rowIdx, rowVals, false));
                    }

                    Platform.runLater(() -> {
                        criteriaGrid.setAvailableColumns(columnNames);
                        syncLiveSql();
                        updatePkBadge();
                        if (loadedTableDesign != null) {
                            inspectorPane.update(loadedTableDesign, metadata);
                        }
                        buildTableColumns(columnNames);
                        masterItems.setAll(loadedRows);
                        updateSummary();
                        lblReadOnly.setVisible(true);
                        workspaceFooter.setSampleDataVisible(true);
                    });
                    return;
                }

                // 1. Ensure metadata is loaded
                if (metadata == null) {
                    Result<TableDesign> designRes = designerService.loadTableDesign(profile, catalog, schema, tableName);
                    if (designRes.isSuccess()) {
                        loadedTableDesign = designRes.getDataOrThrow();
                        metadata = TableEditorMetadata.fromTableDesign(loadedTableDesign);
                    } else {
                        metadata = new TableEditorMetadata(engine, catalog, schema, tableName, Collections.emptyList());
                    }
                }

                // 2. Fetch data via JDBC
                String query = dataEditorService.buildSelectQuery(engine, schema, tableName, null, where, orderBy, limit, 0);

                String targetDb = catalog != null && !catalog.isBlank() ? catalog : profile.databaseName();
                String pwd = CredentialResolver.resolve(profile, null);

                Result<DatabaseConnection> connRes = ConnectionSessionManager.getInstance().openSession(profile, targetDb, pwd);
                if (connRes.isFailure()) {
                    Platform.runLater(() -> tableView.setPlaceholder(new Label("Lỗi kết nối: " + connRes.getError().get().message())));
                    return;
                }

                List<ObservableDataRow> loadedRows = new ArrayList<>();
                List<String> columnNames = new ArrayList<>();

                try (DatabaseConnection dbConn = connRes.getDataOrThrow()) {
                    Connection raw = dbConn.getRawJdbcConnection();
                    try (Statement stmt = raw.createStatement();
                         ResultSet rs = stmt.executeQuery(query)) {

                        ResultSetMetaData rsmd = rs.getMetaData();
                        int colCount = rsmd.getColumnCount();
                        for (int i = 1; i <= colCount; i++) {
                            columnNames.add(rsmd.getColumnLabel(i));
                        }

                        int rowIdx = 1;
                        while (rs.next()) {
                            Map<String, Object> rowVals = new LinkedHashMap<>();
                            for (int i = 1; i <= colCount; i++) {
                                rowVals.put(columnNames.get(i - 1), rs.getObject(i));
                            }
                            loadedRows.add(new ObservableDataRow(rowIdx++, rowVals, false));
                        }
                    }
                }

                Platform.runLater(() -> {
                    criteriaGrid.setAvailableColumns(columnNames);
                    syncLiveSql();
                    updatePkBadge();
                    if (loadedTableDesign != null) {
                        inspectorPane.update(loadedTableDesign, metadata);
                    }
                    buildTableColumns(columnNames);
                    masterItems.setAll(loadedRows);
                    updateSummary();
                    dirtyProperty().set(false);
                    lblReadOnly.setVisible(true);
                    workspaceFooter.setSampleDataVisible(BackendFlags.mock(BackendFlags.Area.METADATA));
                });

            } catch (Exception ex) {
                log.error("Failed to load table data: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    tableView.setPlaceholder(new Label("Lỗi nạp dữ liệu: " + ex.getMessage()));
                    NotificationService.getInstance().showError("Lỗi dữ liệu", ex.getMessage());
                });
            }
        });
    }

    private void updatePkBadge() {
        if (metadata != null && !metadata.getPrimaryKeyNames().isEmpty()) {
            lblPkBadge.setText("PK: " + String.join(", ", metadata.getPrimaryKeyNames()));
            lblPkBadge.setVariant(Badge.Variant.ACCENT);
            lblPkBadge.setTooltip(null);
        } else if (metadata != null) {
            lblPkBadge.setText("Không có PK");
            lblPkBadge.setVariant(Badge.Variant.DANGER);
            lblPkBadge.setTooltip(new Tooltip("Bảng không có khoá chính, thao tác Sửa/Xoá có thể ảnh hưởng nhiều dòng"));
        } else {
            lblPkBadge.setText("PK: —");
            lblPkBadge.setVariant(Badge.Variant.NEUTRAL);
            lblPkBadge.setTooltip(null);
        }
    }

    private void buildTableColumns(List<String> colNames) {
        tableView.getColumns().clear();

        // 1. Sticky Row index & status column
        TableColumn<ObservableDataRow, String> colRowNo = new TableColumn<>("#");
        colRowNo.setPrefWidth(48);
        colRowNo.setMinWidth(48);
        colRowNo.setMaxWidth(65);
        colRowNo.setStyle("-fx-alignment: CENTER; -fx-font-family: monospace;");
        colRowNo.setCellValueFactory(data -> {
            ObservableDataRow row = data.getValue();
            String prefix = switch (row.getState()) {
                case INSERTED -> "+ ";
                case MODIFIED -> "~ ";
                case DELETED -> "- ";
                default -> "";
            };
            return new SimpleStringProperty(prefix + row.getRowId());
        });
        tableView.getColumns().add(colRowNo);

        // 2. Data columns
        Set<String> computedCols = metadata != null ? metadata.getComputedNames() : Collections.emptySet();
        Set<String> identityCols = metadata != null ? metadata.getIdentityNames() : Collections.emptySet();
        List<String> pkCols = metadata != null ? metadata.getPrimaryKeyNames() : Collections.emptyList();

        for (String colName : colNames) {
            boolean isPk = pkCols.stream().anyMatch(pk -> pk.equalsIgnoreCase(colName));
            boolean isIdentity = identityCols.contains(colName.toLowerCase());
            boolean isComputed = computedCols.contains(colName.toLowerCase());

            TableColumn<ObservableDataRow, Object> col = new TableColumn<>(colName);

            // Auto-calculate width based on column name and first 50 rows (min 80, max 360)
            int maxChars = colName.length();
            for (int i = 0; i < Math.min(50, masterItems.size()); i++) {
                Object val = masterItems.get(i).getValue(colName);
                if (val != null) {
                    maxChars = Math.max(maxChars, String.valueOf(val).length());
                }
            }
            double estimatedWidth = Math.max(80, Math.min(360, maxChars * 8.5 + 40));
            col.setPrefWidth(estimatedWidth);
            col.setCellValueFactory(data -> data.getValue().getProperty(colName));

            // Column Header Graphic: Name + small muted data type subtitle underneath
            VBox headerBox = new VBox(1);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            HBox topRow = new HBox(4);
            topRow.setAlignment(Pos.CENTER_LEFT);
            if (isPk) {
                FontIcon keyIcon = new FontIcon("fth-key");
                keyIcon.setIconSize(11);
                keyIcon.getStyleClass().add("icon-accent");
                topRow.getChildren().add(keyIcon);
            }
            if (isIdentity || isComputed) {
                FontIcon lockIcon = new FontIcon("fth-lock");
                lockIcon.setIconSize(10);
                lockIcon.getStyleClass().add("icon-muted");
                topRow.getChildren().add(lockIcon);
            }
            Label titleLbl = new Label(colName);
            if (isPk) {
                titleLbl.getStyleClass().add("header-pk");
            }
            topRow.getChildren().add(titleLbl);

            String dataTypeName = null;
            if (metadata != null) {
                dataTypeName = metadata.getColumns().stream()
                        .filter(c -> c.name().equalsIgnoreCase(colName))
                        .map(TableEditorMetadata.ColumnMeta::dataType)
                        .findFirst().orElse(null);
            }
            Label typeLbl = new Label(dataTypeName != null ? dataTypeName : "");
            typeLbl.getStyleClass().add("tde-col-type-subtitle");

            headerBox.getChildren().addAll(topRow, typeLbl);
            col.setGraphic(headerBox);

            boolean readOnlyCol = isIdentity || isComputed;
            col.setCellFactory(c -> new EditableDataTableCell(colName, readOnlyCol));

            col.setOnEditCommit(e -> {
                ObservableDataRow row = e.getRowValue();
                row.setValue(colName, e.getNewValue());
                updateSummary();
                dirtyProperty().set(true);
            });

            tableView.getColumns().add(col);
        }
    }

    private int parseLimit() {
        String val = cbLimit.getValue();
        if ("50 dòng".equals(val)) return 50;
        if ("100 dòng".equals(val)) return 100;
        if ("500 dòng".equals(val)) return 500;
        if ("1000 dòng".equals(val)) return 1000;
        if ("Tất cả".equals(val)) return 0;
        return 200;
    }

    private void handleAddRow() {
        int nextId = masterItems.size() + 1;
        Map<String, Object> emptyVals = new LinkedHashMap<>();
        if (metadata != null) {
            for (var c : metadata.getColumns()) {
                emptyVals.put(c.name(), null);
            }
        }
        ObservableDataRow newRow = new ObservableDataRow(nextId, emptyVals, true);
        masterItems.add(newRow);
        tableView.getSelectionModel().select(newRow);
        tableView.scrollTo(newRow);
        updateSummary();
        dirtyProperty().set(true);
    }

    private void handleDeleteRow() {
        var selected = tableView.getSelectionModel().getSelectedItems();
        if (selected == null || selected.isEmpty()) return;

        List<ObservableDataRow> toDelete = new ArrayList<>(selected);
        for (ObservableDataRow row : toDelete) {
            if (row.getState() == ObservableDataRow.RowState.INSERTED) {
                masterItems.remove(row);
            } else if (row.getState() == ObservableDataRow.RowState.DELETED) {
                row.unmarkDeleted();
            } else {
                row.markDeleted();
            }
        }
        tableView.refresh();
        updateSummary();
        dirtyProperty().set(true);
    }

    private void handleDuplicateRow() {
        ObservableDataRow selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        int nextId = masterItems.size() + 1;
        Map<String, Object> dupVals = new LinkedHashMap<>(selected.getCurrentValues());

        // Skip identity column value for duplicated row
        if (metadata != null) {
            for (String idCol : metadata.getIdentityNames()) {
                for (String k : dupVals.keySet()) {
                    if (k.equalsIgnoreCase(idCol)) {
                        dupVals.put(k, null);
                    }
                }
            }
        }

        ObservableDataRow dupRow = new ObservableDataRow(nextId, dupVals, true);
        masterItems.add(dupRow);
        tableView.getSelectionModel().select(dupRow);
        tableView.scrollTo(dupRow);
        updateSummary();
        dirtyProperty().set(true);
    }

    private void handleDiscard() {
        List<ObservableDataRow> toRemove = new ArrayList<>();
        for (ObservableDataRow row : masterItems) {
            if (row.getState() == ObservableDataRow.RowState.INSERTED) {
                toRemove.add(row);
            } else {
                row.revert();
            }
        }
        masterItems.removeAll(toRemove);
        tableView.refresh();
        updateSummary();
        dirtyProperty().set(false);
    }

    private void handlePreviewDml() {
        DataChangeBatch batch = buildChangeBatch();
        List<String> dmlStatements = dataEditorService.buildDmlStatements(batch, metadata, true);
        String fullScript = dmlStatements.isEmpty() ? "-- Không có thay đổi nào cần lưu." : String.join("\n", dmlStatements);

        int ins = batch.getInserts().size();
        int upd = batch.getUpdates().size();
        int del = batch.getDeletes().size();

        var stage = app.application.AppContext.getInstance().getPrimaryStage();
        Window owner = stage != null && stage.getScene() != null ? stage.getScene().getWindow() : null;

        PreviewDataChangesDialog.show(owner, schema, tableName, ins, upd, del, fullScript, false);
    }

    private void handleSave() {
        ConnectionProfileDto currentProf = profile != null ? app.framework.connection.ProfileLookup.byId(profile.id()) : null;
        if (currentProf == null && profile != null) currentProf = app.framework.connection.ProfileLookup.byName(profile.name());
        if (currentProf == null) currentProf = profile;
        if (BackendFlags.readOnlyAll() || (currentProf != null && currentProf.readOnly())) {
            var stage = app.application.AppContext.getInstance().getPrimaryStage();
            Window owner = stage != null && stage.getScene() != null ? stage.getScene().getWindow() : null;
            boolean unlocked = ReadOnlyGuardDialog.show(owner, currentProf != null ? currentProf.name() : "");
            if (!unlocked) return;
            currentProf = app.framework.connection.ProfileLookup.byId(currentProf.id());
            if (currentProf == null && profile != null) currentProf = app.framework.connection.ProfileLookup.byName(profile.name());
            if (currentProf != null) this.profile = currentProf;
            lblReadOnly.setText("Cho phép sửa");
            lblReadOnly.setStyle("-fx-text-fill: -color-success-emphasis; -fx-font-weight: bold;");
        } else if (currentProf != null) {
            this.profile = currentProf;
        }

        DataChangeBatch batch = buildChangeBatch();
        if (batch.isEmpty()) {
            NotificationService.getInstance().showInfo("Table Data Editor", "Không có thay đổi nào cần lưu.");
            return;
        }

        List<String> dmlStatements = dataEditorService.buildDmlStatements(batch, metadata, true);
        String fullScript = String.join("\n", dmlStatements);

        int ins = batch.getInserts().size();
        int upd = batch.getUpdates().size();
        int del = batch.getDeletes().size();

        var stage = app.application.AppContext.getInstance().getPrimaryStage();
        Window owner = stage != null && stage.getScene() != null ? stage.getScene().getWindow() : null;

        Optional<PreviewDataChangesDialog.PreviewResult> resultOpt = PreviewDataChangesDialog.show(
                owner, schema, tableName, ins, upd, del, fullScript
        );

        if (resultOpt.isEmpty()) return;

        var result = resultOpt.get();
        if (result.action() == PreviewDataChangesDialog.Action.EXECUTE) {
            executeBatch(dmlStatements);
        } else if (result.action() == PreviewDataChangesDialog.Action.OPEN_IN_SQL_TAB) {
            openInSqlTab(fullScript);
        }
    }

    private void executeBatch(List<String> dmlStatements) {
        ConnectionProfileDto execProfile = this.profile != null ? app.framework.connection.ProfileLookup.byId(this.profile.id()) : null;
        if (execProfile == null && this.profile != null) execProfile = app.framework.connection.ProfileLookup.byName(this.profile.name());
        if (execProfile != null) this.profile = execProfile;
        else execProfile = this.profile;

        String targetDb = catalog != null && !catalog.isBlank() ? catalog : (execProfile != null ? execProfile.databaseName() : null);
        String pwd = CredentialResolver.resolve(execProfile, null);

        Result<DatabaseConnection> connRes = ConnectionSessionManager.getInstance().openSession(execProfile, targetDb, pwd);
        if (connRes.isFailure()) {
            String err = connRes.getError().get().message();
            if (err.contains("READ_ONLY") || err.toLowerCase().contains("chỉ đọc") || err.toLowerCase().contains("read-only")) {
                var stage = app.application.AppContext.getInstance().getPrimaryStage();
                Window owner = stage != null && stage.getScene() != null ? stage.getScene().getWindow() : null;
                boolean unlocked = ReadOnlyGuardDialog.show(owner, execProfile != null ? execProfile.name() : "");
                if (unlocked) {
                    ConnectionProfileDto fresh = app.framework.connection.ProfileLookup.byId(execProfile.id());
                    if (fresh != null) this.profile = fresh;
                    executeBatch(dmlStatements);
                    return;
                }
            }
            NotificationService.getInstance().showError("Lỗi kết nối", err);
            return;
        }

        final ConnectionProfileDto finalProfile = execProfile;
        CompletableFuture.runAsync(() -> {
            try (DatabaseConnection dbConn = connRes.getDataOrThrow()) {
                Connection raw = dbConn.getRawJdbcConnection();
                boolean prevAutoCommit = raw.getAutoCommit();
                raw.setAutoCommit(false);

                try (Statement stmt = raw.createStatement()) {
                    for (String sql : dmlStatements) {
                        stmt.execute(sql);
                    }
                    raw.commit();
                } catch (Exception ex) {
                    raw.rollback();
                    throw ex;
                } finally {
                    try { raw.setAutoCommit(prevAutoCommit); } catch (Exception ignored) {}
                }

                Platform.runLater(() -> {
                    NotificationService.getInstance().showSuccess("Lưu thành công", "Đã cập nhật dữ liệu bảng " + tableName);
                    dirtyProperty().set(false);
                    loadDataAsync();
                });

            } catch (Exception e) {
                log.error("Failed to execute data batch: {}", e.getMessage(), e);
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("25006") || msg.toLowerCase().contains("read-only") || msg.toLowerCase().contains("readonly") || msg.toLowerCase().contains("chỉ đọc") || msg.toLowerCase().contains("read only")) {
                    Platform.runLater(() -> {
                        var stage = app.application.AppContext.getInstance().getPrimaryStage();
                        Window owner = stage != null && stage.getScene() != null ? stage.getScene().getWindow() : null;
                        boolean unlocked = ReadOnlyGuardDialog.show(owner, finalProfile != null ? finalProfile.name() : "");
                        if (unlocked) {
                            executeBatch(dmlStatements);
                        } else {
                            NotificationService.getInstance().showError("Lỗi thực thi dữ liệu", e.getMessage());
                        }
                    });
                } else {
                    Platform.runLater(() -> NotificationService.getInstance().showError("Lỗi thực thi dữ liệu", e.getMessage()));
                }
            }
        });
    }

    private void openInSqlTab(String sql) {
        try {
            var stage = app.application.AppContext.getInstance().getPrimaryStage();
            if (stage != null && stage.getScene() != null) {
                var ec = stage.getScene().getUserData();
                if (ec instanceof EditorController controller) {
                    String title = "DML_" + tableName + ".sql";
                    controller.openNewTab(title, sql, profile != null ? profile.name() : "", catalog, schema);
                }
            }
        } catch (Exception ex) {
            log.warn("Error opening script in SQL tab: {}", ex.getMessage());
        }
    }

    private DataChangeBatch buildChangeBatch() {
        DataChangeBatch batch = new DataChangeBatch(engine, schema, tableName);
        List<String> pkNames = metadata != null ? metadata.getPrimaryKeyNames() : Collections.emptyList();

        for (ObservableDataRow row : masterItems) {
            if (row.getState() == ObservableDataRow.RowState.INSERTED) {
                DataRowChange change = new DataRowChange(DataRowChange.Type.INSERT, row.getRowId());
                Map<String, Object> insertedVals = new LinkedHashMap<>();
                for (String dirtyCol : row.getDirtyColumns()) {
                    insertedVals.put(dirtyCol, row.getValue(dirtyCol));
                }
                change.setNewValues(insertedVals);
                batch.addChange(change);
            } else if (row.getState() == ObservableDataRow.RowState.MODIFIED) {
                DataRowChange change = new DataRowChange(DataRowChange.Type.UPDATE, row.getRowId());
                Map<String, Object> pks = new LinkedHashMap<>();
                for (String pk : pkNames) {
                    pks.put(pk, row.getOriginalValues().get(pk));
                }
                change.setPkValues(pks);

                Map<String, Object> changedVals = new LinkedHashMap<>();
                for (String dirtyCol : row.getDirtyColumns()) {
                    changedVals.put(dirtyCol, row.getValue(dirtyCol));
                }
                change.setNewValues(changedVals);
                change.setOldValues(row.getOriginalValues());
                batch.addChange(change);
            } else if (row.getState() == ObservableDataRow.RowState.DELETED) {
                DataRowChange change = new DataRowChange(DataRowChange.Type.DELETE, row.getRowId());
                Map<String, Object> pks = new LinkedHashMap<>();
                for (String pk : pkNames) {
                    pks.put(pk, row.getOriginalValues().get(pk));
                }
                change.setPkValues(pks);
                change.setOldValues(row.getOriginalValues());
                batch.addChange(change);
            }
        }
        return batch;
    }

    private void updateSummary() {
        int total = masterItems.size();
        long inserted = masterItems.stream().filter(r -> r.getState() == ObservableDataRow.RowState.INSERTED).count();
        long modified = masterItems.stream().filter(r -> r.getState() == ObservableDataRow.RowState.MODIFIED).count();
        long deleted = masterItems.stream().filter(r -> r.getState() == ObservableDataRow.RowState.DELETED).count();
        long totalChanges = inserted + modified + deleted;

        lblSummary.setText("Tổng số: " + total + " dòng");

        StringBuilder sb = new StringBuilder();
        if (inserted > 0) sb.append("+").append(inserted).append(" thêm ");
        if (modified > 0) sb.append("*").append(modified).append(" sửa ");
        if (deleted > 0) sb.append("-").append(deleted).append(" xóa ");

        String changesText = sb.toString().trim();
        lblDirty.setText(totalChanges > 0 ? (totalChanges + " thay đổi (" + changesText + ")") : "0 thay đổi");
        btnSave.setText("Lưu (" + totalChanges + ")");

        // Update grid stats label
        StringBuilder stats = new StringBuilder();
        if (modified > 0) stats.append("~ ").append(modified).append(" sửa  ");
        if (inserted > 0) stats.append("+ ").append(inserted).append(" thêm  ");
        if (deleted > 0) stats.append("- ").append(deleted).append(" xóa");
        lblGridStats.setText(stats.toString().trim());
    }
}
