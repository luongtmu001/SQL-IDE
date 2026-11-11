package app.ui.visualizer;

import app.application.AppContext;
import app.services.NotificationService;
import app.ui.components.AppButton;
import app.ui.components.EmptyState;
import app.ui.components.IconButton;
import app.ui.components.LoadingOverlay;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.visualizer.QueryFlowAnalyzer;
import com.lltool.core.domain.visualizer.QueryFlowGraph;
import com.lltool.core.domain.visualizer.QueryFlowNode;
import com.lltool.core.domain.visualizer.SourceRange;
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
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interactive SQL Visualizer & Query Flow Tab (Phase 3 - Slice 6).
 * Analyzes complex SQL queries into intuitive multi-stage data pipelines,
 * supports two-way editor AST sync, zoom/pan canvas, and execution metrics inspector.
 */
public class SqlVisualizerTab extends SqlEditorTab {

    private static final Logger log = LoggerFactory.getLogger(SqlVisualizerTab.class);

    private final QueryFlowAnalyzer analyzer = new QueryFlowAnalyzer();

    private ConnectionProfileDto profile;
    private String catalog;
    private String schema;
    private String queryName;
    private final String customKey;

    private QueryFlowGraph activeGraph;

    // UI Controls - Header Toolbar
    private final ComboBox<String> cbPresets = new ComboBox<>();
    private final AppButton btnAnalyze = new AppButton("Phân tích", "fth-play", AppButton.Variant.PRIMARY);
    private final ToggleButton btnToggleSqlBox = new ToggleButton("Hiện SQL");

    private final ToggleGroup viewToggleGroup = new ToggleGroup();
    private final ToggleButton btnViewPipeline = new ToggleButton("Luồng dữ liệu");
    private final ToggleButton btnViewTree = new ToggleButton("Cây cú pháp");

    private final IconButton btnZoomIn = new IconButton("fth-plus", "Phóng to (Ctrl++)");
    private final IconButton btnZoomOut = new IconButton("fth-minus", "Thu nhỏ (Ctrl+-)");
    private final IconButton btnFitScreen = new IconButton("fth-maximize-2", "Căn vừa màn hình (Ctrl+0)");
    private final IconButton btnResetZoom = new IconButton("fth-rotate-ccw", "Reset Zoom 100%");
    private final AppButton btnExportPng = new AppButton("Xuất PNG", "fth-download", AppButton.Variant.DEFAULT);

    // Collapsible SQL Input Box
    private final VBox sqlDrawer = new VBox(6);
    private final TextArea txtSqlInput = new TextArea();

    // Main Canvas & Inspector
    private final StackPane canvasContainer = new StackPane();
    private final SqlVisualizerCanvas visualizerCanvas = new SqlVisualizerCanvas();
    private final SqlVisualizerInspector inspector = new SqlVisualizerInspector();
    private final SplitPane splitPane = new SplitPane();

    private LoadingOverlay loadingOverlay;
    private final EmptyState emptyState = new EmptyState("fth-cpu", "Chưa có luồng dữ liệu", "Nhập câu lệnh SQL và bấm 'Phân tích SQL' để trực quan hóa luồng dữ liệu.");

    public SqlVisualizerTab(ConnectionProfileDto profile, String catalog, String schema,
                            String queryName, String initialSql,
                            Consumer<SqlEditorTabAction> tabActionHandler) {
        super(
                buildTabTitle(queryName),
                "",
                null,
                profile != null ? profile.name() : "Visualizer",
                catalog,
                schema,
                tabActionHandler
        );

        this.profile = profile;
        this.catalog = catalog;
        this.schema = schema != null && !schema.isBlank() ? schema : "dbo";
        this.queryName = queryName != null && !queryName.isBlank() ? queryName : "Query1";
        this.customKey = "sql-visualizer-" + UUID.randomUUID();

        buildLayout();
        initShortcuts();

        // Populate initial SQL or default preset 1
        if (initialSql != null && !initialSql.isBlank()) {
            txtSqlInput.setText(initialSql);
        } else {
            txtSqlInput.setText(QueryFlowAnalyzer.getPresetSql(1));
        }

        Platform.runLater(this::runAnalysis);
    }

    public SqlVisualizerTab(ConnectionProfileDto profile, String catalog, String schema,
                            Consumer<SqlEditorTabAction> tabActionHandler) {
        this(profile, catalog, schema, "Query1", null, tabActionHandler);
    }

    private static String buildTabTitle(String name) {
        return "Flow: " + (name != null && !name.isBlank() ? name : "Query");
    }

