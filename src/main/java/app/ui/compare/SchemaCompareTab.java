package app.ui.compare;

import app.application.AppContext;
import app.controllers.MainController;
import app.framework.async.LoadTask;
import app.framework.backend.BackendFlags;
import app.framework.compare.SchemaSnapshotLoader;
import app.framework.connection.ConnectionSessionManager;
import app.framework.connection.ProfileLookup;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import app.services.NotificationService;
import app.ui.components.*;
import app.ui.dialogs.DialogChrome;
import app.ui.editor.EmbeddedMonaco;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.schemacompare.SchemaCompareService;
import com.lltool.core.domain.schemacompare.model.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Modern Schema Compare & Migration Tab (Phase 3 - Slice 4).
 * Compares structure between Source and Target databases/schemas,
 * displays categorized diff matrix, side-by-side DDL viewer, and generates
 * transactional migration scripts.
 */
public class SchemaCompareTab extends SqlEditorTab {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompareTab.class);

    public record CompareEndpoint(ConnectionProfileDto profile, String catalog, String schema) {}

    private final SchemaCompareService compareService = new SchemaCompareService();

    private CompareEndpoint capturedSourceEndpoint;
    private CompareEndpoint capturedTargetEndpoint;
    private boolean isSwapping = false;
    private LoadTask<SchemaCompareResult> activeCompareTask;
    private final PauseTransition scriptDebounce = new PauseTransition(Duration.millis(150));

    // Endpoints State
    private ConnectionProfileDto sourceProfile;
    private ConnectionProfileDto targetProfile;
    private String sourceCatalog;
    private String sourceSchema;
    private String targetCatalog;
    private String targetSchema;

    // Compare Results
    private SchemaCompareResult compareResult;
    private final ObservableList<SchemaObjectDiff> activeDiffs = FXCollections.observableArrayList();
    private SchemaObjectDiff selectedDiff;

    // Options
    private boolean wrapTransaction = true;
    private boolean includeDropTables = false;
    private String currentFilterMode = "ALL"; // ALL, DIFF, NEW, DEL, MOD

    // UI Controls - Header Bar
    private final ComboBox<ConnectionProfileDto> cbSourceProfile = new ComboBox<>();
    private final ComboBox<String> cbSourceCatalog = new ComboBox<>();
    private final ComboBox<String> cbSourceSchema = new ComboBox<>();

    private final ComboBox<ConnectionProfileDto> cbTargetProfile = new ComboBox<>();
    private final ComboBox<String> cbTargetCatalog = new ComboBox<>();
    private final ComboBox<String> cbTargetSchema = new ComboBox<>();

    private final IconButton btnSwap = new IconButton("fth-repeat", "Đảo chiều so sánh");
    private final AppButton btnOptions = new AppButton("Options", "fth-sliders", AppButton.Variant.DEFAULT);
    private final AppButton btnRunCompare = new AppButton("So sánh Schema", "fth-play", AppButton.Variant.PRIMARY);

    // UI Controls - Filter & Metrics Bar
    private final Label lblDiffSummaryBadge = new Label("Chưa so sánh");
    private final SegmentedToggle<String> filterToggle = new SegmentedToggle<>();

    private final Hyperlink linkSelectAll = new Hyperlink("Chọn tất cả");
    private final Hyperlink linkDeselectAll = new Hyperlink("Bỏ chọn");
    private final AppButton btnDeployScript = new AppButton("Sinh script", "fth-code", AppButton.Variant.SUCCESS);

    // UI Controls - Master Tree
    private final SearchField quickFilterField = new SearchField("Lọc danh sách đối tượng...");
    private final TreeView<SchemaTreeNode> diffTreeView = new TreeView<>();
    private final TreeItem<SchemaTreeNode> rootTreeItem = new TreeItem<>(SchemaTreeNode.createRoot());
    private final StackPane masterContentStack = new StackPane();
    private final EmptyState emptyState = new EmptyState("fth-git-commit", "Chưa so sánh Schema", "Chọn CSDL Nguồn và CSDL Đích rồi bấm So sánh Schema.");
    private LoadingOverlay loadingOverlay;

    // UI Controls - Detail Side-by-side DDL Viewers
    private PaneHeader sourceHeader;
    private PaneHeader targetHeader;
    private EmbeddedMonaco sourceEditor;
    private EmbeddedMonaco targetEditor;

    // UI Controls - Script Drawer (Bottom)
    private final SplitPane detailSplitPane = new SplitPane();
    private VBox scriptDrawer;
    private final Label lblScriptBadge = new Label("0 đối tượng");
    private final IconButton btnCopyScript = new IconButton("fth-copy", "Copy Script");
    private final AppButton btnOpenScriptInSql = new AppButton("Mở vào Tab SQL", "fth-external-link", AppButton.Variant.PRIMARY, AppButton.Size.SMALL);
    private EmbeddedMonaco scriptEditor;

    // UI Controls - Footer
    private final WorkspaceFooter workspaceFooter = new WorkspaceFooter();
    private final Label lblFooterStatus = new Label("Sẵn sàng");

    public SchemaCompareTab(
            ConnectionProfileDto sourceProfile,
            String sourceCatalog,
            String sourceSchema,
            ConnectionProfileDto targetProfile,
            String targetCatalog,
            String targetSchema,
            Consumer<SqlEditorTabAction> actionHandler) {

        super(
                "Schema Compare",
                "",
                null,
                sourceProfile != null ? sourceProfile.name() : "Schema",
                sourceCatalog,
                sourceSchema,
                actionHandler
        );

        this.sourceProfile = sourceProfile;
        this.sourceCatalog = sourceCatalog;
        this.sourceSchema = sourceSchema;
        this.targetProfile = targetProfile;
        this.targetCatalog = targetCatalog;
        this.targetSchema = targetSchema;

        setFileIconLiteral("fth-git-commit");
        setTabContentType(TabContentType.SCHEMA_COMPARE);
        initializeView();
    }

    private void initializeView() {
        VBox root = new VBox();
        root.getStyleClass().add("schema-compare-root");

        var cssRes = getClass().getResource("/css/schema-compare.css");
        if (cssRes != null) {
            root.getStylesheets().add(cssRes.toExternalForm());
        }

        // 1. Build Header Bar (Endpoints + Metrics)
        VBox headerBar = buildHeaderBar();

        // 2. Build SplitPane (Master Diff Tree vs Detail Viewers)
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.getStyleClass().add("compare-split-pane");
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);

        VBox masterPane = buildMasterPane();
        VBox detailPane = buildDetailPane();

        mainSplitPane.getItems().addAll(masterPane, detailPane);
        mainSplitPane.setDividerPositions(0.32);
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);

        // 3. Build Footer Bar
        WorkspaceFooter footer = buildFooter();

        root.getChildren().addAll(headerBar, mainSplitPane, footer);

        String tabKey = "schema_compare_" + System.currentTimeMillis();
        setCustomContent(tabKey, root);

        // 4. Wire events and populate profile combos
        scriptDebounce.setOnFinished(e -> generateAndDisplayScript());
        setupEvents(root);
        populateEndpoints();
    }

    private VBox buildHeaderBar() {
        VBox header = new VBox(8);
        header.getStyleClass().add("schema-compare-header");

        // Source Box
        HBox srcBox = new HBox(6);
        srcBox.getStyleClass().add("endpoint-box");
        HBox.setHgrow(srcBox, Priority.ALWAYS);

        Label lblSourceBadge = new Label("SOURCE");
        lblSourceBadge.getStyleClass().add("endpoint-badge-source");

        cbSourceProfile.setPromptText("Kết nối Nguồn");
        cbSourceProfile.setMinWidth(120);
        cbSourceCatalog.setPromptText("Database");
        cbSourceCatalog.setMinWidth(120);
        cbSourceSchema.setPromptText("Schema");
        cbSourceSchema.setMinWidth(100);
        cbSourceSchema.setEditable(true);

        HBox.setHgrow(cbSourceProfile, Priority.ALWAYS);
        HBox.setHgrow(cbSourceCatalog, Priority.ALWAYS);
        HBox.setHgrow(cbSourceSchema, Priority.ALWAYS);

        srcBox.getChildren().addAll(lblSourceBadge, cbSourceProfile, new Label("/"), cbSourceCatalog, cbSourceSchema);

        // Swap Button
        btnSwap.getStyleClass().add("swap-btn");
        btnSwap.setOnAction(e -> swapEndpoints());

        // Target Box
        HBox tgtBox = new HBox(6);
        tgtBox.getStyleClass().add("endpoint-box");
        HBox.setHgrow(tgtBox, Priority.ALWAYS);

        Label lblTargetBadge = new Label("TARGET");
        lblTargetBadge.getStyleClass().add("endpoint-badge-target");

        cbTargetProfile.setPromptText("Kết nối Đích");
        cbTargetProfile.setMinWidth(120);
        cbTargetCatalog.setPromptText("Database");
        cbTargetCatalog.setMinWidth(120);
        cbTargetSchema.setPromptText("Schema");
        cbTargetSchema.setMinWidth(100);
        cbTargetSchema.setEditable(true);

        HBox.setHgrow(cbTargetProfile, Priority.ALWAYS);
        HBox.setHgrow(cbTargetCatalog, Priority.ALWAYS);
        HBox.setHgrow(cbTargetSchema, Priority.ALWAYS);

        tgtBox.getChildren().addAll(lblTargetBadge, cbTargetProfile, new Label("/"), cbTargetCatalog, cbTargetSchema);

        // Action Buttons
        btnOptions.setOnAction(e -> showOptionsDialog());
        btnRunCompare.setOnAction(e -> executeCompareAsync());
        HBox actionBox = new HBox(6, btnOptions, btnRunCompare);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        // Responsive container for endpoints
        VBox endpointsContainer = new VBox(6);
        HBox row1Wide = new HBox(8);
        row1Wide.setAlignment(Pos.CENTER_LEFT);
        HBox row1NarrowTop = new HBox(8);
        row1NarrowTop.setAlignment(Pos.CENTER_LEFT);
        HBox row1NarrowBottom = new HBox(8);
        row1NarrowBottom.setAlignment(Pos.CENTER_LEFT);

        Runnable updateEndpointsLayout = () -> {
            boolean narrow = header.getWidth() > 0 && header.getWidth() < 1400;
            endpointsContainer.getChildren().clear();
            if (narrow) {
                row1NarrowTop.getChildren().setAll(srcBox, btnSwap);
                row1NarrowBottom.getChildren().setAll(tgtBox, actionBox);
                endpointsContainer.getChildren().setAll(row1NarrowTop, row1NarrowBottom);
            } else {
                row1Wide.getChildren().setAll(srcBox, btnSwap, tgtBox, actionBox);
                endpointsContainer.getChildren().setAll(row1Wide);
            }
        };
        header.widthProperty().addListener((obs, oldW, newW) -> updateEndpointsLayout.run());
        row1Wide.getChildren().setAll(srcBox, btnSwap, tgtBox, actionBox);
        endpointsContainer.getChildren().setAll(row1Wide);

        // Row 2: Filter & Metrics Bar
        HBox row2 = new HBox(8);
        row2.getStyleClass().add("compare-filter-bar");
        row2.setAlignment(Pos.CENTER_LEFT);

        Label lblMetricsTitle = new Label("Tổng khác biệt:");
        lblMetricsTitle.getStyleClass().add("compare-metrics-title");

        lblDiffSummaryBadge.getStyleClass().addAll("badge", "badge-warning");

        // SegmentedToggle for filtering
        filterToggle.addItem("ALL", "Tất cả (0)", "fth-layers");
        filterToggle.addItem("DIFF", "Khác biệt (0)", "fth-alert-circle");
        filterToggle.addItem("NEW", "Chỉ có ở nguồn (0)", "fth-plus-circle");
        filterToggle.addItem("DEL", "Chỉ có ở đích (0)", "fth-minus-circle");
        filterToggle.addItem("MOD", "Khác nhau (0)", "fth-edit-2");
        filterToggle.select("ALL");
        filterToggle.selectedValueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                applyFilterMode(newV);
            }
        });

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        linkSelectAll.setOnAction(e -> toggleAllSelection(true));
        linkDeselectAll.setOnAction(e -> toggleAllSelection(false));
        btnDeployScript.setOnAction(e -> generateAndDisplayScript());

        row2.getChildren().addAll(
                lblMetricsTitle, lblDiffSummaryBadge,
                new Separator(Orientation.VERTICAL),
                filterToggle,
                filterSpacer,
                linkSelectAll, new Label("·"), linkDeselectAll,
                btnDeployScript
        );

        header.getChildren().addAll(endpointsContainer, row2);
        return header;
    }

    private VBox buildMasterPane() {
        VBox pane = new VBox();
        pane.getStyleClass().add("object-search-master");
        pane.setMinWidth(300);
        pane.setPrefWidth(380);

        // Quick filter box
        VBox headerBox = new VBox(6);
        headerBox.getStyleClass().add("master-header-box");
        headerBox.getChildren().add(quickFilterField);

        // Diff TreeView
        diffTreeView.getStyleClass().add("diff-tree-view");
        diffTreeView.setShowRoot(false);
        diffTreeView.setRoot(rootTreeItem);
        diffTreeView.setCellFactory(tv -> new SchemaTreeCell());

        diffTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.getValue().isCategory()) {
                displayDiffObject(newVal.getValue().getDiff());
            }
        });

        // Content Stack
        masterContentStack.getChildren().addAll(diffTreeView, emptyState);
        VBox.setVgrow(masterContentStack, Priority.ALWAYS);

        loadingOverlay = LoadingOverlay.over(masterContentStack);

        pane.getChildren().addAll(headerBox, masterContentStack);
        return pane;
    }

    private VBox buildDetailPane() {
        VBox pane = new VBox();
        pane.getStyleClass().add("object-search-detail");

        detailSplitPane.setOrientation(Orientation.VERTICAL);
        detailSplitPane.getStyleClass().add("detail-split-pane");
        VBox.setVgrow(detailSplitPane, Priority.ALWAYS);

        // Top: Side-by-side DDL Viewers
        VBox topDiffBox = buildSideBySideDdlBox();

        // Bottom: Script Drawer
        scriptDrawer = buildScriptDrawer();

        detailSplitPane.getItems().addAll(topDiffBox, scriptDrawer);
        detailSplitPane.setDividerPositions(0.65);

        pane.getChildren().add(detailSplitPane);
        return pane;
    }

    private VBox buildSideBySideDdlBox() {
        VBox box = new VBox();
        box.getStyleClass().add("diff-viewer-container");
        VBox.setVgrow(box, Priority.ALWAYS);

        sourceHeader = new PaneHeader("SOURCE: (Chưa chọn đối tượng)", "fth-database", "DEV");
        targetHeader = new PaneHeader("TARGET: (Chưa chọn đối tượng)", "fth-database", "PROD");

        sourceEditor = new EmbeddedMonaco("compare_source_" + System.currentTimeMillis(), "sql", "-- SOURCE DDL");
        sourceEditor.setReadOnly(true);
        sourceEditor.setShowStatusBar(false);

        targetEditor = new EmbeddedMonaco("compare_target_" + System.currentTimeMillis(), "sql", "-- TARGET DDL");
        targetEditor.setReadOnly(true);
        targetEditor.setShowStatusBar(false);

        VBox leftPane = new VBox(sourceHeader, sourceEditor.node());
        VBox.setVgrow(sourceEditor.node(), Priority.ALWAYS);
        HBox.setHgrow(leftPane, Priority.ALWAYS);

        VBox rightPane = new VBox(targetHeader, targetEditor.node());
        VBox.setVgrow(targetEditor.node(), Priority.ALWAYS);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        SplitPane ddlSplit = new SplitPane(leftPane, rightPane);
        ddlSplit.setOrientation(Orientation.HORIZONTAL);
        ddlSplit.setDividerPositions(0.50);
        VBox.setVgrow(ddlSplit, Priority.ALWAYS);

        box.getChildren().add(ddlSplit);
        return box;
    }

    private VBox buildScriptDrawer() {
        VBox drawer = new VBox();
        drawer.getStyleClass().add("script-drawer");
        drawer.setPrefHeight(180);
        drawer.setMinHeight(28);

        // Header using PaneHeader
        PaneHeader header = new PaneHeader("Script đồng bộ", "fth-file-text", "");
        header.setRightNode(new HBox(6, lblScriptBadge, btnCopyScript, btnOpenScriptInSql));

        // Monaco Script Editor
        scriptEditor = new EmbeddedMonaco("migration_script_" + System.currentTimeMillis(), "sql", "-- Bấm 'Sinh script' để tạo script");
        scriptEditor.setReadOnly(false);
        scriptEditor.setShowStatusBar(false);
        VBox.setVgrow(scriptEditor.node(), Priority.ALWAYS);

        // Header click toggles collapse / expand
        header.setOnMouseClicked(e -> {
            boolean isVisible = scriptEditor.node().isVisible();
            boolean expand = !isVisible;
            scriptEditor.node().setVisible(expand);
            scriptEditor.node().setManaged(expand);
            drawer.setPrefHeight(expand ? 180 : 28);
        });

        drawer.getChildren().addAll(header, scriptEditor.node());
        return drawer;
    }

    private void toggleScriptDrawer(boolean show) {
        if (show) {
            if (!detailSplitPane.getItems().contains(scriptDrawer)) {
                detailSplitPane.getItems().add(scriptDrawer);
                detailSplitPane.setDividerPositions(0.65);
            }
        } else {
            detailSplitPane.getItems().remove(scriptDrawer);
        }
    }

    private WorkspaceFooter buildFooter() {
        workspaceFooter.addSection(lblFooterStatus);
        workspaceFooter.setSampleDataVisible(BackendFlags.mock(BackendFlags.Area.METADATA) || MockMetadataProvider.isEnabled());
        return workspaceFooter;
    }

    private void setupEvents(VBox root) {
        quickFilterField.setOnTextChange(filterText -> rebuildTree());

        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F5 || (e.isControlDown() && e.getCode() == KeyCode.R)) {
                executeCompareAsync();
                e.consume();
            } else if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.S) {
                generateAndDisplayScript();
                e.consume();
            }
        });
    }

    private void populateEndpoints() {
        List<ConnectionProfileDto> profiles = ProfileLookup.all();
        if (profiles.isEmpty()) {
            emptyState.setMessage("Không có kết nối nào trong hệ thống. Vui lòng tạo kết nối trước khi so sánh schema.");
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            diffTreeView.setVisible(false);
            btnRunCompare.setDisable(true);
            return;
        }

        cbSourceProfile.getItems().setAll(profiles);
        cbTargetProfile.getItems().setAll(profiles);

        // Pre-select source and target
        if (sourceProfile != null) {
            cbSourceProfile.setValue(sourceProfile);
        } else if (!profiles.isEmpty()) {
            cbSourceProfile.setValue(profiles.get(0));
        }

        if (targetProfile != null) {
            cbTargetProfile.setValue(targetProfile);
        } else if (profiles.size() > 1) {
            cbTargetProfile.setValue(profiles.get(1));
        } else if (!profiles.isEmpty()) {
            cbTargetProfile.setValue(profiles.get(0));
        }

        // Configure cell rendering
        cbSourceProfile.setCellFactory(lv -> new ProfileListCell());
        cbSourceProfile.setButtonCell(new ProfileListCell());
        cbTargetProfile.setCellFactory(lv -> new ProfileListCell());
        cbTargetProfile.setButtonCell(new ProfileListCell());

        // Catalogs & Schemas loaders
        cbSourceProfile.setOnAction(e -> {
            if (!isSwapping) {
                loadCatalogsForEndpoint(cbSourceProfile.getValue(), cbSourceCatalog, cbSourceSchema, sourceCatalog, sourceSchema);
                markResultStale();
            }
        });
        cbTargetProfile.setOnAction(e -> {
            if (!isSwapping) {
                loadCatalogsForEndpoint(cbTargetProfile.getValue(), cbTargetCatalog, cbTargetSchema, targetCatalog, targetSchema);
                markResultStale();
            }
        });

        loadCatalogsForEndpoint(cbSourceProfile.getValue(), cbSourceCatalog, cbSourceSchema, sourceCatalog, sourceSchema);
        loadCatalogsForEndpoint(cbTargetProfile.getValue(), cbTargetCatalog, cbTargetSchema, targetCatalog, targetSchema);
    }

    private void loadCatalogsForEndpoint(ConnectionProfileDto p, ComboBox<String> cbCat, ComboBox<String> cbSch, String initCat, String initSch) {
        if (p == null) {
            cbCat.getItems().clear();
            cbSch.getItems().clear();
            return;
        }

        DesktopMetadataService.getInstance().fetchCatalogs(p, dbs -> {
            if (dbs != null && !dbs.isEmpty()) {
                cbCat.getItems().setAll(dbs);
                if (initCat != null && dbs.contains(initCat)) {
                    cbCat.setValue(initCat);
                } else if (!dbs.isEmpty()) {
                    cbCat.setValue(dbs.get(0));
                }
                loadSchemasForEndpoint(p, cbCat.getValue(), cbSch, initSch);
            } else {
                cbCat.getItems().clear();
                cbSch.getItems().clear();
                NotificationService.getInstance().showWarning("Schema Compare", "Không thể lấy danh sách database từ kết nối " + p.name());
            }
        });

        cbCat.setOnAction(e -> {
            if (!isSwapping) {
                loadSchemasForEndpoint(p, cbCat.getValue(), cbSch, null);
                markResultStale();
            }
        });
    }

    private void loadSchemasForEndpoint(ConnectionProfileDto p, String cat, ComboBox<String> cbSch, String initSch) {
        if (p == null || cat == null) {
            cbSch.getItems().clear();
            return;
        }

        DesktopMetadataService.getInstance().fetchSchemas(p, cat, schemas -> {
            if (schemas != null && !schemas.isEmpty()) {
                cbSch.getItems().setAll(schemas);
                if (initSch != null && schemas.contains(initSch)) {
                    cbSch.setValue(initSch);
                } else if (!schemas.isEmpty()) {
                    cbSch.setValue(schemas.get(0));
                }
            } else {
                cbSch.getItems().clear();
                String defSch = (p.databaseType() != null && p.databaseType().toLowerCase().contains("postgre")) ? "public" : "dbo";
                cbSch.getItems().setAll(List.of(defSch));
                cbSch.setValue(defSch);
            }
        });
    }

    private void swapEndpoints() {
        isSwapping = true;
        try {
            ConnectionProfileDto p = cbSourceProfile.getValue();
            ObservableList<String> srcCats = FXCollections.observableArrayList(cbSourceCatalog.getItems());
            String c = cbSourceCatalog.getValue();
            ObservableList<String> srcSchs = FXCollections.observableArrayList(cbSourceSchema.getItems());
            String s = cbSourceSchema.getValue();

            cbSourceProfile.setValue(cbTargetProfile.getValue());
            cbSourceCatalog.setItems(FXCollections.observableArrayList(cbTargetCatalog.getItems()));
            cbSourceCatalog.setValue(cbTargetCatalog.getValue());
            cbSourceSchema.setItems(FXCollections.observableArrayList(cbTargetSchema.getItems()));
            cbSourceSchema.setValue(cbTargetSchema.getValue());

            cbTargetProfile.setValue(p);
            cbTargetCatalog.setItems(srcCats);
            cbTargetCatalog.setValue(c);
            cbTargetSchema.setItems(srcSchs);
            cbTargetSchema.setValue(s);

            markResultStale();
            NotificationService.getInstance().showInfo("Schema Compare", "Đã hoán đổi chiều so sánh giữa Source và Target.");
        } finally {
            isSwapping = false;
        }
    }

    private void markResultStale() {
        if (compareResult != null) {
            lblFooterStatus.setText("Cấu hình endpoints đã thay đổi — hãy bấm 'So sánh' để cập nhật kết quả.");
        }
    }

    private void showOptionsDialog() {
        VBox content = new VBox(12);
        content.getStyleClass().add("options-dialog-content");

        CheckBox chkTx = new CheckBox("Bọc trong giao dịch an toàn (BEGIN TRANSACTION / COMMIT)");
        chkTx.setSelected(wrapTransaction);

        CheckBox chkDrop = new CheckBox("Cho phép tạo lệnh DROP đối tượng thừa ở Target");
        chkDrop.setSelected(includeDropTables);

        HBox warnBanner = new HBox(8);
        warnBanner.setAlignment(Pos.CENTER_LEFT);
        warnBanner.getStyleClass().addAll("dialog-chrome-banner", "warning");
        FontIcon warnIcon = app.ui.components.Icons.of("fth-alert-triangle", app.ui.components.IconScale.LG);
        Label lblWarn = new Label("Cảnh báo: Thao tác DROP có thể làm mất dữ liệu vĩnh viễn trên CSDL Đích!");
        lblWarn.setWrapText(true);
        warnBanner.getChildren().addAll(warnIcon, lblWarn);
        warnBanner.visibleProperty().bind(chkDrop.selectedProperty());
        warnBanner.managedProperty().bind(chkDrop.selectedProperty());

        content.getChildren().addAll(chkTx, chkDrop, warnBanner);

        javafx.stage.Window owner = getTabPane() != null && getTabPane().getScene() != null ? getTabPane().getScene().getWindow() : null;
        DialogChrome chrome = DialogChrome.create(owner, "Tùy chọn So sánh & Di chuyển", "fth-sliders")
                .subtitle("Cấu hình phiên so sánh schema");

        AppButton btnApply = new AppButton("Áp dụng", "fth-check", AppButton.Variant.PRIMARY, AppButton.Size.SMALL);
        btnApply.setOnAction(e -> {
            this.wrapTransaction = chkTx.isSelected();
            this.includeDropTables = chkDrop.isSelected();
            chrome.close();
            if (compareResult != null) {
                scheduleScriptGeneration();
            }
        });

        AppButton btnCancel = new AppButton("Hủy", "fth-x", AppButton.Variant.DEFAULT, AppButton.Size.SMALL);
        btnCancel.setOnAction(e -> chrome.close());

        chrome.content(content).footer(null, btnCancel, btnApply);
        chrome.show();
    }

    public void executeCompareAsync() {
        ConnectionProfileDto srcP = cbSourceProfile.getValue();
        ConnectionProfileDto tgtP = cbTargetProfile.getValue();
        String srcDb = cbSourceCatalog.getValue();
        String tgtDb = cbTargetCatalog.getValue();
        String srcSch = cbSourceSchema.getValue();
        String tgtSch = cbTargetSchema.getValue();

        if (srcP == null || tgtP == null) {
            NotificationService.getInstance().showWarning("Thiếu thông tin kết nối", "Vui lòng chọn đầy đủ kết nối Nguồn và kết nối Đích.");
            lblFooterStatus.setText("Lỗi: Chưa chọn kết nối nguồn/đích");
            return;
        }

        String srcEngine = (srcP.databaseType() != null && srcP.databaseType().toLowerCase().contains("postgre")) ? "postgresql" : "sqlserver";
        String tgtEngine = (tgtP.databaseType() != null && tgtP.databaseType().toLowerCase().contains("postgre")) ? "postgresql" : "sqlserver";

        // SC-3: Database type check
        if (!srcEngine.equalsIgnoreCase(tgtEngine)) {
            NotificationService.getInstance().showWarning("Không thể so sánh",
                    String.format("Không thể so sánh giữa 2 hệ quản trị khác nhau (Source: %s, Target: %s).", srcEngine, tgtEngine));
            lblFooterStatus.setText("Lỗi: Không cùng loại hệ quản trị CSDL");
            return;
        }

        // SC-3: Same endpoint check
        boolean sameProfile = Objects.equals(srcP.id(), tgtP.id());
        boolean sameCatalog = Objects.equals(srcDb, tgtDb);
        boolean sameSchema = Objects.equals(srcSch != null ? srcSch.trim() : "", tgtSch != null ? tgtSch.trim() : "");
        if (sameProfile && sameCatalog && sameSchema) {
            NotificationService.getInstance().showWarning("Cảnh báo Endpoint", "Nguồn và Đích đang trỏ cùng một cơ sở dữ liệu và schema.");
            lblFooterStatus.setText("Cảnh báo: Nguồn và Đích trùng nhau hoàn toàn");
            return;
        }

        CompareEndpoint currentSrc = new CompareEndpoint(srcP, srcDb, srcSch);
        CompareEndpoint currentTgt = new CompareEndpoint(tgtP, tgtDb, tgtSch);

        loadingOverlay.show("Đang chuẩn bị so sánh metadata...", () -> {
            if (activeCompareTask != null) {
                activeCompareTask.cancel();
            }
            loadingOverlay.hide();
            btnRunCompare.setDisable(false);
            lblFooterStatus.setText("Đã hủy so sánh");
        });
        btnRunCompare.setDisable(true);
        long startTime = System.currentTimeMillis();

        if (activeCompareTask == null) {
            activeCompareTask = new LoadTask<>();
        }

        activeCompareTask.execute(
                ctx -> {
                    ctx.checkCancelled();
                    SchemaSnapshot srcSnap;
                    SchemaSnapshot tgtSnap;

                    if (BackendFlags.mock(BackendFlags.Area.METADATA) || MockMetadataProvider.isEnabled()) {
                        srcSnap = SchemaSnapshotLoader.createMockSnapshot(srcEngine, srcDb != null ? srcDb : "DEV", srcSch != null ? srcSch : "dbo", true);
                        tgtSnap = SchemaSnapshotLoader.createMockSnapshot(tgtEngine, tgtDb != null ? tgtDb : "PROD", tgtSch != null ? tgtSch : "dbo", false);
                    } else {
                        SchemaSnapshotLoader.SnapshotLoadResult srcRes = SchemaSnapshotLoader.load(srcP, srcDb, srcSch, true, msg -> {
                            Platform.runLater(() -> loadingOverlay.setMessage(msg));
                        });
                        ctx.checkCancelled();

                        SchemaSnapshotLoader.SnapshotLoadResult tgtRes = SchemaSnapshotLoader.load(tgtP, tgtDb, tgtSch, false, msg -> {
                            Platform.runLater(() -> loadingOverlay.setMessage(msg));
                        });
                        ctx.checkCancelled();

                        srcSnap = srcRes.snapshot();
                        tgtSnap = tgtRes.snapshot();
                    }

                    return compareService.compare(srcSnap, tgtSnap);
                },
                result -> {
                    loadingOverlay.hide();
                    btnRunCompare.setDisable(false);
                    long elapsed = System.currentTimeMillis() - startTime;

                    this.capturedSourceEndpoint = currentSrc;
                    this.capturedTargetEndpoint = currentTgt;
                    this.compareResult = result;
                    activeDiffs.setAll(result.getDiffs());

                    // Update Metrics Bar
                    SchemaCompareSummary s = result.getSummary();
                    lblDiffSummaryBadge.setText(s.getTotalDifferences() + " khác biệt");
                    if (filterToggle.getButton("ALL") != null) filterToggle.getButton("ALL").setText("Tất cả (" + s.totalObjects() + ")");
                    if (filterToggle.getButton("DIFF") != null) filterToggle.getButton("DIFF").setText("Khác biệt (" + s.getTotalDifferences() + ")");
                    if (filterToggle.getButton("NEW") != null) filterToggle.getButton("NEW").setText("Chỉ có ở nguồn (" + s.missingInTargetCount() + ")");
                    if (filterToggle.getButton("DEL") != null) filterToggle.getButton("DEL").setText("Chỉ có ở đích (" + s.extraInTargetCount() + ")");
                    if (filterToggle.getButton("MOD") != null) filterToggle.getButton("MOD").setText("Khác nhau (" + s.differentCount() + ")");

                    lblFooterStatus.setText(String.format("So sánh hoàn tất: %d khác biệt / %d đối tượng (%d ms)",
                            s.getTotalDifferences(), s.totalObjects(), elapsed));
                    workspaceFooter.setSampleDataVisible(BackendFlags.mock(BackendFlags.Area.METADATA) || MockMetadataProvider.isEnabled());

                    emptyState.setVisible(false);
                    emptyState.setManaged(false);
                    diffTreeView.setVisible(true);

                    rebuildTree();
                    generateAndDisplayScript();
                },
                throwable -> {
                    loadingOverlay.hide();
                    btnRunCompare.setDisable(false);
                    log.error("Schema compare error: {}", throwable.getMessage(), throwable);
                    NotificationService.getInstance().showError("Lỗi So sánh Schema", throwable.getMessage());
                    lblFooterStatus.setText("Lỗi: " + throwable.getMessage());
                },
                () -> {
                    loadingOverlay.hide();
                    btnRunCompare.setDisable(false);
                    lblFooterStatus.setText("Đã hủy so sánh");
                }
        );
    }

    private void applyFilterMode(String mode) {
        this.currentFilterMode = mode;
        rebuildTree();
    }

    private void rebuildTree() {
        rootTreeItem.getChildren().clear();
        if (compareResult == null) return;

        String query = quickFilterField.getText() != null ? quickFilterField.getText().trim().toLowerCase() : "";

        // Group by Object Types: TABLES, VIEWS, PROCEDURES, FUNCTIONS, TRIGGERS
        SchemaObjectType[] types = {
                SchemaObjectType.TABLE,
                SchemaObjectType.VIEW,
                SchemaObjectType.PROCEDURE,
                SchemaObjectType.FUNCTION,
                SchemaObjectType.TRIGGER
        };

        TreeItem<SchemaTreeNode> firstSelectable = null;
        TreeItem<SchemaTreeNode> itemToSelect = null;
        SchemaObjectDiff prevDiff = selectedDiff;

        for (SchemaObjectType type : types) {
            List<SchemaObjectDiff> matching = new ArrayList<>();
            int catDiffs = 0;

            for (SchemaObjectDiff d : activeDiffs) {
                if (d.getObjectType() != type) continue;

                // Mode filter
                boolean passMode = switch (currentFilterMode) {
                    case "DIFF" -> d.getStatus() != DiffStatus.IDENTICAL;
                    case "NEW" -> d.getStatus() == DiffStatus.MISSING_IN_TARGET;
                    case "DEL" -> d.getStatus() == DiffStatus.EXTRA_IN_TARGET;
                    case "MOD" -> d.getStatus() == DiffStatus.DIFFERENT;
                    default -> true;
                };
                if (!passMode) continue;

                // Text filter
                if (!query.isEmpty() && !d.getName().toLowerCase().contains(query) && !d.getSchema().toLowerCase().contains(query)) {
                    continue;
                }

                matching.add(d);
                if (d.getStatus() != DiffStatus.IDENTICAL) {
                    catDiffs++;
                }
            }

            // Category Node
            SchemaTreeNode catNode = SchemaTreeNode.createCategory(type, catDiffs);
            TreeItem<SchemaTreeNode> catItem = new TreeItem<>(catNode);

            for (SchemaObjectDiff d : matching) {
                TreeItem<SchemaTreeNode> item = new TreeItem<>(SchemaTreeNode.createObject(d));
                catItem.getChildren().add(item);
                if (prevDiff != null && d.getQualifiedName().equals(prevDiff.getQualifiedName())) {
                    itemToSelect = item;
                } else if (firstSelectable == null && d.getStatus() != DiffStatus.IDENTICAL) {
                    firstSelectable = item;
                }
            }

            catItem.setExpanded(!matching.isEmpty());
            rootTreeItem.getChildren().add(catItem);
        }

        if (itemToSelect != null) {
            diffTreeView.getSelectionModel().select(itemToSelect);
        } else if (firstSelectable != null) {
            diffTreeView.getSelectionModel().select(firstSelectable);
        }
    }

    private void displayDiffObject(SchemaObjectDiff diff) {
        this.selectedDiff = diff;
        if (diff == null) return;

        String srcName = (capturedSourceEndpoint != null && capturedSourceEndpoint.profile() != null ? capturedSourceEndpoint.profile().name() : (sourceProfile != null ? sourceProfile.name() : "DEV"));
        String tgtName = (capturedTargetEndpoint != null && capturedTargetEndpoint.profile() != null ? capturedTargetEndpoint.profile().name() : (targetProfile != null ? targetProfile.name() : "PROD"));

        String colStatsSrc = diff.getObjectType() == SchemaObjectType.TABLE ? (diff.getSourceColumnCount() + " cột") : diff.getObjectType().getDisplayName();
        String colStatsTgt = diff.getObjectType() == SchemaObjectType.TABLE ? (diff.getTargetColumnCount() + " cột") : diff.getObjectType().getDisplayName();

        if (sourceHeader != null) {
            sourceHeader.setTitle("SOURCE: " + diff.getQualifiedName());
            sourceHeader.setSubtitle(colStatsSrc + " · " + srcName);
        }

        if (targetHeader != null) {
            targetHeader.setTitle("TARGET: " + diff.getQualifiedName());
            targetHeader.setSubtitle(colStatsTgt + " · " + tgtName);
        }

        sourceEditor.setText(diff.getSourceDefinition().isEmpty() ? "-- Không tồn tại ở Source" : diff.getSourceDefinition());
        targetEditor.setText(diff.getTargetDefinition().isEmpty() ? "-- Không tồn tại ở Target" : diff.getTargetDefinition());
    }

    private void toggleAllSelection(boolean selected) {
        for (SchemaObjectDiff d : activeDiffs) {
            if (d.getStatus() != DiffStatus.IDENTICAL) {
                if (selected && d.isDestructive() && !includeDropTables) {
                    d.setSelected(false);
                } else {
                    d.setSelected(selected);
                }
            }
        }
        diffTreeView.refresh();
        scheduleScriptGeneration();
    }

    private void scheduleScriptGeneration() {
        scriptDebounce.playFromStart();
    }

    private void generateAndDisplayScript() {
        if (compareResult == null) return;

        Set<String> selectedNames = new HashSet<>();
        for (SchemaObjectDiff d : activeDiffs) {
            if (d.isSelected()) {
                selectedNames.add(d.getObjectType().name() + ":" + d.getQualifiedName());
                selectedNames.add(d.getQualifiedName());
            }
        }

        MigrationScriptResult res = compareService.generateMigrationScript(
                compareResult, selectedNames, includeDropTables, wrapTransaction
        );

        scriptEditor.setText(res.sql());
        lblScriptBadge.setText(String.format("%d đối tượng · %d lệnh DDL", res.objectCount(), res.statementCount()));
        toggleScriptDrawer(true);
    }

    private void copyScriptToClipboard() {
        String sql = scriptEditor.getText();
        if (sql == null || sql.isBlank() || sql.startsWith("-- Không có thay đổi")) {
            NotificationService.getInstance().showWarning("Copy", "Không có kịch bản SQL để sao chép.");
            return;
        }

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(sql);
        clipboard.setContent(content);

        NotificationService.getInstance().showSuccess("Copy", "Đã sao chép kịch bản SQL Migration vào clipboard.");
    }

    private void openScriptInSqlTab() {
        String sql = scriptEditor.getText();
        if (sql == null || sql.isBlank() || sql.startsWith("-- Không có thay đổi")) {
            return;
        }

        MainController mc = AppContext.getInstance().getMainController();
        if (mc != null) {
            String tgtCat = (capturedTargetEndpoint != null && capturedTargetEndpoint.catalog() != null) ? capturedTargetEndpoint.catalog() : targetCatalog;
            String tgtSch = (capturedTargetEndpoint != null && capturedTargetEndpoint.schema() != null) ? capturedTargetEndpoint.schema() : targetSchema;
            String title = "Migration_" + (tgtCat != null ? tgtCat : "Deploy") + ".sql";
            String conn = (capturedTargetEndpoint != null && capturedTargetEndpoint.profile() != null) ? capturedTargetEndpoint.profile().name() : (targetProfile != null ? targetProfile.name() : "");
            mc.openQueryConsole(title, sql, conn, tgtCat, tgtSch);
        }
    }

    // --- TreeNode Model ---
    public static class SchemaTreeNode {
        private final boolean category;
        private final SchemaObjectType objectType;
        private final int diffCount;
        private final SchemaObjectDiff diff;

        private SchemaTreeNode(boolean category, SchemaObjectType objectType, int diffCount, SchemaObjectDiff diff) {
            this.category = category;
            this.objectType = objectType;
            this.diffCount = diffCount;
            this.diff = diff;
        }

        public static SchemaTreeNode createRoot() {
            return new SchemaTreeNode(true, SchemaObjectType.TABLE, 0, null);
        }

        public static SchemaTreeNode createCategory(SchemaObjectType type, int diffCount) {
            return new SchemaTreeNode(true, type, diffCount, null);
        }

        public static SchemaTreeNode createObject(SchemaObjectDiff diff) {
            return new SchemaTreeNode(false, diff.getObjectType(), 0, diff);
        }

        public boolean isCategory() { return category; }
        public SchemaObjectType getObjectType() { return objectType; }
        public int getDiffCount() { return diffCount; }
        public SchemaObjectDiff getDiff() { return diff; }
    }

    // --- Custom TreeCell ---
    private class SchemaTreeCell extends TreeCell<SchemaTreeNode> {
        private final HBox rootCat = new HBox(6);
        private final FontIcon iconCat = new FontIcon();
        private final Label lblCatTitle = new Label();
        private final Badge lblCatBadge = Badge.neutral("");

        private final HBox rootObj = new HBox(6);
        private final CheckBox chkObj = new CheckBox();
        private final FontIcon iconObj = new FontIcon();
        private final Label lblObjName = new Label();
        private final Badge lblObjBadge = Badge.neutral("");

        public SchemaTreeCell() {
            // Category layout
            rootCat.setAlignment(Pos.CENTER_LEFT);
            app.ui.components.Icons.bind(iconCat, app.ui.components.IconScale.MD);
            lblCatTitle.getStyleClass().add("diff-category-title");

            Region catSpacer = new Region();
            HBox.setHgrow(catSpacer, Priority.ALWAYS);
            rootCat.getChildren().addAll(iconCat, lblCatTitle, catSpacer, lblCatBadge);

            // Object layout
            rootObj.setAlignment(Pos.CENTER_LEFT);
            app.ui.components.Icons.bind(iconObj, app.ui.components.IconScale.MD);
            lblObjName.getStyleClass().add("diff-object-title");

            Region objSpacer = new Region();
            HBox.setHgrow(objSpacer, Priority.ALWAYS);
            rootObj.getChildren().addAll(chkObj, iconObj, lblObjName, objSpacer, lblObjBadge);

            chkObj.setOnAction(e -> {
                SchemaTreeNode node = getItem();
                if (node != null && !node.isCategory() && node.getDiff() != null) {
                    node.getDiff().setSelected(chkObj.isSelected());
                    scheduleScriptGeneration();
                }
            });
        }

        @Override
        protected void updateItem(SchemaTreeNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                getStyleClass().removeAll("diff-category-cell");
            } else if (item.isCategory()) {
                if (!getStyleClass().contains("diff-category-cell")) {
                    getStyleClass().add("diff-category-cell");
                }
                iconCat.setIconLiteral(item.getObjectType().getIconLiteral());
                lblCatTitle.setText(item.getObjectType().getDisplayName().toUpperCase());
                lblCatBadge.setText(item.getDiffCount() + " diffs");
                lblCatBadge.setVariant(item.getDiffCount() > 0 ? Badge.Variant.WARNING : Badge.Variant.NEUTRAL);
                lblCatBadge.setVisible(item.getDiffCount() > 0);
                lblCatBadge.setManaged(item.getDiffCount() > 0);
                setGraphic(rootCat);
                setText(null);
            } else {
                getStyleClass().removeAll("diff-category-cell");
                SchemaObjectDiff d = item.getDiff();
                chkObj.setSelected(d.isSelected());
                chkObj.setDisable(d.getStatus() == DiffStatus.IDENTICAL);

                iconObj.setIconLiteral(d.getObjectType().getIconLiteral());
                lblObjName.setText(d.getQualifiedName());

                switch (d.getStatus()) {
                    case MISSING_IN_TARGET -> {
                        lblObjBadge.setText("Chỉ có ở nguồn");
                        lblObjBadge.setVariant(Badge.Variant.SUCCESS);
                    }
                    case EXTRA_IN_TARGET -> {
                        lblObjBadge.setText("Chỉ có ở đích");
                        lblObjBadge.setVariant(Badge.Variant.DANGER);
                    }
                    case DIFFERENT -> {
                        lblObjBadge.setText("Khác nhau");
                        lblObjBadge.setVariant(Badge.Variant.WARNING);
                    }
                    case IDENTICAL -> {
                        lblObjBadge.setText("Giống nhau");
                        lblObjBadge.setVariant(Badge.Variant.NEUTRAL);
                    }
                }

                if (d.isDestructive()) {
                    lblObjBadge.setText(lblObjBadge.getText() + " (Destructive)");
                    lblObjBadge.setVariant(Badge.Variant.DANGER);
                }

                setGraphic(rootObj);
                setText(null);
            }
        }
    }

    // --- Profile List Cell ---
    private static class ProfileListCell extends ListCell<ConnectionProfileDto> {
        @Override
        protected void updateItem(ConnectionProfileDto item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item.name());
            }
        }
    }

    // Getters for testing
    public ComboBox<ConnectionProfileDto> getCbSourceProfile() { return cbSourceProfile; }
    public ComboBox<ConnectionProfileDto> getCbTargetProfile() { return cbTargetProfile; }
    public AppButton getBtnRunCompare() { return btnRunCompare; }
    public TreeView<SchemaTreeNode> getDiffTreeView() { return diffTreeView; }
    public EmbeddedMonaco getSourceEditor() { return sourceEditor; }
    public EmbeddedMonaco getTargetEditor() { return targetEditor; }
    public EmbeddedMonaco getScriptEditor() { return scriptEditor; }
    public SchemaCompareResult getCompareResult() { return compareResult; }
    public ObservableList<SchemaObjectDiff> getActiveDiffs() { return activeDiffs; }
}
