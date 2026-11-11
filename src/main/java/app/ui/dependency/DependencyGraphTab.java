package app.ui.dependency;

import app.application.AppContext;
import app.controllers.MainController;
import app.services.NotificationService;
import app.ui.components.AppButton;
import app.ui.components.EmptyState;
import app.ui.components.IconButton;
import app.ui.components.LoadingOverlay;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.dependency.DependencyGraphService;
import com.lltool.core.domain.dependency.*;
import javafx.application.Platform;
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
import java.util.Objects;
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

    // UI Controls - Header Toolbar
    private final ComboBox<String> cbRootObject = new ComboBox<>();
    private final ToggleGroup directionGroup = new ToggleGroup();
    private final ToggleButton btnDirBoth = new ToggleButton("Cả hai chiều");
    private final ToggleButton btnDirDepends = new ToggleButton("Phụ thuộc vào (Depends On)");
    private final ToggleButton btnDirReferenced = new ToggleButton("Được dùng bởi (Referenced By)");

    private final ComboBox<String> cbDepth = new ComboBox<>();
    private final ComboBox<String> cbLayout = new ComboBox<>();

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

    private LoadingOverlay loadingOverlay;
    private final EmptyState emptyState = new EmptyState("fth-share-2", "Không có liên kết phụ thuộc", "Đối tượng này không có quan hệ phụ thuộc nào trong phạm vi đã chọn.");

    public DependencyGraphTab(ConnectionProfileDto profile, String catalog, String schema,
                              String objectName, String columnName, DependencyNodeType objectType,
                              Consumer<SqlEditorTabAction> tabActionHandler) {
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
        HBox header = buildHeaderToolbar();

        // 2. Main Content SplitPane
        canvasContainer.getChildren().addAll(graphCanvas, emptyState);
        emptyState.setVisible(false);

        loadingOverlay = LoadingOverlay.over(canvasContainer);

        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(canvasContainer, inspectorPanel);
        splitPane.setDividerPositions(0.70);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        root.getChildren().addAll(header, splitPane);
        setContent(root);

        // Wire Inspector
        graphCanvas.setOnNodeSelected(node -> inspectorPanel.inspectNode(node, activeModel));
        inspectorPanel.setOpenInSqlTabHandler(this::openSqlInConsole);
    }

    private HBox buildHeaderToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.getStyleClass().add("dep-header-bar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Object Picker
        HBox pickerBox = new HBox(6);
        pickerBox.getStyleClass().add("dep-picker-box");
        pickerBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon pickIcon = new FontIcon(objectType.getIconLiteral());
        pickIcon.setIconSize(13);
        pickIcon.getStyleClass().add("icon-accent");

        Label pickLbl = new Label("Đối tượng gốc:");
        pickLbl.getStyleClass().add("dep-control-label");

        cbRootObject.getItems().addAll(
                schema + "." + objectName + (columnName != null ? " (" + columnName + ")" : "") + " (" + objectType.name() + ")",
                "dbo.orders (TABLE)",
                "dbo.customers (TABLE)",
                "dbo.v_customer_orders (VIEW)",
                "dbo.usp_close_period (PROCEDURE)"
        );
        cbRootObject.getSelectionModel().selectFirst();
        cbRootObject.setOnAction(e -> {
            String selected = cbRootObject.getValue();
            if (selected != null && selected.contains(".")) {
                String raw = selected.split(" ")[0];
                if (raw.contains(".")) {
                    String[] parts = raw.split("\\.");
                    this.schema = parts[0];
                    this.objectName = parts[1];
                    loadGraph();
                }
            }
        });

        pickerBox.getChildren().addAll(pickIcon, pickLbl, cbRootObject);

        // Direction Toggle Group
        HBox dirBox = new HBox(2);
        dirBox.getStyleClass().add("dep-direction-group");

        btnDirBoth.setToggleGroup(directionGroup);
        btnDirDepends.setToggleGroup(directionGroup);
        btnDirReferenced.setToggleGroup(directionGroup);

        btnDirBoth.setSelected(true);
        btnDirBoth.setOnAction(e -> setDirection(GraphDirection.BOTH));
        btnDirDepends.setOnAction(e -> setDirection(GraphDirection.DEPENDENCIES));
        btnDirReferenced.setOnAction(e -> setDirection(GraphDirection.DEPENDENTS));

        dirBox.getChildren().addAll(btnDirBoth, btnDirDepends, btnDirReferenced);

        // Depth Selector
        HBox depthBox = new HBox(4);
        depthBox.setAlignment(Pos.CENTER_LEFT);
        Label depthLbl = new Label("Độ sâu:");
        depthLbl.getStyleClass().add("dep-control-label");

        cbDepth.getItems().addAll("Cấp 1", "Cấp 2 (Khuyên dùng)", "Cấp 3", "Tất cả");
        cbDepth.getSelectionModel().select(1);
        cbDepth.setOnAction(e -> {
            int idx = cbDepth.getSelectionModel().getSelectedIndex();
            this.currentDepth = (idx == 0) ? 1 : (idx == 1) ? 2 : (idx == 2) ? 3 : 5;
            loadGraph();
        });

        depthBox.getChildren().addAll(depthLbl, cbDepth);

        // Layout Selector
        HBox layoutBox = new HBox(4);
        layoutBox.setAlignment(Pos.CENTER_LEFT);
        Label layoutLbl = new Label("Bố cục:");
        layoutLbl.getStyleClass().add("dep-control-label");

        cbLayout.getItems().addAll("Hierarchical (Trái sang Phải)", "Hierarchical (Trên xuống Dưới)");
        cbLayout.getSelectionModel().selectFirst();

        layoutBox.getChildren().addAll(layoutLbl, cbLayout);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Canvas Navigation Buttons
        btnZoomIn.setOnAction(e -> graphCanvas.zoomIn());
        btnZoomOut.setOnAction(e -> graphCanvas.zoomOut());
        btnFitScreen.setOnAction(e -> graphCanvas.fitToScreen());
        btnResetZoom.setOnAction(e -> graphCanvas.resetZoom());
        btnRefresh.setOnAction(e -> loadGraph());
        btnExportPng.setOnAction(e -> exportPng());

        toolbar.getChildren().addAll(
                pickerBox,
                dirBox,
                depthBox,
                layoutBox,
                spacer,
                btnZoomIn, btnZoomOut, btnFitScreen, btnResetZoom, btnRefresh,
                btnExportPng
        );

        return toolbar;
    }

    private void initShortcuts() {
        getContent().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F5) {
                loadGraph();
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
        loadGraph();
    }

    public void loadGraph() {
        if (loadingOverlay != null) loadingOverlay.show("Đang phân tích quan hệ phụ thuộc...", null);

        // Load asynchronously to prevent UI freeze
        new Thread(() -> {
            try {
                // Build model using DependencyGraphService
                DependencyGraphModel model = graphService.createSampleOrdersGraph(currentDirection, currentDepth);
                Thread.sleep(120); // Smooth animation transition

                Platform.runLater(() -> {
                    this.activeModel = model;
                    if (model.getNodeCount() == 0) {
                        emptyState.setVisible(true);
                        graphCanvas.setVisible(false);
                    } else {
                        emptyState.setVisible(false);
                        graphCanvas.setVisible(true);
                        graphCanvas.setModel(model);
                        inspectorPanel.inspectNode(model.getRootNode(), model);
                    }
                    if (loadingOverlay != null) loadingOverlay.hide();
                });
            } catch (Exception ex) {
                log.error("Failed to build dependency graph", ex);
                Platform.runLater(() -> {
                    if (loadingOverlay != null) loadingOverlay.hide();
                    NotificationService.getInstance().showError("Lỗi", "Không thể xây dựng đồ thị phụ thuộc: " + ex.getMessage());
                });
            }
        }).start();
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