    public String getCustomKey() {
        return customKey;
    }

    private void buildLayout() {
        VBox root = new VBox();
        root.getStyleClass().add("sql-visualizer-root");
        root.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/sql-visualizer.css")).toExternalForm());

        // 1. Header Toolbar
        HBox header = buildHeaderToolbar();

        // 2. Collapsible SQL Input Drawer
        buildSqlDrawer();

        // 3. Canvas & Inspector SplitPane
        canvasContainer.getChildren().addAll(visualizerCanvas, emptyState);
        emptyState.setVisible(false);

        loadingOverlay = LoadingOverlay.over(canvasContainer);

        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(canvasContainer, inspector);
        splitPane.setDividerPositions(0.72);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        root.getChildren().addAll(header, sqlDrawer, splitPane);
        setContent(root);

        // Wire Interactions
        visualizerCanvas.setOnNodeSelectedListener(this::handleNodeSelectedOnCanvas);

        // Editor caret listener for reverse sync (Editor -> Canvas)
        txtSqlInput.caretPositionProperty().addListener((obs, oldVal, newVal) -> {
            if (activeGraph != null && newVal != null) {
                int pos = newVal.intValue();
                String text = txtSqlInput.getText();
                int line = 1, col = 1, cur = 0;
                String[] lines = text.split("\\R", -1);
                for (int i = 0; i < lines.length; i++) {
                    int len = lines[i].length() + 1;
                    if (cur <= pos && pos < cur + len) {
                        line = i + 1;
                        col = (pos - cur) + 1;
                        break;
                    }
                    cur += len;
                }
                QueryFlowNode matchedNode = activeGraph.findNodeAtPosition(line, col).orElse(null);
                if (matchedNode != null && matchedNode != visualizerCanvas.getSelectedNode()) {
                    visualizerCanvas.selectNode(matchedNode);
                }
            }
        });
    }

    private HBox buildHeaderToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.getStyleClass().add("qf-header-bar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Preset Selector
        HBox presetBox = new HBox(6);
        presetBox.getStyleClass().add("qf-picker-box");
        presetBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon presetIcon = new FontIcon("fth-layers");
        presetIcon.setIconSize(13);
        presetIcon.getStyleClass().add("icon-accent");

        Label lblPreset = new Label("Mẫu truy vấn:");
        lblPreset.getStyleClass().add("qf-control-label");

        cbPresets.getItems().addAll(
                "Mẫu 1: Doanh thu theo khách hàng",
                "Mẫu 2: Tồn kho & Đơn hàng",
                "Mẫu 3: Đơn hàng chưa thanh toán",
                "Tùy biến câu lệnh..."
        );
        cbPresets.getSelectionModel().selectFirst();
        cbPresets.setOnAction(e -> {
            int idx = cbPresets.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < 3) {
                txtSqlInput.setText(QueryFlowAnalyzer.getPresetSql(idx + 1));
                runAnalysis();
            }
        });

        presetBox.getChildren().addAll(presetIcon, lblPreset, cbPresets);

        // Analyze Button
        btnAnalyze.setOnAction(e -> runAnalysis());

        // Toggle SQL Box
        btnToggleSqlBox.setSelected(true);
        btnToggleSqlBox.setOnAction(e -> {
            boolean visible = btnToggleSqlBox.isSelected();
            sqlDrawer.setVisible(visible);
            sqlDrawer.setManaged(visible);
        });

        // View Mode Toggle Group
        HBox viewGroup = new HBox(2);
        viewGroup.getStyleClass().add("qf-view-toggle-group");
        btnViewPipeline.setToggleGroup(viewToggleGroup);
        btnViewTree.setToggleGroup(viewToggleGroup);
        btnViewPipeline.setSelected(true);
        viewGroup.getChildren().addAll(btnViewPipeline, btnViewTree);

        viewToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (activeGraph != null) {
                // Rerender layout
                visualizerCanvas.setGraph(activeGraph);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Canvas Navigation Buttons
        btnZoomIn.setOnAction(e -> visualizerCanvas.zoomIn());
        btnZoomOut.setOnAction(e -> visualizerCanvas.zoomOut());
        btnFitScreen.setOnAction(e -> visualizerCanvas.fitToScreen());
        btnResetZoom.setOnAction(e -> visualizerCanvas.resetZoom());

        btnExportPng.setOnAction(e -> exportDiagramPng());

        toolbar.getChildren().addAll(
                presetBox,
                btnAnalyze,
                btnToggleSqlBox,
                new Separator(Orientation.VERTICAL),
                viewGroup,
                spacer,
                btnZoomIn,
                btnZoomOut,
                btnFitScreen,
                btnResetZoom,
                new Separator(Orientation.VERTICAL),
                btnExportPng
        );

        return toolbar;
    }

