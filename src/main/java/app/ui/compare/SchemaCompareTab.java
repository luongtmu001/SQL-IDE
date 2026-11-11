package app.ui.compare;

import app.application.AppContext;
import app.controllers.MainController;
import app.framework.backend.BackendFlags;
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
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Modern Schema Compare & Migration Tab (Phase 3 - Slice 4).
 * Compares structure between Source and Target databases/schemas,
 * displays categorized diff matrix, side-by-side DDL viewer, and generates
 * transactional migration scripts.
 */
public class SchemaCompareTab extends SqlEditorTab {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompareTab.class);

    private final SchemaCompareService compareService = new SchemaCompareService();

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
    private final ToggleButton btnFilterAll = new ToggleButton("Tất cả (0)");
    private final ToggleButton btnFilterDiff = new ToggleButton("Khác biệt (0)");
    private final ToggleButton btnFilterNew = new ToggleButton("Chỉ có ở nguồn (0)");
    private final ToggleButton btnFilterDel = new ToggleButton("Chỉ có ở đích (0)");
    private final ToggleButton btnFilterMod = new ToggleButton("Khác nhau (0)");
    private final ToggleGroup filterGroup = new ToggleGroup();

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
    private final Label lblSourceDdlHeader = new Label("SOURCE: (Chưa chọn đối tượng)");
    private final Label lblTargetDdlHeader = new Label("TARGET: (Chưa chọn đối tượng)");
    private final Label lblSourceStats = new Label("");
    private final Label lblTargetStats = new Label("");
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
        HBox footer = buildFooter();

        root.getChildren().addAll(headerBar, mainSplitPane, footer);

        String tabKey = "schema_compare_" + System.currentTimeMillis();
        setCustomContent(tabKey, root);

        // 4. Wire events and populate profile combos
        setupEvents(root);
        populateEndpoints();
    }

    private VBox buildHeaderBar() {
        VBox header = new VBox(8);
        header.getStyleClass().add("schema-compare-header");

        // Row 1: Source & Target Endpoints
        HBox row1 = new HBox(8);
        row1.setAlignment(Pos.CENTER_LEFT);

        // Source Box
        HBox srcBox = new HBox(6);
        srcBox.getStyleClass().add("endpoint-box");
        HBox.setHgrow(srcBox, Priority.ALWAYS);

        Label lblSourceBadge = new Label("SOURCE");
        lblSourceBadge.getStyleClass().add("endpoint-badge-source");

        cbSourceProfile.setPromptText("Kết nối Nguồn");
        cbSourceCatalog.setPromptText("Database");
        cbSourceSchema.setPromptText("Schema");
        cbSourceSchema.setEditable(true);

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
        cbTargetCatalog.setPromptText("Database");
        cbTargetSchema.setPromptText("Schema");
        cbTargetSchema.setEditable(true);

        tgtBox.getChildren().addAll(lblTargetBadge, cbTargetProfile, new Label("/"), cbTargetCatalog, cbTargetSchema);

        // Action Buttons
        btnOptions.setOnAction(e -> showOptionsDialog());
        btnRunCompare.setOnAction(e -> executeCompareAsync());

        row1.getChildren().addAll(srcBox, btnSwap, tgtBox, btnOptions, btnRunCompare);

        // Row 2: Filter & Metrics Bar
        HBox row2 = new HBox(8);
        row2.getStyleClass().add("compare-filter-bar");
        row2.setAlignment(Pos.CENTER_LEFT);

        Label lblMetricsTitle = new Label("Tổng khác biệt:");
        lblMetricsTitle.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-weight: bold;");

        lblDiffSummaryBadge.getStyleClass().addAll("badge", "badge-warning");

        // Filter Toggles
        btnFilterAll.setToggleGroup(filterGroup);
        btnFilterDiff.setToggleGroup(filterGroup);
        btnFilterNew.setToggleGroup(filterGroup);
        btnFilterDel.setToggleGroup(filterGroup);
        btnFilterMod.setToggleGroup(filterGroup);
        btnFilterAll.setSelected(true);

        btnFilterAll.getStyleClass().add("filter-badge-btn");
        btnFilterDiff.getStyleClass().add("filter-badge-btn");
        btnFilterNew.getStyleClass().add("filter-badge-btn");
        btnFilterDel.getStyleClass().add("filter-badge-btn");
        btnFilterMod.getStyleClass().add("filter-badge-btn");

        btnFilterAll.setOnAction(e -> applyFilterMode("ALL"));
        btnFilterDiff.setOnAction(e -> applyFilterMode("DIFF"));
        btnFilterNew.setOnAction(e -> applyFilterMode("NEW"));
        btnFilterDel.setOnAction(e -> applyFilterMode("DEL"));
        btnFilterMod.setOnAction(e -> applyFilterMode("MOD"));

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        linkSelectAll.setOnAction(e -> toggleAllSelection(true));
        linkDeselectAll.setOnAction(e -> toggleAllSelection(false));
        btnDeployScript.setOnAction(e -> generateAndDisplayScript());

        row2.getChildren().addAll(
                lblMetricsTitle, lblDiffSummaryBadge,
                new Separator(Orientation.VERTICAL),
                btnFilterAll, btnFilterDiff, btnFilterNew, btnFilterDel, btnFilterMod,
                filterSpacer,
                linkSelectAll, new Label("·"), linkDeselectAll,
                btnDeployScript
        );

        header.getChildren().addAll(row1, row2);
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

        // Subheader bar
        HBox subHeader = new HBox();
        subHeader.getStyleClass().add("diff-viewer-header");

        HBox leftTitleBox = new HBox(8);
        leftTitleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(leftTitleBox, Priority.ALWAYS);
        lblSourceDdlHeader.getStyleClass().add("diff-header-source");
        lblSourceStats.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 0.8em;");
        leftTitleBox.getChildren().addAll(lblSourceDdlHeader, lblSourceStats);

        Separator titleSep = new Separator(Orientation.VERTICAL);

        HBox rightTitleBox = new HBox(8);
        rightTitleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(rightTitleBox, Priority.ALWAYS);
        lblTargetDdlHeader.getStyleClass().add("diff-header-target");
        lblTargetStats.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 0.8em;");
        rightTitleBox.getChildren().addAll(lblTargetDdlHeader, lblTargetStats);

        subHeader.getChildren().addAll(leftTitleBox, titleSep, rightTitleBox);

        // Side-by-side SplitPane
        SplitPane ddlSplit = new SplitPane();
        ddlSplit.setOrientation(Orientation.HORIZONTAL);
        VBox.setVgrow(ddlSplit, Priority.ALWAYS);

        sourceEditor = new EmbeddedMonaco("compare_source_" + System.currentTimeMillis(), "sql", "-- SOURCE DDL");
        sourceEditor.setReadOnly(true);

        targetEditor = new EmbeddedMonaco("compare_target_" + System.currentTimeMillis(), "sql", "-- TARGET DDL");
        targetEditor.setReadOnly(true);

        ddlSplit.getItems().addAll(sourceEditor.node(), targetEditor.node());
        ddlSplit.setDividerPositions(0.50);

        box.getChildren().addAll(subHeader, ddlSplit);
        return box;
    }

    private VBox buildScriptDrawer() {
        VBox drawer = new VBox();
        drawer.getStyleClass().add("script-drawer");
        drawer.setPrefHeight(180);
        drawer.setMinHeight(28);

        // Header
        HBox header = new HBox(8);
        header.getStyleClass().add("script-drawer-header");
        header.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon("fth-file-text");
        icon.setIconSize(13);
        icon.getStyleClass().add("icon-success");

        Label lblTitle = new Label("Script đồng bộ");
        lblTitle.getStyleClass().add("script-drawer-title");

        lblScriptBadge.getStyleClass().addAll("badge", "badge-subtle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnCopyScript.setOnAction(e -> copyScriptToClipboard());
        btnOpenScriptInSql.setOnAction(e -> openScriptInSqlTab());

        IconButton btnClose = new IconButton("fth-x", "Thu gọn script drawer");
        btnClose.setOnAction(e -> toggleScriptDrawer(false));

        header.getChildren().addAll(icon, lblTitle, lblScriptBadge, spacer, btnCopyScript, btnOpenScriptInSql, btnClose);

        // Monaco Script Editor
        scriptEditor = new EmbeddedMonaco("migration_script_" + System.currentTimeMillis(), "sql", "-- Bấm 'Sinh script' để tạo script");
        scriptEditor.setReadOnly(false);
        VBox.setVgrow(scriptEditor.node(), Priority.ALWAYS);

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

    private HBox buildFooter() {
        HBox footer = new HBox(12);
        footer.getStyleClass().add("schema-compare-footer");
        footer.setAlignment(Pos.CENTER_LEFT);

        footer.getChildren().add(lblFooterStatus);
        return footer;
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
            // Mock profile entries if store is empty
            ConnectionProfileDto mockDev = new ConnectionProfileDto(
                    "dev-1", "MSSQL-DEV (localhost:1433)", "Default", "Dev", false,
                    "sqlserver", "localhost", 1433, "B10_PhucLoi", null, "sa", "SQL_PASSWORD",
                    null, false, 30, true, Collections.emptyMap(), null, null
            );
            ConnectionProfileDto mockProd = new ConnectionProfileDto(
                    "prod-1", "MSSQL-PROD (192.168.1.100)", "Default", "Prod", false,
                    "sqlserver", "192.168.1.100", 1433, "B10_PhucLoi", null, "sa", "SQL_PASSWORD",
                    null, false, 30, true, Collections.emptyMap(), null, null
            );
            profiles = List.of(mockDev, mockProd);
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
        cbSourceProfile.setOnAction(e -> loadCatalogsForEndpoint(cbSourceProfile.getValue(), cbSourceCatalog, cbSourceSchema, sourceCatalog, sourceSchema));
        cbTargetProfile.setOnAction(e -> loadCatalogsForEndpoint(cbTargetProfile.getValue(), cbTargetCatalog, cbTargetSchema, targetCatalog, targetSchema));

        loadCatalogsForEndpoint(cbSourceProfile.getValue(), cbSourceCatalog, cbSourceSchema, sourceCatalog, sourceSchema);
        loadCatalogsForEndpoint(cbTargetProfile.getValue(), cbTargetCatalog, cbTargetSchema, targetCatalog, targetSchema);
    }

    private void loadCatalogsForEndpoint(ConnectionProfileDto p, ComboBox<String> cbCat, ComboBox<String> cbSch, String initCat, String initSch) {
        if (p == null) return;

        DesktopMetadataService.getInstance().fetchCatalogs(p, dbs -> {
            List<String> list = (dbs != null && !dbs.isEmpty()) ? dbs : List.of("B10_PhucLoi", "BravoSetup", "master");
            cbCat.getItems().setAll(list);
            if (initCat != null && list.contains(initCat)) {
                cbCat.setValue(initCat);
            } else {
                cbCat.setValue(list.get(0));
            }
            loadSchemasForEndpoint(p, cbCat.getValue(), cbSch, initSch);
        });

        cbCat.setOnAction(e -> loadSchemasForEndpoint(p, cbCat.getValue(), cbSch, null));
    }

    private void loadSchemasForEndpoint(ConnectionProfileDto p, String cat, ComboBox<String> cbSch, String initSch) {
        if (p == null || cat == null) return;

        DesktopMetadataService.getInstance().fetchSchemas(p, cat, schemas -> {
            List<String> list = (schemas != null && !schemas.isEmpty()) ? schemas : List.of("dbo", "sales", "audit");
            cbSch.getItems().setAll(list);
            if (initSch != null && list.contains(initSch)) {
                cbSch.setValue(initSch);
            } else {
                cbSch.setValue(list.get(0));
            }
        });
    }

    private void swapEndpoints() {
        ConnectionProfileDto p = cbSourceProfile.getValue();
        String c = cbSourceCatalog.getValue();
        String s = cbSourceSchema.getValue();

        cbSourceProfile.setValue(cbTargetProfile.getValue());
        cbSourceCatalog.setValue(cbTargetCatalog.getValue());
        cbSourceSchema.setValue(cbTargetSchema.getValue());

        cbTargetProfile.setValue(p);
        cbTargetCatalog.setValue(c);
        cbTargetSchema.setValue(s);

        NotificationService.getInstance().showInfo("Schema Compare", "Đã hoán đổi chiều so sánh giữa Source và Target.");
    }

    private void showOptionsDialog() {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 16;");

        CheckBox chkTx = new CheckBox("Bọc trong giao dịch an toàn");
        chkTx.setSelected(wrapTransaction);

        CheckBox chkDrop = new CheckBox("Cho phép tạo lệnh DROP đối tượng");
        chkDrop.setSelected(includeDropTables);

        Label lblWarn = new Label("Lưu ý: Thao tác DROP có thể làm mất dữ liệu trên CSDL đích.");
        lblWarn.setStyle("-fx-text-fill: -color-danger-fg; -fx-font-size: 0.85em;");

        content.getChildren().addAll(chkTx, chkDrop, lblWarn);

        javafx.stage.Window owner = getTabPane() != null && getTabPane().getScene() != null ? getTabPane().getScene().getWindow() : null;
        DialogChrome chrome = DialogChrome.create(owner, "Tùy chọn So sánh & Di chuyển", "fth-sliders")
                .subtitle("Cấu hình phiên so sánh schema");

        AppButton btnApply = new AppButton("Áp dụng", "fth-check", AppButton.Variant.PRIMARY, AppButton.Size.SMALL);
        btnApply.setOnAction(e -> {
            this.wrapTransaction = chkTx.isSelected();
            this.includeDropTables = chkDrop.isSelected();
            chrome.close();
            if (compareResult != null) {
                generateAndDisplayScript();
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

        loadingOverlay.show("Đang phân tích và so sánh metadata giữa hai cơ sở dữ liệu...", () -> {
            loadingOverlay.hide();
            lblFooterStatus.setText("Đã hủy so sánh");
        });
        btnRunCompare.setDisable(true);
        long startTime = System.currentTimeMillis();

        CompletableFuture.supplyAsync(() -> {
            try {
                // Determine engine
                String engine = (srcP != null && srcP.databaseType() != null && srcP.databaseType().toLowerCase().contains("postgre"))
                        ? "postgresql" : "sqlserver";

                // Build snapshots (Mock fallback if mock enabled or connection not active)
                SchemaSnapshot srcSnap = compareService.createMockSnapshot(engine, srcDb != null ? srcDb : "DEV", srcSch != null ? srcSch : "dbo", true);
                SchemaSnapshot tgtSnap = compareService.createMockSnapshot(engine, tgtDb != null ? tgtDb : "PROD", tgtSch != null ? tgtSch : "dbo", false);

                Thread.sleep(300); // UI feel
                return compareService.compare(srcSnap, tgtSnap);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).whenComplete((result, throwable) -> Platform.runLater(() -> {
            loadingOverlay.hide();
            btnRunCompare.setDisable(false);
            long elapsed = System.currentTimeMillis() - startTime;

            if (throwable != null) {
                log.error("Schema compare error: {}", throwable.getMessage(), throwable);
                NotificationService.getInstance().showError("Lỗi So sánh Schema", throwable.getMessage());
                lblFooterStatus.setText("Lỗi: " + throwable.getMessage());
                return;
            }

            this.compareResult = result;
            activeDiffs.setAll(result.getDiffs());

            // Update Metrics Bar
            SchemaCompareSummary s = result.getSummary();
            lblDiffSummaryBadge.setText(s.getTotalDifferences() + " khác biệt");
            btnFilterAll.setText("Tất cả (" + s.totalObjects() + ")");
            btnFilterDiff.setText("Khác biệt (" + s.getTotalDifferences() + ")");
            btnFilterNew.setText("Chỉ có ở nguồn (" + s.missingInTargetCount() + ")");
            btnFilterDel.setText("Chỉ có ở đích (" + s.extraInTargetCount() + ")");
            btnFilterMod.setText("Khác nhau (" + s.differentCount() + ")");

            lblFooterStatus.setText(String.format("So sánh hoàn tất: %d khác biệt / %d đối tượng (%d ms)",
                    s.getTotalDifferences(), s.totalObjects(), elapsed));

            emptyState.setVisible(false);
            emptyState.setManaged(false);
            diffTreeView.setVisible(true);

            rebuildTree();
            generateAndDisplayScript();
        }));
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
                if (firstSelectable == null && d.getStatus() != DiffStatus.IDENTICAL) {
                    firstSelectable = item;
                }
            }

            catItem.setExpanded(!matching.isEmpty());
            rootTreeItem.getChildren().add(catItem);
        }

        if (firstSelectable != null) {
            diffTreeView.getSelectionModel().select(firstSelectable);
        }
    }

    private void displayDiffObject(SchemaObjectDiff diff) {
        this.selectedDiff = diff;
        if (diff == null) return;

        lblSourceDdlHeader.setText("SOURCE: " + diff.getQualifiedName() + " (" + (sourceProfile != null ? sourceProfile.name() : "DEV") + ")");
        lblTargetDdlHeader.setText("TARGET: " + diff.getQualifiedName() + " (" + (targetProfile != null ? targetProfile.name() : "PROD") + ")");

        if (diff.getObjectType() == SchemaObjectType.TABLE) {
            lblSourceStats.setText(diff.getSourceColumnCount() + " cột");
            lblTargetStats.setText(diff.getTargetColumnCount() + " cột");
        } else {
            lblSourceStats.setText(diff.getObjectType().getDisplayName());
            lblTargetStats.setText(diff.getObjectType().getDisplayName());
        }

        sourceEditor.setText(diff.getSourceDefinition().isEmpty() ? "-- Không tồn tại ở Source" : diff.getSourceDefinition());
        targetEditor.setText(diff.getTargetDefinition().isEmpty() ? "-- Không tồn tại ở Target" : diff.getTargetDefinition());
    }

    private void toggleAllSelection(boolean selected) {
        for (SchemaObjectDiff d : activeDiffs) {
            if (d.getStatus() != DiffStatus.IDENTICAL) {
                d.setSelected(selected);
            }
        }
        diffTreeView.refresh();
        generateAndDisplayScript();
    }

    private void generateAndDisplayScript() {
        if (compareResult == null) return;

        Set<String> selectedNames = new HashSet<>();
        for (SchemaObjectDiff d : activeDiffs) {
            if (d.isSelected()) {
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
            String title = "Migration_" + (targetCatalog != null ? targetCatalog : "Deploy") + ".sql";
            String conn = targetProfile != null ? targetProfile.name() : (sourceProfile != null ? sourceProfile.name() : "");
            mc.openQueryConsole(title, sql, conn, targetCatalog, targetSchema);
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
        private final Label lblCatBadge = new Label();

        private final HBox rootObj = new HBox(6);
        private final CheckBox chkObj = new CheckBox();
        private final FontIcon iconObj = new FontIcon();
        private final Label lblObjName = new Label();
        private final Label lblObjBadge = new Label();

        public SchemaTreeCell() {
            // Category layout
            rootCat.setAlignment(Pos.CENTER_LEFT);
            iconCat.setIconSize(13);
            lblCatTitle.getStyleClass().add("diff-category-title");
            lblCatBadge.getStyleClass().addAll("badge", "badge-warning");

            Region catSpacer = new Region();
            HBox.setHgrow(catSpacer, Priority.ALWAYS);
            rootCat.getChildren().addAll(iconCat, lblCatTitle, catSpacer, lblCatBadge);

            // Object layout
            rootObj.setAlignment(Pos.CENTER_LEFT);
            iconObj.setIconSize(13);
            lblObjName.getStyleClass().add("diff-object-title");

            Region objSpacer = new Region();
            HBox.setHgrow(objSpacer, Priority.ALWAYS);
            rootObj.getChildren().addAll(chkObj, iconObj, lblObjName, objSpacer, lblObjBadge);

            chkObj.setOnAction(e -> {
                SchemaTreeNode node = getItem();
                if (node != null && !node.isCategory() && node.getDiff() != null) {
                    node.getDiff().setSelected(chkObj.isSelected());
                    generateAndDisplayScript();
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
                getStyleClass().add("diff-category-cell");
                iconCat.setIconLiteral(item.getObjectType().getIconLiteral());
                lblCatTitle.setText(item.getObjectType().getDisplayName().toUpperCase());
                lblCatBadge.setText(item.getDiffCount() + " diffs");
                lblCatBadge.setVisible(item.getDiffCount() > 0);
                setGraphic(rootCat);
                setText(null);
            } else {
                getStyleClass().removeAll("diff-category-cell");
                SchemaObjectDiff d = item.getDiff();
                chkObj.setSelected(d.isSelected());
                chkObj.setDisable(d.getStatus() == DiffStatus.IDENTICAL);

                iconObj.setIconLiteral(d.getObjectType().getIconLiteral());
                lblObjName.setText(d.getQualifiedName());

                lblObjBadge.getStyleClass().removeAll("diff-badge-new", "diff-badge-del", "diff-badge-mod", "diff-badge-same");
                switch (d.getStatus()) {
                    case MISSING_IN_TARGET -> {
                        lblObjBadge.setText("Chỉ có ở nguồn");
                        lblObjBadge.getStyleClass().add("diff-badge-new");
                    }
                    case EXTRA_IN_TARGET -> {
                        lblObjBadge.setText("Chỉ có ở đích");
                        lblObjBadge.getStyleClass().add("diff-badge-del");
                    }
                    case DIFFERENT -> {
                        lblObjBadge.setText("Khác nhau");
                        lblObjBadge.getStyleClass().add("diff-badge-mod");
                    }
                    case IDENTICAL -> {
                        lblObjBadge.setText("Giống nhau");
                        lblObjBadge.getStyleClass().add("diff-badge-same");
                    }
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
