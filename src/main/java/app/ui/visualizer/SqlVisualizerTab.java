package app.ui.visualizer;

import app.framework.async.AsyncTaskManager;
import app.framework.plan.DesktopExecutionPlanService;
import app.models.PhysicalExecutionPlan;
import app.models.PlanOperatorNode;
import app.services.NotificationService;
import app.ui.components.*;
import app.ui.editor.EmbeddedMonaco;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.visualizer.QueryFlowAnalyzer;
import com.lltool.core.domain.visualizer.QueryFlowGraph;
import com.lltool.core.domain.visualizer.QueryFlowNode;
import com.lltool.core.domain.visualizer.SourceRange;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
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
 * Fully refactored to conform to Phase 3 design tokens and component standards (SV-1..SV-7).
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
    private PauseTransition typingDebounce;

    // UI Controls - Header Toolbar
    private final AppButton btnAnalyze = new AppButton("Phân tích", "fth-play", AppButton.Variant.PRIMARY);
    private final ToggleButton btnToggleSqlBox = new ToggleButton("Hiện SQL");
    private final MenuButton btnPresets = new MenuButton("Mẫu");
    private final AppButton btnEstimatedPlan = new AppButton("Kế hoạch ước lượng", "fth-activity", AppButton.Variant.DEFAULT);

    private final IconButton btnZoomIn = new IconButton("fth-plus", "Phóng to (Ctrl++)");
    private final IconButton btnZoomOut = new IconButton("fth-minus", "Thu nhỏ (Ctrl+-)");
    private final IconButton btnFitScreen = new IconButton("fth-maximize-2", "Căn vừa màn hình (Ctrl+0)");
    private final IconButton btnResetZoom = new IconButton("fth-rotate-ccw", "Reset Zoom 100%");
    private final AppButton btnExportPng = new AppButton("Xuất PNG", "fth-download", AppButton.Variant.DEFAULT);

    // Collapsible SQL Input Box (EmbeddedMonaco)
    private final VBox sqlDrawer = new VBox();
    private EmbeddedMonaco monacoSql;

    // Main Canvas & Inspector
    private final StackPane canvasContainer = new StackPane();
    private final SqlVisualizerCanvas visualizerCanvas = new SqlVisualizerCanvas();
    private final SqlVisualizerInspector inspector = new SqlVisualizerInspector();
    private final SplitPane splitPane = new SplitPane();

    // Footer
    private final WorkspaceFooter footer = new WorkspaceFooter();
    private final Label lblStepStats = new Label("0 bước xử lý");

    private LoadingOverlay loadingOverlay;
    private final EmptyState emptyState = new EmptyState("fth-cpu", "Chưa có luồng dữ liệu", "Nhập câu lệnh SQL và bấm 'Phân tích' để trực quan hóa luồng dữ liệu.");

    public SqlVisualizerTab(ConnectionProfileDto profile, String catalog, String schema,
                            String queryName, String initialSql,
                            Consumer<SqlEditorTab.SqlEditorTabAction> tabActionHandler) {
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

        setTabContentType(TabContentType.SQL_VISUALIZER);
        buildLayout();
        initShortcuts();

        // Populate initial SQL or default preset 1
        String initText = (initialSql != null && !initialSql.isBlank())
                ? initialSql
                : QueryFlowAnalyzer.getPresetSql(1);
        monacoSql.setText(initText);

        Platform.runLater(this::runAnalysis);
    }

    public SqlVisualizerTab(ConnectionProfileDto profile, String catalog, String schema,
                            Consumer<SqlEditorTab.SqlEditorTabAction> tabActionHandler) {
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
        root.setMinWidth(0);

        // 1. Header Toolbar (WorkspaceToolbar - G-SV-1, G-SV-2, G-SV-7)
        WorkspaceToolbar toolbar = buildHeaderToolbar();
        toolbar.setMinWidth(0);

        // 2. Collapsible SQL Input Drawer (G-SV-4)
        buildSqlDrawer();
        sqlDrawer.setMinWidth(0);

        // 3. Canvas & Inspector SplitPane
        canvasContainer.getChildren().addAll(visualizerCanvas, emptyState);
        canvasContainer.setMinWidth(0);
        emptyState.setVisible(false);

        loadingOverlay = LoadingOverlay.over(canvasContainer);

        inspector.setMinWidth(0);

        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(sqlDrawer, canvasContainer, inspector);
        splitPane.setDividerPositions(0.25, 0.75);
        splitPane.setMinWidth(0);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // 4. Footer (G-SV-8)
        buildFooter();
        footer.setMinWidth(0);

        root.getChildren().addAll(toolbar, splitPane, footer);

        // G-SV-1: Use setCustomContent instead of setContent
        setCustomContent(customKey, root);

        // Wire Interactions
        visualizerCanvas.setOnNodeSelectedListener(this::handleNodeSelectedOnCanvas);

        // Monaco cursor sync to Canvas (G-SV-4)
        monacoSql.setOnCursorPositionChanged((line, col) -> {
            if (activeGraph != null && line != null && col != null) {
                QueryFlowNode matchedNode = activeGraph.findNodeAtPosition(line, col).orElse(null);
                if (matchedNode != null && matchedNode != visualizerCanvas.getSelectedNode()) {
                    visualizerCanvas.selectNode(matchedNode);
                }
            }
        });

        // Fast typing debounce (SV-5)
        monacoSql.setOnUserEdit(text -> {
            if (typingDebounce == null) {
                typingDebounce = new PauseTransition(Duration.millis(400));
                typingDebounce.setOnFinished(e -> runAnalysisQuick());
            }
            typingDebounce.playFromStart();
        });
    }

    private WorkspaceToolbar buildHeaderToolbar() {
        WorkspaceToolbar toolbar = new WorkspaceToolbar();

        // G-SV-7: Compact MenuButton "Mẫu"
        btnPresets.getStyleClass().addAll("btn-default", "btn-sm");
        FontIcon presetIcon = new FontIcon("fth-layers");
        presetIcon.setIconSize(13);
        btnPresets.setGraphic(presetIcon);

        MenuItem m1 = new MenuItem("Mẫu 1: Doanh thu theo khách hàng");
        MenuItem m2 = new MenuItem("Mẫu 2: Tồn kho & Đơn hàng");
        MenuItem m3 = new MenuItem("Mẫu 3: Đơn hàng chưa thanh toán");
        m1.setOnAction(e -> {
            monacoSql.setText(QueryFlowAnalyzer.getPresetSql(1));
            runAnalysis();
        });
        m2.setOnAction(e -> {
            monacoSql.setText(QueryFlowAnalyzer.getPresetSql(2));
            runAnalysis();
        });
        m3.setOnAction(e -> {
            monacoSql.setText(QueryFlowAnalyzer.getPresetSql(3));
            runAnalysis();
        });
        btnPresets.getItems().addAll(m1, m2, m3);

        // Analyze Button
        btnAnalyze.setOnAction(e -> runAnalysis());

        // Toggle SQL Box
        FontIcon codeIcon = new FontIcon("fth-code");
        codeIcon.setIconSize(13);
        btnToggleSqlBox.setGraphic(codeIcon);
        btnToggleSqlBox.setSelected(true);
        btnToggleSqlBox.getStyleClass().add("btn-toggle");
        btnToggleSqlBox.setOnAction(e -> {
            boolean show = btnToggleSqlBox.isSelected();
            if (show) {
                if (!splitPane.getItems().contains(sqlDrawer)) {
                    splitPane.getItems().add(0, sqlDrawer);
                    splitPane.setDividerPositions(0.25, 0.75);
                }
            } else {
                splitPane.getItems().remove(sqlDrawer);
            }
        });

        // Left Controls
        btnEstimatedPlan.setOnAction(e -> fetchEstimatedPlanMetrics());
        toolbar.addLeft(btnAnalyze);
        toolbar.addLeft(btnToggleSqlBox);
        toolbar.addLeft(btnPresets);
        toolbar.addLeft(btnEstimatedPlan);

        // Canvas Navigation Buttons
        Label lblZoom = new Label("100%");
        lblZoom.textProperty().bind(visualizerCanvas.zoomPercentProperty());
        lblZoom.getStyleClass().add("qf-control-label");
        lblZoom.setStyle("-fx-min-width: 38px; -fx-alignment: center;");

        btnZoomIn.setOnAction(e -> visualizerCanvas.zoomIn());
        btnZoomOut.setOnAction(e -> visualizerCanvas.zoomOut());
        btnFitScreen.setOnAction(e -> visualizerCanvas.fitToScreen());
        btnResetZoom.setOnAction(e -> visualizerCanvas.resetZoom());

        btnExportPng.setOnAction(e -> exportDiagramPng());

        toolbar.addRight(btnZoomIn);
        toolbar.addRight(btnZoomOut);
        toolbar.addRight(lblZoom);
        toolbar.addRight(btnFitScreen);
        toolbar.addRight(btnResetZoom);
        toolbar.addRight(new Separator(Orientation.VERTICAL));
        toolbar.addRight(btnExportPng);

        return toolbar;
    }

    private void buildSqlDrawer() {
        sqlDrawer.getStyleClass().add("qf-sql-drawer");
        sqlDrawer.setMinHeight(120);
        sqlDrawer.setPrefHeight(150);

        PaneHeader header = new PaneHeader("Câu lệnh SQL", "fth-code", "Phím tắt: F5 hoặc Ctrl+Enter để phân tích");

        String modelId = "sql_vis_" + UUID.randomUUID();
        monacoSql = new EmbeddedMonaco(modelId, "sql", "-- Nhập câu lệnh SQL tại đây");
        monacoSql.setShowStatusBar(false);
        VBox.setVgrow(monacoSql.node(), Priority.ALWAYS);

        sqlDrawer.getChildren().addAll(header, monacoSql.node());
    }

    private void buildFooter() {
        FontIcon stepIcon = new FontIcon("fth-layers");
        stepIcon.setIconSize(13);
        stepIcon.getStyleClass().add("icon-accent");

        HBox statsBox = new HBox(6, stepIcon, lblStepStats);
        statsBox.setAlignment(Pos.CENTER_LEFT);
        footer.addSection(statsBox);

        footer.setSampleDataVisible(app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.METADATA) || app.framework.metadata.MockMetadataProvider.isEnabled());
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
        String sql = monacoSql.getText();
        if (sql == null || sql.isBlank()) {
            emptyState.setVisible(true);
            visualizerCanvas.clearCanvas();
            inspector.inspectNode(null);
            lblStepStats.setText("0 bước xử lý");
            return;
        }

        try {
            QueryFlowGraph graph = analyzer.analyze(sql);
            this.activeGraph = graph;
            if (graph.isUnsupported()) {
                emptyState.setTitle("Không hỗ trợ câu lệnh này");
                emptyState.setMessage(graph.getUnsupportedReason());
                emptyState.setVisible(true);
                visualizerCanvas.clearCanvas();
                inspector.inspectNode(null);
                lblStepStats.setText("0 bước xử lý");
            } else {
                emptyState.setVisible(graph.getNodeCount() == 0);
                visualizerCanvas.setGraph(graph);
                lblStepStats.setText(graph.getNodeCount() + " bước xử lý");
                Platform.runLater(visualizerCanvas::fitToScreen);
            }
        } catch (Exception ex) {
            log.error("Failed to analyze SQL flow: {}", ex.getMessage(), ex);
            NotificationService ns = NotificationService.getInstance();
            if (ns != null) {
                ns.showError("Lỗi phân tích SQL", ex.getMessage());
            }
        }
    }

    private void runAnalysisQuick() {
        String sql = monacoSql.getText();
        if (sql == null || sql.isBlank()) {
            emptyState.setVisible(true);
            visualizerCanvas.clearCanvas();
            inspector.inspectNode(null);
            lblStepStats.setText("0 bước xử lý");
            return;
        }

        try {
            QueryFlowGraph graph = analyzer.analyze(sql);
            this.activeGraph = graph;
            if (graph.isUnsupported()) {
                emptyState.setTitle("Không hỗ trợ câu lệnh này");
                emptyState.setMessage(graph.getUnsupportedReason());
                emptyState.setVisible(true);
                visualizerCanvas.clearCanvas();
                inspector.inspectNode(null);
                lblStepStats.setText("0 bước xử lý");
            } else {
                emptyState.setVisible(graph.getNodeCount() == 0);
                visualizerCanvas.setGraph(graph);
                lblStepStats.setText(graph.getNodeCount() + " bước xử lý");
            }
        } catch (Exception ex) {
            log.debug("Live typing parse exception: {}", ex.getMessage());
        }
    }

    private void fetchEstimatedPlanMetrics() {
        if (profile == null) {
            NotificationService.getInstance().showWarning("Kế hoạch ước lượng", "Cần kết nối CSDL để lấy kế hoạch ước lượng.");
            return;
        }
        String sql = monacoSql.getText();
        if (sql == null || sql.isBlank() || activeGraph == null) return;

        loadingOverlay.show("Đang lấy kế hoạch thực thi ước lượng...", null);
        AsyncTaskManager.getInstance().runInBackground(() -> {
            try {
                DesktopExecutionPlanService.getInstance().getExecutionPlanAsync(
                        profile.name(), catalog, schema, sql, "estimated",
                        new DesktopExecutionPlanService.PlanCallback() {
                            @Override
                            public void onStart(String planType, String dialect) {}

                            @Override
                            public void onSuccess(PhysicalExecutionPlan plan) {
                                Platform.runLater(() -> {
                                    if (plan != null && activeGraph != null) {
                                        applyPlanMetricsToGraph(activeGraph, plan);
                                        visualizerCanvas.setGraph(activeGraph);
                                        NotificationService.getInstance().showSuccess("Kế hoạch ước lượng", "Đã cập nhật số liệu ước lượng vào sơ đồ.");
                                    }
                                    loadingOverlay.hide();
                                });
                            }

                            @Override
                            public void onError(Throwable error) {
                                Platform.runLater(() -> {
                                    loadingOverlay.hide();
                                    NotificationService.getInstance().showWarning("Kế hoạch ước lượng", "Không thể lấy kế hoạch: " + error.getMessage());
                                });
                            }
                        }
                );
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loadingOverlay.hide();
                    NotificationService.getInstance().showWarning("Kế hoạch ước lượng", "Không thể lấy kế hoạch: " + ex.getMessage());
                });
            }
        });
    }

    private void applyPlanMetricsToGraph(QueryFlowGraph graph, PhysicalExecutionPlan plan) {
        if (graph == null || plan == null || plan.getAllNodes() == null) return;

        for (QueryFlowNode qNode : graph.getNodes().values()) {
            for (PlanOperatorNode pNode : plan.getAllNodes()) {
                boolean match = false;
                if (qNode.getType() == com.lltool.core.domain.visualizer.QueryNodeType.SOURCE_TABLE ||
                    qNode.getType() == com.lltool.core.domain.visualizer.QueryNodeType.JOIN_TABLE) {
                    if (pNode.getRelationName() != null && !pNode.getRelationName().isBlank()) {
                        String cleanP = pNode.getRelationName().replace("[", "").replace("]", "").replace("\"", "");
                        String cleanQ = qNode.getTitle().replace("[", "").replace("]", "").replace("\"", "");
                        if (cleanP.equalsIgnoreCase(cleanQ) || cleanP.endsWith("." + cleanQ) || cleanQ.endsWith("." + cleanP)) {
                            match = true;
                        }
                    }
                } else if (qNode.getType() == com.lltool.core.domain.visualizer.QueryNodeType.JOIN) {
                    if (pNode.getOperatorType() != null && pNode.getOperatorType().toLowerCase().contains("join")) {
                        match = true;
                    }
                } else if (qNode.getType() == com.lltool.core.domain.visualizer.QueryNodeType.AGGREGATE) {
                    if (pNode.getOperatorType() != null && (pNode.getOperatorType().toLowerCase().contains("hash") || pNode.getOperatorType().toLowerCase().contains("aggregate"))) {
                        match = true;
                    }
                } else if (qNode.getType() == com.lltool.core.domain.visualizer.QueryNodeType.SORT) {
                    if (pNode.getOperatorType() != null && pNode.getOperatorType().toLowerCase().contains("sort")) {
                        match = true;
                    }
                }

                if (match) {
                    if (pNode.getEstimatedRows() > 0) {
                        qNode.setRowCount((long) pNode.getEstimatedRows());
                    }
                    if (pNode.getCostPercent() > 0) {
                        qNode.setEstimatedCost(String.format("%.1f%%", pNode.getCostPercent()));
                    }
                    break;
                }
            }
        }
    }

    private void handleNodeSelectedOnCanvas(QueryFlowNode node) {
        inspector.inspectNode(node);

        if (node != null && node.getSourceRange() != null) {
            SourceRange r = node.getSourceRange();
            if (r.getStartLine() > 0) {
                monacoSql.setActiveSearchMatch(r.getStartLine(), r.getStartCol(), Math.max(1, r.getEndOffset() - r.getStartOffset()));
                monacoSql.gotoLine(r.getStartLine());
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
            boolean ok = visualizerCanvas.exportToPng(file);
            NotificationService ns = NotificationService.getInstance();
            if (ok) {
                if (ns != null) {
                    ns.showSuccess("Xuất PNG thành công", "Đã lưu ảnh sơ đồ tại: " + file.getAbsolutePath());
                }
            } else {
                if (ns != null) {
                    ns.showError("Xuất file thất bại", "Không thể ghi tệp hình ảnh PNG.");
                }
            }
        }
    }

    public void setSqlAndAnalyze(String sql) {
        if (sql != null) {
            monacoSql.setText(sql);
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
