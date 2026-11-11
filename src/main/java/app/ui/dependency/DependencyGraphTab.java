package app.ui.dependency;

import app.application.AppContext;
import app.controllers.MainController;
import app.framework.async.AsyncTaskManager;
import app.framework.async.LoadTask;
import app.framework.backend.BackendFlags;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.connection.ConnectionSessionManager;
import app.framework.dependency.DependencyGraphLoader;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import app.services.NotificationService;
import app.ui.components.*;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.dependency.DependencyGraphService;
import com.lltool.core.domain.dependency.*;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseProvider;
import com.lltool.infrastructure.database.api.DatabaseType;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Modern Dependency Graph & Impact Analysis Tab (Phase 3 - Slice 5).
 * Visualizes relational and column dependencies, supports interactive panning/zooming,
 * node dragging, and safe migration step generation.
 */
public class DependencyGraphTab extends SqlEditorTab {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphTab.class);

    private final DependencyGraphService graphService = new DependencyGraphService();

    // Context & Target
    private ConnectionProfileDto profile;
    private String catalog;
    private String schema;
    private String objectName;
    private String columnName;
    private DependencyNodeType objectType;
    private final String customKey;

    // Graph State
    private GraphDirection currentDirection = GraphDirection.BOTH;
    private int currentDepth = 2;
    private DependencyGraphModel activeModel;

    // Async Loading & Cache (DG-4)
    private final Map<String, DependencyGraphModel> graphCache = new ConcurrentHashMap<>();
    private LoadTask<DependencyGraphModel> currentLoadTask;

    // UI Controls - Header Toolbar (DG-2)
    private final ComboBox<RootObjectItem> cbRootObject = new ComboBox<>();
    private final SegmentedToggle<GraphDirection> dirToggle = new SegmentedToggle<>();
    private final ComboBox<String> cbDepth = new ComboBox<>();

    private final IconButton btnZoomIn = new IconButton("fth-plus", "Phóng to (Ctrl++)");
    private final IconButton btnZoomOut = new IconButton("fth-minus", "Thu nhỏ (Ctrl+-)");
    private final IconButton btnFitScreen = new IconButton("fth-maximize-2", "Căn vừa màn hình (Ctrl+0)");
    private final IconButton btnResetZoom = new IconButton("fth-rotate-ccw", "Reset Zoom về 100%");
    private final AppButton btnExportPng = new AppButton("Xuất PNG", "fth-download", AppButton.Variant.PRIMARY);
    private final IconButton btnRefresh = new IconButton("fth-refresh-cw", "Làm mới đồ thị (F5)");

    // UI Controls - Main Canvas & Inspector
    private final StackPane canvasContainer = new StackPane();
    private final DependencyGraphCanvas graphCanvas = new DependencyGraphCanvas();
    private final DependencyInspectorPanel inspectorPanel = new DependencyInspectorPanel();
    private final SplitPane splitPane = new SplitPane();

    // UI Controls - Footer
    private final WorkspaceFooter workspaceFooter = new WorkspaceFooter();
    private final Label lblNodeEdgeStats = new Label("0 đối tượng · 0 liên kết");

    private LoadingOverlay loadingOverlay;
    private final EmptyState emptyState = new EmptyState("fth-share-2", "Không có liên kết phụ thuộc", "Đối tượng này không có quan hệ phụ thuộc nào trong phạm vi đã chọn.");

    public DependencyGraphTab(ConnectionProfileDto profile, String catalog, String schema,
                              String objectName, String columnName, DependencyNodeType objectType,
                              Consumer<SqlEditorTab.SqlEditorTabAction> tabActionHandler) {
        super(
                buildTabTitle(schema, objectName, columnName),
                "",
                null,
                profile != null ? profile.name() : "Dependency",
                catalog,
                schema,
                tabActionHandler
        );

        this.profile = profile;
        this.catalog = catalog;
        this.schema = schema != null && !schema.isBlank() ? schema : "dbo";
        this.objectName = objectName != null && !objectName.isBlank() ? objectName : "orders";
        this.columnName = columnName;
        this.objectType = objectType != null ? objectType : DependencyNodeType.TABLE;

        this.customKey = "dep_graph|" + (profile != null ? profile.id() : "default") + "|" +
                this.schema + "." + this.objectName + (this.columnName != null ? "." + this.columnName : "");

        buildLayout();
        initShortcuts();

        // Populate root objects picker (DG-2)
        populateRootObjects();

        // Load graph data
        Platform.runLater(this::loadGraph);
    }

    private static String buildTabTitle(String schema, String objectName, String columnName) {
        String base = (schema != null && !schema.isBlank() ? schema + "." : "") + (objectName != null ? objectName : "Root");
        if (columnName != null && !columnName.isBlank()) {
            return "Dep: " + base + " (" + columnName + ")";
        }
        return "Dep: " + base;
    }

    public String getCustomKey() {
        return customKey;
    }

    private void buildLayout() {
        VBox root = new VBox();
        root.getStyleClass().addAll("dependency-graph-root");
        root.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/dependency-graph.css")).toExternalForm());

        // 1. Header Toolbar
        WorkspaceToolbar header = buildHeaderToolbar();

        // 2. Main Content SplitPane
        canvasContainer.getChildren().addAll(graphCanvas, emptyState);
        emptyState.setVisible(false);

        loadingOverlay = LoadingOverlay.over(canvasContainer);

        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(canvasContainer, inspectorPanel);
        splitPane.setDividerPositions(0.70);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // 3. Footer with Stats and Legend
        HBox legendBox = buildLegendBox();
        workspaceFooter.addSection(lblNodeEdgeStats);
        workspaceFooter.addSection(legendBox);
        workspaceFooter.setSampleDataVisible(BackendFlags.mock(BackendFlags.Area.METADATA) || MockMetadataProvider.isEnabled());

        root.getChildren().addAll(header, splitPane, workspaceFooter);
        setCustomContent(customKey, root);

        // Wire Inspector
        graphCanvas.setOnNodeSelected(node -> inspectorPanel.inspectNode(node, activeModel));
        inspectorPanel.setOpenInSqlTabHandler(this::openSqlInConsole);
    }

    private HBox buildLegendBox() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("graph-legend-box");

        box.getChildren().addAll(
                createLegendItem("Bảng", "node-type-table"),
                createLegendItem("View", "node-type-view"),
                createLegendItem("Thủ tục", "node-type-proc"),
                createLegendItem("Trigger", "node-type-trigger")
        );
        return box;
    }

    private HBox createLegendItem(String label, String styleClass) {
        HBox item = new HBox(4);
        item.setAlignment(Pos.CENTER_LEFT);
        Region dot = new Region();
        dot.setPrefSize(8, 8);
        dot.setMinSize(8, 8);
        dot.setMaxSize(8, 8);
        dot.getStyleClass().addAll("legend-dot", styleClass);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("legend-label");
        item.getChildren().addAll(dot, lbl);
        return item;
    }

    private WorkspaceToolbar buildHeaderToolbar() {
        WorkspaceToolbar toolbar = new WorkspaceToolbar();

        // Object Picker
        HBox pickerBox = new HBox(6);
        pickerBox.getStyleClass().add("dep-picker-box");
        pickerBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon pickIcon = new FontIcon(objectType.getIconLiteral());
        pickIcon.setIconSize(13);
        pickIcon.getStyleClass().add("icon-accent");

        Label pickLbl = new Label("Gốc:");
        pickLbl.getStyleClass().add("dep-control-label");

        cbRootObject.setMinWidth(220);
        cbRootObject.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(RootObjectItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getDisplayName());
                    FontIcon icon = new FontIcon(item.type().getIconLiteral());
                    icon.setIconSize(13);
                    setGraphic(icon);
                }
            }
        });
        cbRootObject.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(RootObjectItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getDisplayName());
                    FontIcon icon = new FontIcon(item.type().getIconLiteral());
                    icon.setIconSize(13);
                    setGraphic(icon);
                }
            }
        });

        cbRootObject.setOnAction(e -> {
            RootObjectItem selected = cbRootObject.getValue();
            if (selected != null) {
                this.schema = selected.schema();
                this.objectName = selected.name();
                this.objectType = selected.type();
                loadGraph(false);
            }
        });

        pickerBox.getChildren().addAll(pickIcon, pickLbl, cbRootObject);

        // Direction SegmentedToggle
        dirToggle.addItem(GraphDirection.BOTH, "Cả hai", "fth-git-commit");
        dirToggle.addItem(GraphDirection.DEPENDENCIES, "Phụ thuộc vào", "fth-arrow-right");
        dirToggle.addItem(GraphDirection.DEPENDENTS, "Được dùng bởi", "fth-arrow-left");
        dirToggle.select(GraphDirection.BOTH);
        dirToggle.selectedValueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                setDirection(newV);
            }
        });

        // Depth Selector
        HBox depthBox = new HBox(4);
        depthBox.setAlignment(Pos.CENTER_LEFT);
        Label depthLbl = new Label("Độ sâu:");
        depthLbl.getStyleClass().add("dep-control-label");

        cbDepth.getItems().addAll("Cấp 1", "Cấp 2", "Cấp 3", "Tất cả");
        cbDepth.setMinWidth(80);
        cbDepth.getSelectionModel().select(1);
        cbDepth.setOnAction(e -> {
            int idx = cbDepth.getSelectionModel().getSelectedIndex();
            this.currentDepth = (idx == 0) ? 1 : (idx == 1) ? 2 : (idx == 2) ? 3 : 100;
            loadGraph(false);
        });

        depthBox.getChildren().addAll(depthLbl, cbDepth);

        toolbar.addLeft(pickerBox);
        toolbar.addLeft(dirToggle);
        toolbar.addLeft(depthBox);

        // Canvas Navigation Buttons
        Label lblZoom = new Label("100%");
        lblZoom.textProperty().bind(graphCanvas.zoomPercentProperty());
        lblZoom.setStyle("-fx-font-size: 0.8em; -fx-text-fill: -color-fg-muted; -fx-min-width: 38px; -fx-alignment: center;");

        btnZoomIn.setOnAction(e -> graphCanvas.zoomIn());
        btnZoomOut.setOnAction(e -> graphCanvas.zoomOut());
        btnFitScreen.setOnAction(e -> graphCanvas.fitToScreen());
        btnResetZoom.setOnAction(e -> graphCanvas.resetZoom());
        btnRefresh.setOnAction(e -> loadGraph(true));
        btnExportPng.setOnAction(e -> exportPng());

        toolbar.addRight(btnZoomIn);
        toolbar.addRight(btnZoomOut);
        toolbar.addRight(lblZoom);
        toolbar.addRight(btnFitScreen);
        toolbar.addRight(btnResetZoom, WorkspaceToolbar.Priority.LOW);
        toolbar.addRight(btnRefresh);
        toolbar.addRight(btnExportPng);

        return toolbar;
    }

    private void populateRootObjects() {
        if (profile == null || MockMetadataProvider.isEnabled() || BackendFlags.mock(BackendFlags.Area.METADATA)) {
            cbRootObject.getItems().setAll(
                    new RootObjectItem("dbo", "orders", DependencyNodeType.TABLE),
                    new RootObjectItem("dbo", "customers", DependencyNodeType.TABLE),
                    new RootObjectItem("dbo", "v_customer_orders", DependencyNodeType.VIEW),
                    new RootObjectItem("dbo", "v_monthly_revenue", DependencyNodeType.VIEW),
                    new RootObjectItem("dbo", "usp_close_period", DependencyNodeType.PROCEDURE),
                    new RootObjectItem(schema != null ? schema : "dbo", objectName, objectType)
            );
            selectCurrentRoot();
            return;
        }

        AsyncTaskManager.getInstance().runInBackground(() -> {
            List<RootObjectItem> items = new ArrayList<>();
            try (DatabaseConnection dbConn = DesktopMetadataService.getInstance().explorerConnection(profile, catalog)) {
                DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                var providerOpt = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                if (providerOpt.isPresent()) {
                    var provider = providerOpt.get();
                    // Tables
                    var tablesRes = provider.getTables(dbConn, catalog, schema);
                    if (tablesRes != null && tablesRes.isSuccess() && tablesRes.getData().isPresent()) {
                        for (var t : tablesRes.getData().get()) {
                            if (!t.isView()) {
                                items.add(new RootObjectItem(t.schema(), t.name(), DependencyNodeType.TABLE));
                            }
                        }
                    }
                    // Views
                    var viewsRes = provider.getViews(dbConn, catalog, schema);
                    if (viewsRes != null && viewsRes.isSuccess() && viewsRes.getData().isPresent()) {
                        for (var v : viewsRes.getData().get()) {
                            items.add(new RootObjectItem(v.schema(), v.name(), DependencyNodeType.VIEW));
                        }
                    }
                    // Routines
                    var routRes = provider.getRoutines(dbConn, catalog, schema);
                    if (routRes != null && routRes.isSuccess() && routRes.getData().isPresent()) {
                        for (var r : routRes.getData().get()) {
                            items.add(new RootObjectItem(r.schema(), r.name(), r.isProcedure() ? DependencyNodeType.PROCEDURE : DependencyNodeType.FUNCTION));
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Could not load schema objects for dependency picker: {}", ex.getMessage());
            }

            Platform.runLater(() -> {
                if (!items.isEmpty()) {
                    cbRootObject.getItems().setAll(items);
                    selectCurrentRoot();
                } else {
                    cbRootObject.getItems().setAll(new RootObjectItem(schema, objectName, objectType));
                    selectCurrentRoot();
                }
            });
        });
    }

    private void selectCurrentRoot() {
        for (RootObjectItem item : cbRootObject.getItems()) {
            if (item.name().equalsIgnoreCase(objectName) &&
                (schema == null || item.schema().equalsIgnoreCase(schema))) {
                cbRootObject.setValue(item);
                return;
            }
        }
        if (!cbRootObject.getItems().isEmpty()) {
            cbRootObject.getSelectionModel().selectFirst();
        }
    }

    private void initShortcuts() {
        getContent().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F5) {
                loadGraph(true);
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.DIGIT0) {
                graphCanvas.fitToScreen();
                e.consume();
            } else if (e.isControlDown() && (e.getCode() == KeyCode.EQUALS || e.getCode() == KeyCode.PLUS)) {
                graphCanvas.zoomIn();
                e.consume();
            } else if (e.isControlDown() && (e.getCode() == KeyCode.MINUS)) {
                graphCanvas.zoomOut();
                e.consume();
            }
        });
    }

    private void setDirection(GraphDirection dir) {
        this.currentDirection = dir;
        loadGraph(false);
    }

    public void loadGraph() {
        loadGraph(false);
    }

    public void loadGraph(boolean forceRefresh) {
        if (forceRefresh) {
            graphCache.clear();
        }

        String targetKey = (columnName != null && !columnName.isBlank())
                ? (schema + "." + objectName + "." + columnName)
                : (schema + "." + objectName);
        String cacheKey = targetKey + "|" + currentDirection.name() + "|" + currentDepth;

        if (!forceRefresh && graphCache.containsKey(cacheKey)) {
            applyModel(graphCache.get(cacheKey));
            return;
        }

        if (currentLoadTask != null) {
            currentLoadTask.cancel();
        } else {
            currentLoadTask = new LoadTask<>();
        }

        if (loadingOverlay != null) {
            loadingOverlay.show("Đang phân tích quan hệ phụ thuộc...", () -> {
                if (currentLoadTask != null) currentLoadTask.cancel();
            });
        }

        currentLoadTask.execute(
                ctx -> {
                    ctx.checkCancelled();
                    return DependencyGraphLoader.load(
                            profile, catalog, schema, objectName, columnName, objectType,
                            currentDirection, currentDepth, msg -> {
                                Platform.runLater(() -> {
                                    if (loadingOverlay != null) loadingOverlay.setMessage(msg);
                                });
                            }
                    );
                },
                model -> {
                    if (model != null) {
                        graphCache.put(cacheKey, model);
                        applyModel(model);
                    }
                    if (loadingOverlay != null) loadingOverlay.hide();
                },
                error -> {
                    log.error("Failed to build dependency graph", error);
                    if (loadingOverlay != null) loadingOverlay.hide();
                    NotificationService.getInstance().showError("Lỗi", "Không thể xây dựng đồ thị phụ thuộc: " + (error != null ? error.getMessage() : "Unknown error"));
                },
                () -> {
                    if (loadingOverlay != null) loadingOverlay.hide();
                }
        );
    }

    private void applyModel(DependencyGraphModel model) {
        this.activeModel = model;
        lblNodeEdgeStats.setText(model.getNodeCount() + " đối tượng · " + model.getEdgeCount() + " liên kết");
        workspaceFooter.setSampleDataVisible(BackendFlags.mock(BackendFlags.Area.METADATA) || MockMetadataProvider.isEnabled());
        if (model.getNodeCount() == 0) {
            emptyState.setVisible(true);
            graphCanvas.setVisible(false);
        } else {
            emptyState.setVisible(false);
            graphCanvas.setVisible(true);
            graphCanvas.setModel(model);
            inspectorPanel.inspectNode(model.getRootNode(), model);
        }
    }

    private void exportPng() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Xuất sơ đồ phụ thuộc sang PNG");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Hình ảnh PNG (*.png)", "*.png"));
        chooser.setInitialFileName("dependency_" + schema + "_" + objectName + ".png");

        File file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            boolean ok = graphCanvas.exportToPng(file);
            if (ok) {
                NotificationService.getInstance().showSuccess("Xuất PNG", "Đã lưu sơ đồ phụ thuộc vào: " + file.getName());
            } else {
                NotificationService.getInstance().showError("Lỗi", "Không thể xuất file hình ảnh PNG.");
            }
        }
    }

    private void openSqlInConsole(String sql) {
        if (sql == null || sql.isBlank()) return;
        MainController mc = AppContext.getInstance().getMainController();
        if (mc != null) {
            String title = "Impact_" + objectName + ".sql";
            String conn = profile != null ? profile.name() : "";
            mc.openQueryConsole(title, sql, conn, catalog, schema);
        }
    }
}