    private void buildSqlDrawer() {
        sqlDrawer.getStyleClass().add("qf-sql-drawer");
        sqlDrawer.setPrefHeight(110);

        HBox topBox = new HBox(8);
        topBox.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Câu lệnh SQL");
        title.setStyle("-fx-font-size: 0.82em; -fx-text-fill: -color-fg-muted; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label shortcutTip = new Label("Phím tắt: F5 hoặc Ctrl+Enter để phân tích");
        shortcutTip.setStyle("-fx-font-size: 0.78em; -fx-text-fill: -color-fg-muted;");

        topBox.getChildren().addAll(title, spacer, shortcutTip);

        txtSqlInput.getStyleClass().add("qf-sql-area");
        txtSqlInput.setPrefRowCount(4);
        txtSqlInput.setWrapText(true);
        VBox.setVgrow(txtSqlInput, Priority.ALWAYS);

        sqlDrawer.getChildren().addAll(topBox, txtSqlInput);
    }

    private void initShortcuts() {
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F5 || (e.isControlDown() && e.getCode() == KeyCode.ENTER)) {
                runAnalysis();
                e.consume();
            } else if (e.isControlDown() && (e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.EQUALS)) {
                visualizerCanvas.zoomIn();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.MINUS) {
                visualizerCanvas.zoomOut();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.DIGIT0) {
                visualizerCanvas.fitToScreen();
                e.consume();
            }
        });
    }

    public void runAnalysis() {
        String sql = txtSqlInput.getText();
        if (sql == null || sql.isBlank()) {
            emptyState.setVisible(true);
            visualizerCanvas.clearCanvas();
            inspector.inspectNode(null);
            return;
        }

        loadingOverlay.show("Đang phân tích cú pháp AST & luồng dữ liệu...", null);

        new Thread(() -> {
            try {
                QueryFlowGraph graph = analyzer.analyze(sql);
                Thread.sleep(120); // Smooth transition

                Platform.runLater(() -> {
                    this.activeGraph = graph;
                    emptyState.setVisible(graph.getNodeCount() == 0);
                    visualizerCanvas.setGraph(graph);

                    // Fit to screen on initial render
                    Platform.runLater(visualizerCanvas::fitToScreen);
                });
            } catch (Exception ex) {
                log.error("Failed to analyze SQL flow: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    NotificationService ns = NotificationService.getInstance();
                    if (ns != null) {
                        ns.showError("Lỗi phân tích SQL", ex.getMessage());
                    }
                });
            } finally {
                Platform.runLater(loadingOverlay::hide);
            }
        }).start();
    }

    private void handleNodeSelectedOnCanvas(QueryFlowNode node) {
        inspector.inspectNode(node);

        if (node != null && node.getSourceRange() != null) {
            SourceRange r = node.getSourceRange();
            if (r.getStartOffset() >= 0 && r.getEndOffset() <= txtSqlInput.getText().length()) {
                txtSqlInput.selectRange(r.getStartOffset(), r.getEndOffset());
            }
        }
    }

    private void exportDiagramPng() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Xuất sơ đồ luồng dữ liệu");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Hình ảnh PNG (*.png)", "*.png"));
        chooser.setInitialFileName("sql_flow_" + queryName.toLowerCase().replace(" ", "_") + ".png");

        File file = chooser.showSaveDialog(getContent().getScene().getWindow());
        if (file != null) {
            try {
                visualizerCanvas.exportToPng(file);
                NotificationService ns = NotificationService.getInstance();
                if (ns != null) {
                    ns.showSuccess("Xuất PNG thành công", "Đã lưu ảnh sơ đồ tại: " + file.getAbsolutePath());
                }
            } catch (Exception ex) {
                log.error("Failed to export PNG: {}", ex.getMessage(), ex);
                NotificationService ns = NotificationService.getInstance();
                if (ns != null) {
                    ns.showError("Xuất file thất bại", ex.getMessage());
                }
            }
        }
    }

    public void setSqlAndAnalyze(String sql) {
        if (sql != null) {
            txtSqlInput.setText(sql);
            runAnalysis();
        }
    }

    public SqlVisualizerCanvas getCanvas() {
        return visualizerCanvas;
    }

    public SqlVisualizerInspector getInspector() {
        return inspector;
    }

    public QueryFlowGraph getActiveGraph() {
        return activeGraph;
    }
}
