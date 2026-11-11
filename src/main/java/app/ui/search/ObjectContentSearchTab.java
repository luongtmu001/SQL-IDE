package app.ui.search;

import app.application.AppContext;
import app.controllers.MainController;
import app.framework.backend.BackendFlags;
import app.framework.connection.ConnectionSessionManager;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import app.framework.session.CredentialResolver;
import app.services.NotificationService;
import app.ui.components.*;
import app.ui.editor.EmbeddedMonaco;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.objectsearch.ObjectSearchService;
import com.lltool.core.common.result.Result;
import com.lltool.core.domain.objectsearch.model.ObjectSearchHit;
import com.lltool.core.domain.objectsearch.model.ObjectSearchQuery;
import com.lltool.core.domain.objectsearch.model.SearchMatchLocation;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Modern Object Content Search Tab (Phase 3 - Slice 3).
 * Searches keyword or Regex patterns across Views, Stored Procedures, Functions, and Triggers.
 * Master-detail UI with categorized groups, total count badges, Monaco DDL preview,
 * and a resizable/collapsible bottom occurrences panel with click-to-jump.
 */
public class ObjectContentSearchTab extends SqlEditorTab {

    private static final Logger log = LoggerFactory.getLogger(ObjectContentSearchTab.class);

    private final ConnectionProfileDto profile;
    private final String initialCatalog;
    private final String initialSchema;
    private final ObjectSearchService searchService = new ObjectSearchService();

    // Data models
    private final ObservableList<ObjectSearchHit> searchResults = FXCollections.observableArrayList();
    private final ObservableList<SearchMatchLocation> currentOccurrences = FXCollections.observableArrayList();
    private int currentMatchIndex = -1;
    private ObjectSearchHit currentHit = null;

    // UI Controls - Toolbar
    private final SearchField searchField = new SearchField("Nhập từ khóa tìm kiếm (regex, tên bảng, cột, text)...");
    private final ToggleButton btnMatchCase = new ToggleButton("Aa");
    private final ToggleButton btnWholeWord = new ToggleButton("\\b");
    private final ToggleButton btnRegex = new ToggleButton(".*");

    private final FilterChip chipViews = new FilterChip("Views", "fth-eye", true);
    private final FilterChip chipProcs = new FilterChip("Procedures", "fth-cpu", true);
    private final FilterChip chipFuncs = new FilterChip("Functions", "fth-code", true);
    private final FilterChip chipTriggers = new FilterChip("Triggers", "fth-zap", true);

    private final ComboBox<String> cbCatalog = new ComboBox<>();
    private final ComboBox<String> cbSchema = new ComboBox<>();
    private final AppButton btnSearch = new AppButton("Tìm kiếm", "fth-search", AppButton.Variant.PRIMARY);
    private final Label lblRegexError = new Label();
    private final HBox warnBanner = new HBox(8);
    private final Label lblWarnMessage = new Label();
    private final app.framework.async.LoadTask<ObjectSearchService.SearchResultWithWarnings> searchTask = new app.framework.async.LoadTask<>();
    private final java.util.concurrent.ScheduledExecutorService searchDebounce = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SearchDebounceThread");
        t.setDaemon(true);
        return t;
    });
    private java.util.concurrent.ScheduledFuture<?> pendingFilterTask;

    // UI Controls - Master Panel (Grouped Tree)
    private final SearchField quickFilterField = new SearchField("Lọc danh sách đối tượng...");
    private final Label lblResultsCount = new Label("Chưa có kết quả");
    private final TreeView<ObjectSearchNode> hitTreeView = new TreeView<>();
    private final TreeItem<ObjectSearchNode> rootTreeItem = new TreeItem<>(ObjectSearchNode.createRoot());
    private final StackPane masterContentStack = new StackPane();
    private final EmptyState emptyState = new EmptyState("fth-search", "Tìm kiếm Nội dung Đối tượng", "Nhập từ khóa và bấm Tìm kiếm để quét mã nguồn DDL.");
    private LoadingOverlay loadingOverlay;

    // UI Controls - Detail Panel
    private final Label lblActiveObjectName = new Label("Chưa chọn đối tượng");
    private final Label lblMatchStepper = new Label("Không có kết quả");
    private final IconButton btnPrevMatch = new IconButton("fth-chevron-up", "Kết quả trước (Shift+F3)");
    private final IconButton btnNextMatch = new IconButton("fth-chevron-down", "Kết quả sau (F3)");
    private final ToggleButton btnToggleOccurrences = new ToggleButton("Vị trí khớp");
    private final AppButton btnCopyDdl = new AppButton("Sao chép DDL", "fth-copy", AppButton.Variant.DEFAULT, AppButton.Size.SMALL);
    private final AppButton btnOpenInSql = new AppButton("Mở trong Tab SQL", "fth-external-link", AppButton.Variant.PRIMARY, AppButton.Size.SMALL);
    private final EmptyState detailEmptyState = new EmptyState("fth-file-text", "Chưa chọn đối tượng", "Chọn một đối tượng bên trái để xem định nghĩa DDL.");
    private EmbeddedMonaco ddlEditor;

    // SplitPane for Monaco (Top) and Bottom Occurrences Table (Bottom)
    private final SplitPane detailSplitPane = new SplitPane();
    private VBox bottomOccurrencesPanel;
    private final TableView<SearchMatchLocation> occurrencesTable = new TableView<>();
    private final Label lblOccurrencesBadge = new Label("0 vị trí");

    // UI Controls - Footer
    private final WorkspaceFooter workspaceFooter = new WorkspaceFooter();
    private final Label lblFooterStatus = new Label("Sẵn sàng");

    public ObjectContentSearchTab(
            ConnectionProfileDto profile,
            String initialCatalog,
            String initialSchema,
            Consumer<SqlEditorTabAction> actionHandler) {

        super(
                "Search: " + (profile != null ? profile.name() : "Objects"),
                "",
                null,
                profile != null ? profile.name() : "",
                initialCatalog,
                initialSchema,
                actionHandler
        );

        this.profile = profile;
        this.initialCatalog = initialCatalog;
        this.initialSchema = initialSchema;

        setFileIconLiteral("fth-search");
        setTabContentType(TabContentType.OBJECT_SEARCH);
        initializeView();
    }

    private void initializeView() {
        VBox root = new VBox();
        root.getStyleClass().add("object-search-root");

        var cssRes = getClass().getResource("/css/object-content-search.css");
        if (cssRes != null) {
            root.getStylesheets().add(cssRes.toExternalForm());
        }

        // 1. Build Top Toolbar
        HBox toolbar = buildToolbar();

        lblRegexError.getStyleClass().add("search-regex-error");
        lblRegexError.setVisible(false);
        lblRegexError.setManaged(false);

        // 2. Build SplitPane (Master - Detail)
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.getStyleClass().add("object-search-split-pane");
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);

        VBox masterPane = buildMasterPane();
        VBox detailPane = buildDetailPane();

        mainSplitPane.getItems().addAll(masterPane, detailPane);
        mainSplitPane.setDividerPositions(0.35);
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);

        // 3. Build Footer
        HBox footer = buildFooter();

        root.getChildren().addAll(toolbar, lblRegexError, mainSplitPane, footer);

        String tabKey = "object_search_" + (profile != null ? profile.id() : "default");
        setCustomContent(tabKey, root);

        // 4. Wire events and shortcuts
        setupEvents(root);

        // 5. Populate catalogs & schemas
        populateCatalogsAndSchemas();
    }

    private HBox buildToolbar() {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("object-search-toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(searchField, Priority.ALWAYS);

        // Option Toggles
        btnMatchCase.getStyleClass().add("search-toggle-btn");
        btnMatchCase.setTooltip(new Tooltip("Phân biệt hoa/thường"));

        btnWholeWord.getStyleClass().add("search-toggle-btn");
        btnWholeWord.setTooltip(new Tooltip("Từ nguyên vẹn"));

        btnRegex.getStyleClass().add("search-toggle-btn");
        btnRegex.setTooltip(new Tooltip("Biểu thức chính quy"));

        // Type Filter Chips are initialized selected with their icons

        // Scope Dropdowns
        cbCatalog.setPromptText("Database / Catalog");
        cbCatalog.setPrefWidth(140);

        cbSchema.setPromptText("Schema (* = Tất cả)");
        cbSchema.setPrefWidth(130);
        cbSchema.setEditable(true);

        Separator sep1 = new Separator(Orientation.VERTICAL);
        Separator sep2 = new Separator(Orientation.VERTICAL);

        bar.getChildren().addAll(
                searchField,
                btnMatchCase, btnWholeWord, btnRegex,
                sep1,
                chipViews, chipProcs, chipFuncs, chipTriggers,
                sep2,
                cbCatalog, cbSchema,
                btnSearch
        );

        return bar;
    }

    private VBox buildMasterPane() {
        VBox pane = new VBox();
        pane.getStyleClass().add("object-search-master");
        pane.setMinWidth(280);
        pane.setPrefWidth(360);

        // Header box with count and quick filter
        VBox headerBox = new VBox(6);
        headerBox.getStyleClass().add("master-header-box");

        lblResultsCount.getStyleClass().add("master-count-label");
        headerBox.getChildren().addAll(lblResultsCount, quickFilterField);

        // TreeView setup for grouped objects
        hitTreeView.getStyleClass().add("object-hit-tree");
        hitTreeView.setShowRoot(false);
        hitTreeView.setRoot(rootTreeItem);
        hitTreeView.setCellFactory(tv -> new ObjectTreeCell());

        hitTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.getValue().isCategory()) {
                displayHit(newVal.getValue().getHit());
            }
        });

        // Master StackPane (Tree + EmptyState + LoadingOverlay)
        masterContentStack.getChildren().addAll(hitTreeView, emptyState);
        VBox.setVgrow(masterContentStack, Priority.ALWAYS);

        // Warning banner for partial query failures or limits
        warnBanner.setAlignment(Pos.CENTER_LEFT);
        warnBanner.getStyleClass().addAll("dialog-chrome-banner", "warning");
        warnBanner.setPadding(new Insets(4, 8, 4, 8));
        FontIcon warnIcon = app.ui.components.Icons.of("fth-alert-triangle");
        warnIcon.getStyleClass().add("warning-icon");
        lblWarnMessage.getStyleClass().add("search-warn-message");
        lblWarnMessage.setWrapText(true);
        warnBanner.getChildren().addAll(warnIcon, lblWarnMessage);
        warnBanner.setVisible(false);
        warnBanner.setManaged(false);

        // Attach LoadingOverlay to masterContentStack
        loadingOverlay = LoadingOverlay.over(masterContentStack);

        pane.getChildren().addAll(headerBox, warnBanner, masterContentStack);
        return pane;
    }

    private VBox buildDetailPane() {
        VBox pane = new VBox();
        pane.getStyleClass().add("object-search-detail");

        // Top navigation toolbar
        HBox navBar = new HBox(8);
        navBar.getStyleClass().add("detail-nav-toolbar");
        navBar.setAlignment(Pos.CENTER_LEFT);

        lblActiveObjectName.getStyleClass().add("detail-active-object-label");
        lblMatchStepper.getStyleClass().add("detail-stepper-label");

        btnPrevMatch.setOnAction(e -> navigateMatch(-1));
        btnNextMatch.setOnAction(e -> navigateMatch(1));

        btnToggleOccurrences.getStyleClass().add("search-toggle-btn");
        btnToggleOccurrences.setSelected(true);
        FontIcon listIcon = app.ui.components.Icons.of("fth-list");
        btnToggleOccurrences.setGraphic(listIcon);
        btnToggleOccurrences.setOnAction(e -> toggleOccurrencesPanel(btnToggleOccurrences.isSelected()));

        btnCopyDdl.setOnAction(e -> copyDdlToClipboard());
        btnOpenInSql.setOnAction(e -> openHitInSqlEditor());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Separator navSep = new Separator(Orientation.VERTICAL);

        navBar.getChildren().addAll(
                lblActiveObjectName,
                navSep,
                lblMatchStepper,
                btnPrevMatch, btnNextMatch,
                spacer,
                btnToggleOccurrences,
                btnCopyDdl, btnOpenInSql
        );

        // Embedded Monaco Editor
        ddlEditor = new EmbeddedMonaco("object_search_ddl_" + System.currentTimeMillis(), "sql", "-- Chọn đối tượng bên trái để xem DDL");
        ddlEditor.setReadOnly(true);
        VBox.setVgrow(ddlEditor.node(), Priority.ALWAYS);

        // Vertical SplitPane hosting Monaco Editor (Top) and Occurrences Panel (Bottom)
        detailSplitPane.setOrientation(Orientation.VERTICAL);
        detailSplitPane.getStyleClass().add("detail-split-pane");
        VBox.setVgrow(detailSplitPane, Priority.ALWAYS);

        // Bottom Occurrences Panel
        bottomOccurrencesPanel = buildBottomOccurrencesPanel();

        detailSplitPane.getItems().addAll(ddlEditor.node(), bottomOccurrencesPanel);
        detailSplitPane.setDividerPositions(0.70);

        StackPane detailStack = new StackPane();
        VBox.setVgrow(detailStack, Priority.ALWAYS);
        detailStack.getChildren().addAll(detailSplitPane, detailEmptyState);
        detailEmptyState.setVisible(true);
        detailEmptyState.setManaged(true);
        detailSplitPane.setVisible(false);

        pane.getChildren().addAll(navBar, detailStack);
        return pane;
    }

    private VBox buildBottomOccurrencesPanel() {
        VBox panel = new VBox();
        panel.getStyleClass().add("occurrences-panel");
        panel.setPrefHeight(180);
        panel.setMinHeight(28);

        // Header
        HBox header = new HBox(8);
        header.getStyleClass().add("occurrences-header");
        header.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = app.ui.components.Icons.of("fth-list");

        Label lblTitle = new Label("Vị trí khớp");
        lblTitle.getStyleClass().add("occurrences-title");

        lblOccurrencesBadge.getStyleClass().addAll("badge", "badge-warning");

        Label lblHint = new Label("Click vào dòng để nhảy đến vị trí trong Monaco");
        lblHint.getStyleClass().add("occurrences-hint");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        IconButton btnClose = new IconButton("fth-x", "Đóng bảng vị trí khớp");
        btnClose.setOnAction(e -> {
            btnToggleOccurrences.setSelected(false);
            toggleOccurrencesPanel(false);
        });

        header.getChildren().addAll(icon, lblTitle, lblOccurrencesBadge, lblHint, spacer, btnClose);

        // Table View Setup
        occurrencesTable.getStyleClass().add("occurrences-table");
        occurrencesTable.setItems(currentOccurrences);
        occurrencesTable.setPlaceholder(new Label("Không có vị trí khớp trong đối tượng hiện tại"));
        VBox.setVgrow(occurrencesTable, Priority.ALWAYS);

        // Column # (Row index) - Fixed 48px
        TableColumn<SearchMatchLocation, Integer> colIndex = new TableColumn<>("#");
        colIndex.setPrefWidth(48);
        colIndex.setMinWidth(48);
        colIndex.setMaxWidth(48);
        colIndex.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(occurrencesTable.getItems().indexOf(data.getValue()) + 1));
        colIndex.getStyleClass().add("col-index-center");

        // Column Line - Fixed 64px
        TableColumn<SearchMatchLocation, Integer> colLine = new TableColumn<>("Dòng");
        colLine.setPrefWidth(64);
        colLine.setMinWidth(64);
        colLine.setMaxWidth(64);
        colLine.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().line()));
        colLine.getStyleClass().add("col-num-right");

        // Column Col - Fixed 64px
        TableColumn<SearchMatchLocation, Integer> colCol = new TableColumn<>("Cột");
        colCol.setPrefWidth(64);
        colCol.setMinWidth(64);
        colCol.setMaxWidth(64);
        colCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().column()));
        colCol.getStyleClass().add("col-num-right");

        // Column Code Snippet - Remainder with keyword highlighting
        TableColumn<SearchMatchLocation, String> colSnippet = new TableColumn<>("Đoạn mã");
        colSnippet.setPrefWidth(450);
        colSnippet.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().snippet()));
        colSnippet.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String snippet, boolean empty) {
                super.updateItem(snippet, empty);
                if (empty || snippet == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String kw = searchField.getText();
                    if (kw != null && !kw.isBlank()) {
                        int idx = snippet.toLowerCase().indexOf(kw.toLowerCase());
                        if (idx >= 0) {
                            TextFlow flow = new TextFlow();
                            flow.getStyleClass().add("occurrences-code-cell");
                            String before = snippet.substring(0, idx);
                            String match = snippet.substring(idx, Math.min(snippet.length(), idx + kw.length()));
                            String after = snippet.substring(Math.min(snippet.length(), idx + kw.length()));

                            Text tBefore = new Text(before);
                            tBefore.getStyleClass().add("occurrences-code-text");
                            Text tMatch = new Text(match);
                            tMatch.getStyleClass().add("occurrences-highlight-match");
                            Text tAfter = new Text(after);
                            tAfter.getStyleClass().add("occurrences-code-text");

                            flow.getChildren().addAll(tBefore, tMatch, tAfter);
                            setText(null);
                            setGraphic(flow);
                            return;
                        }
                    }
                    setText(snippet);
                    setGraphic(null);
                }
            }
        });

        occurrencesTable.getColumns().setAll(colIndex, colLine, colCol, colSnippet);
        occurrencesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Click row -> Jump in Monaco editor
        occurrencesTable.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int idx = newIdx != null ? newIdx.intValue() : -1;
            if (idx >= 0 && idx < currentOccurrences.size() && idx != currentMatchIndex) {
                jumpToOccurrence(idx);
            }
        });

        panel.getChildren().addAll(header, occurrencesTable);
        return panel;
    }

    private void toggleOccurrencesPanel(boolean show) {
        if (show) {
            if (!detailSplitPane.getItems().contains(bottomOccurrencesPanel)) {
                detailSplitPane.getItems().add(bottomOccurrencesPanel);
                detailSplitPane.setDividerPositions(0.70);
            }
        } else {
            detailSplitPane.getItems().remove(bottomOccurrencesPanel);
        }
    }

    private void jumpToOccurrence(int matchIndex) {
        if (currentHit == null || matchIndex < 0 || matchIndex >= currentOccurrences.size()) return;

        currentMatchIndex = matchIndex;
        SearchMatchLocation match = currentOccurrences.get(matchIndex);
        ddlEditor.setActiveSearchMatch(match.line(), match.column(), match.length());

        lblMatchStepper.setText(String.format("%d / %d (Dòng %d, Cột %d)",
                currentMatchIndex + 1, currentOccurrences.size(), match.line(), match.column()));
        btnPrevMatch.setDisable(false);
        btnNextMatch.setDisable(false);

        // Ensure table row is selected
        if (occurrencesTable.getSelectionModel().getSelectedIndex() != matchIndex) {
            occurrencesTable.getSelectionModel().select(matchIndex);
            occurrencesTable.scrollTo(matchIndex);
        }
    }

    private WorkspaceFooter buildFooter() {
        lblFooterStatus.setText("Kết nối: " + (profile != null ? profile.name() : "Chưa kết nối"));
        workspaceFooter.addSection(lblFooterStatus);
        workspaceFooter.setSampleDataVisible(BackendFlags.mock(BackendFlags.Area.METADATA) || MockMetadataProvider.isEnabled());
        return workspaceFooter;
    }

    private void setupEvents(VBox root) {
        // Execute search on button click or Enter key in SearchField
        btnSearch.setOnAction(e -> executeSearchAsync());
        searchField.getInputField().setOnAction(e -> executeSearchAsync());

        // Quick filter in list with 150ms debounce (OS-7)
        quickFilterField.setOnTextChange(filterText -> {
            if (pendingFilterTask != null && !pendingFilterTask.isDone()) {
                pendingFilterTask.cancel(false);
            }
            pendingFilterTask = searchDebounce.schedule(() -> {
                Platform.runLater(() -> rebuildGroupedTree(filterText));
            }, 150, java.util.concurrent.TimeUnit.MILLISECONDS);
        });

        // Global keyboard navigation within this tab
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F3) {
                if (e.isShiftDown()) {
                    navigateMatch(-1);
                } else {
                    navigateMatch(1);
                }
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.ENTER) {
                openHitInSqlEditor();
                e.consume();
            }
        });
    }

    private void populateCatalogsAndSchemas() {
        if (profile == null) return;

        DesktopMetadataService.getInstance().fetchCatalogs(profile, dbs -> {
            cbCatalog.getItems().setAll(dbs != null ? dbs : Collections.emptyList());
            if (initialCatalog != null && dbs != null && dbs.contains(initialCatalog)) {
                cbCatalog.setValue(initialCatalog);
            } else if (dbs != null && !dbs.isEmpty()) {
                cbCatalog.setValue(dbs.get(0));
            }
            loadSchemasForCatalog(cbCatalog.getValue());
        });

        cbCatalog.setOnAction(e -> loadSchemasForCatalog(cbCatalog.getValue()));
    }

    private void loadSchemasForCatalog(String catalog) {
        if (profile == null || catalog == null) return;

        DesktopMetadataService.getInstance().fetchSchemas(profile, catalog, schemas -> {
            List<String> comboItems = new ArrayList<>();
            comboItems.add("*"); // All schemas
            if (schemas != null) comboItems.addAll(schemas);
            cbSchema.getItems().setAll(comboItems);
            if (initialSchema != null && comboItems.contains(initialSchema)) {
                cbSchema.setValue(initialSchema);
            } else {
                cbSchema.setValue("*");
            }
        });
    }

    public void executeSearchAsync() {
        String keyword = searchField.getText();
        if (keyword == null || keyword.trim().isEmpty()) {
            NotificationService.getInstance().showWarning("Tìm kiếm", "Vui lòng nhập từ khóa tìm kiếm.");
            searchField.getInputField().requestFocus();
            return;
        }

        // Validate regex upfront (OS-3)
        if (btnRegex.isSelected()) {
            try {
                int flags = btnMatchCase.isSelected() ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE;
                java.util.regex.Pattern.compile(keyword.trim(), flags);
                lblRegexError.setVisible(false);
                lblRegexError.setManaged(false);
            } catch (java.util.regex.PatternSyntaxException pse) {
                lblRegexError.setText("Lỗi cú pháp Regex tại vị trí " + pse.getIndex() + ": " + pse.getDescription());
                lblRegexError.setVisible(true);
                lblRegexError.setManaged(true);
                searchField.getInputField().requestFocus();
                return;
            }
        } else {
            lblRegexError.setVisible(false);
            lblRegexError.setManaged(false);
        }

        Set<String> requestedTypes = new LinkedHashSet<>();
        if (chipViews.isSelected()) requestedTypes.add("VIEW");
        if (chipProcs.isSelected()) requestedTypes.add("PROCEDURE");
        if (chipFuncs.isSelected()) requestedTypes.add("FUNCTION");
        if (chipTriggers.isSelected()) requestedTypes.add("TRIGGER");

        if (requestedTypes.isEmpty()) {
            NotificationService.getInstance().showWarning("Bộ lọc", "Vui lòng chọn ít nhất một loại đối tượng.");
            return;
        }

        ObjectSearchQuery query = new ObjectSearchQuery(
                keyword.trim(),
                cbCatalog.getValue(),
                cbSchema.getValue(),
                requestedTypes,
                btnMatchCase.isSelected(),
                btnWholeWord.isSelected(),
                btnRegex.isSelected()
        );

        loadingOverlay.show("Đang tìm kiếm đối tượng trong CSDL...", this::cancelSearch);
        btnSearch.setDisable(true);
        warnBanner.setVisible(false);
        warnBanner.setManaged(false);
        long startTime = System.currentTimeMillis();

        searchTask.execute(
                ctx -> {
                    if (MockMetadataProvider.isEnabled() || BackendFlags.mock(BackendFlags.Area.METADATA) || profile == null) {
                        Thread.sleep(150);
                        return searchService.searchWithWarnings(null, "sqlserver", query);
                    }

                    String engine = profile.databaseType() != null && profile.databaseType().toLowerCase().contains("postgre")
                            ? "postgresql" : "sqlserver";
                    String targetDb = query.getCatalog() != null ? query.getCatalog() : profile.databaseName();
                    String pwd = CredentialResolver.resolve(profile, null);

                    Result<DatabaseConnection> connRes = ConnectionSessionManager.getInstance().openSession(profile, targetDb, pwd);
                    if (connRes.isFailure()) {
                        throw new RuntimeException(connRes.getError().get().message());
                    }

                    try (DatabaseConnection dbConn = connRes.getDataOrThrow()) {
                        Connection raw = dbConn.getRawJdbcConnection();
                        return searchService.searchWithWarnings(raw, engine, query);
                    }
                },
                result -> {
                    loadingOverlay.hide();
                    btnSearch.setDisable(false);
                    long elapsedMs = System.currentTimeMillis() - startTime;

                    List<ObjectSearchHit> hits = result != null ? result.hits() : Collections.emptyList();
                    List<String> warnings = result != null ? result.warnings() : Collections.emptyList();
                    int encryptedCount = result != null ? result.encryptedObjectsSkipped() : 0;

                    searchResults.setAll(hits);
                    rebuildGroupedTree(quickFilterField.getText());

                    int totalMatches = 0;
                    for (ObjectSearchHit h : hits) {
                        totalMatches += h.getMatchCount();
                    }

                    StringBuilder statusText = new StringBuilder();
                    statusText.append(String.format("Tìm thấy %d đối tượng (%d matches) trong %d ms",
                            searchResults.size(), totalMatches, elapsedMs));
                    if (encryptedCount > 0) {
                        statusText.append(String.format(" · %d đối tượng mã hóa bị bỏ qua", encryptedCount));
                    }
                    lblFooterStatus.setText(statusText.toString());

                    // Display warning banner if partial failures or truncated (OS-1, OS-4, OS-6)
                    if (warnings != null && !warnings.isEmpty()) {
                        lblWarnMessage.setText("Kết quả có thể thiếu hoặc bị cắt bớt: " + String.join("; ", warnings));
                        warnBanner.setVisible(true);
                        warnBanner.setManaged(true);
                    } else {
                        warnBanner.setVisible(false);
                        warnBanner.setManaged(false);
                    }

                    if (searchResults.isEmpty()) {
                        emptyState.setTitle("Không tìm thấy kết quả");
                        emptyState.setMessage("Không có đối tượng nào chứa từ khóa \"" + keyword + "\".");
                        emptyState.setVisible(true);
                        emptyState.setManaged(true);
                        hitTreeView.setVisible(false);
                        clearDetailView();
                    } else {
                        emptyState.setVisible(false);
                        emptyState.setManaged(false);
                        hitTreeView.setVisible(true);
                    }
                },
                error -> {
                    loadingOverlay.hide();
                    btnSearch.setDisable(false);
                    String msg = error != null && error.getMessage() != null ? error.getMessage() : "Lỗi tìm kiếm";
                    log.error("Object content search error: {}", msg, error);
                    NotificationService.getInstance().showError("Lỗi tìm kiếm", msg);
                    lblFooterStatus.setText("Lỗi tìm kiếm: " + msg);
                }
        );
    }

    private void cancelSearch() {
        searchTask.cancel();
        loadingOverlay.hide();
        btnSearch.setDisable(false);
        lblFooterStatus.setText("Đã hủy tìm kiếm");
    }

    /**
     * Rebuilds the TreeView grouped by Object Type (VIEWS, STORED PROCEDURES, FUNCTIONS, TRIGGERS)
     * with object counts and match totals per category, applying the quick filter text.
     */
    private void rebuildGroupedTree(String filterText) {
        rootTreeItem.getChildren().clear();

        String lowerFilter = filterText != null ? filterText.trim().toLowerCase() : "";

        // Categories definition: {Type, Display Title, Icon}
        String[][] categories = {
                {"VIEW", "Views", "fth-eye"},
                {"PROCEDURE", "Procedures", "fth-cpu"},
                {"FUNCTION", "Functions", "fth-code"},
                {"TRIGGER", "Triggers", "fth-zap"}
        };

        int totalVisibleHits = 0;
        int totalVisibleMatches = 0;
        TreeItem<ObjectSearchNode> firstHitItem = null;

        // Preserve active selection across filter rebuilds (OS-7)
        String activeKey = currentHit != null ? (currentHit.getType() + "|" + currentHit.getSchema() + "|" + currentHit.getName()).toLowerCase() : null;
        TreeItem<ObjectSearchNode> preservedItem = null;

        for (String[] catDef : categories) {
            String type = catDef[0];
            String title = catDef[1];
            String icon = catDef[2];

            List<ObjectSearchHit> matchingHits = new ArrayList<>();
            int catMatches = 0;

            for (ObjectSearchHit hit : searchResults) {
                if (hit.getType().equalsIgnoreCase(type)) {
                    if (lowerFilter.isEmpty()
                            || hit.getName().toLowerCase().contains(lowerFilter)
                            || hit.getSchema().toLowerCase().contains(lowerFilter)
                            || hit.getFirstSnippet().toLowerCase().contains(lowerFilter)) {
                        matchingHits.add(hit);
                        catMatches += hit.getMatchCount();
                    }
                }
            }

            totalVisibleHits += matchingHits.size();
            totalVisibleMatches += catMatches;

            // Create category TreeItem
            ObjectSearchNode catNode = ObjectSearchNode.createCategory(type, title, icon, matchingHits.size(), catMatches);
            TreeItem<ObjectSearchNode> catItem = new TreeItem<>(catNode);

            for (ObjectSearchHit hit : matchingHits) {
                TreeItem<ObjectSearchNode> hitItem = new TreeItem<>(ObjectSearchNode.createHit(hit));
                catItem.getChildren().add(hitItem);
                if (firstHitItem == null) {
                    firstHitItem = hitItem;
                }
                if (activeKey != null) {
                    String hitKey = (hit.getType() + "|" + hit.getSchema() + "|" + hit.getName()).toLowerCase();
                    if (activeKey.equals(hitKey)) {
                        preservedItem = hitItem;
                    }
                }
            }

            // Expand category if it has matches
            catItem.setExpanded(!matchingHits.isEmpty());
            rootTreeItem.getChildren().add(catItem);
        }

        // Update Results Count Label
        int totalHits = searchResults.size();
        if (totalVisibleHits == totalHits) {
            lblResultsCount.setText(String.format("%d đối tượng · %d kết quả", totalHits, totalVisibleMatches));
        } else {
            lblResultsCount.setText(String.format("Hiển thị %d / %d đối tượng (%d kết quả)",
                    totalVisibleHits, totalHits, totalVisibleMatches));
        }

        // Select preserved item or first hit
        if (preservedItem != null) {
            hitTreeView.getSelectionModel().select(preservedItem);
        } else if (firstHitItem != null && (currentHit == null || !searchResults.contains(currentHit))) {
            hitTreeView.getSelectionModel().select(firstHitItem);
        }
    }

    private void displayHit(ObjectSearchHit hit) {
        this.currentHit = hit;
        this.currentMatchIndex = -1;

        if (hit == null || hit.getDdl() == null || hit.getDdl().isBlank()) {
            clearDetailView();
            return;
        }

        detailEmptyState.setVisible(false);
        detailEmptyState.setManaged(false);
        detailSplitPane.setVisible(true);

        lblActiveObjectName.setText(hit.getQualifiedName() + " (" + hit.getType() + ")");
        ddlEditor.setText(hit.getDdl());
        ddlEditor.highlightSearchMatches(hit.getMatches());

        currentOccurrences.setAll(hit.getMatches());
        lblOccurrencesBadge.setText(hit.getMatches().size() + " vị trí");

        if (!hit.getMatches().isEmpty()) {
            navigateMatch(1); // Jump to first match
        } else {
            lblMatchStepper.setText("0 kết quả");
            btnPrevMatch.setDisable(true);
            btnNextMatch.setDisable(true);
            occurrencesTable.getSelectionModel().clearSelection();
        }
    }

    private void navigateMatch(int direction) {
        if (currentHit == null || currentOccurrences.isEmpty()) {
            lblMatchStepper.setText("0 kết quả");
            btnPrevMatch.setDisable(true);
            btnNextMatch.setDisable(true);
            return;
        }

        int total = currentOccurrences.size();
        if (currentMatchIndex < 0) {
            currentMatchIndex = 0;
        } else {
            currentMatchIndex = (currentMatchIndex + direction + total) % total;
        }

        jumpToOccurrence(currentMatchIndex);
    }

    private void clearDetailView() {
        detailEmptyState.setVisible(true);
        detailEmptyState.setManaged(true);
        detailSplitPane.setVisible(false);

        lblActiveObjectName.setText("Chưa chọn đối tượng");
        ddlEditor.setText("-- Chọn đối tượng bên trái để xem DDL");
        ddlEditor.clearSearchHighlights();
        lblMatchStepper.setText("Không có kết quả");
        btnPrevMatch.setDisable(true);
        btnNextMatch.setDisable(true);
        currentHit = null;
        currentMatchIndex = -1;
        currentOccurrences.clear();
        lblOccurrencesBadge.setText("0 vị trí");
    }

    private void copyDdlToClipboard() {
        String ddl = ddlEditor.getText();
        if (ddl == null || ddl.isBlank() || ddl.startsWith("--")) {
            NotificationService.getInstance().showWarning("Copy", "Không có nội dung DDL để sao chép.");
            return;
        }

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(ddl);
        clipboard.setContent(content);

        NotificationService.getInstance().showSuccess("Copy", "Đã sao chép DDL vào clipboard.");
    }

    private void openHitInSqlEditor() {
        if (currentHit == null || currentHit.getDdl() == null || currentHit.getDdl().isBlank()) {
            return;
        }

        MainController mc = AppContext.getInstance().getMainController();
        if (mc != null) {
            String title = currentHit.getName() + ".sql";
            String connName = profile != null ? profile.name() : "";
            String db = currentHit.getCatalog();
            String sch = currentHit.getSchema();
            mc.openQueryConsole(title, currentHit.getDdl(), connName, db, sch);
        }
    }

    // --- Node Data Structure for Grouped Tree ---
    public static class ObjectSearchNode {
        private final boolean category;
        private final String categoryType;
        private final String title;
        private final String iconLiteral;
        private final int objectCount;
        private final int matchCount;
        private final ObjectSearchHit hit;

        private ObjectSearchNode(boolean category, String categoryType, String title, String iconLiteral,
                                 int objectCount, int matchCount, ObjectSearchHit hit) {
            this.category = category;
            this.categoryType = categoryType;
            this.title = title;
            this.iconLiteral = iconLiteral;
            this.objectCount = objectCount;
            this.matchCount = matchCount;
            this.hit = hit;
        }

        public static ObjectSearchNode createRoot() {
            return new ObjectSearchNode(true, "ROOT", "ROOT", "", 0, 0, null);
        }

        public static ObjectSearchNode createCategory(String type, String title, String iconLiteral, int count, int matches) {
            return new ObjectSearchNode(true, type, title, iconLiteral, count, matches, null);
        }

        public static ObjectSearchNode createHit(ObjectSearchHit hit) {
            return new ObjectSearchNode(false, hit.getType(), hit.getQualifiedName(), "", 0, hit.getMatchCount(), hit);
        }

        public boolean isCategory() { return category; }
        public String getCategoryType() { return categoryType; }
        public String getTitle() { return title; }
        public String getIconLiteral() { return iconLiteral; }
        public int getObjectCount() { return objectCount; }
        public int getMatchCount() { return matchCount; }
        public ObjectSearchHit getHit() { return hit; }
    }

    // --- Custom TreeCell Rendering ---
    private static class ObjectTreeCell extends TreeCell<ObjectSearchNode> {
        private final HBox rootCat = new HBox(6);
        private final FontIcon iconCat = new FontIcon();
        private final Label lblCatTitle = new Label();
        private final Badge lblCatBadge = Badge.neutral("");

        private final HBox rootHit = new HBox(6);
        private final FontIcon iconHit = new FontIcon();
        private final VBox textContainer = new VBox(2);
        private final HBox topRow = new HBox(6);
        private final Label lblHitName = new Label();
        private final Badge lblHitBadge = Badge.neutral("");
        private final Label lblSnippet = new Label();

        public ObjectTreeCell() {
            // Category layout setup
            rootCat.setAlignment(Pos.CENTER_LEFT);
            app.ui.components.Icons.bind(iconCat, app.ui.components.IconScale.MD);
            lblCatTitle.getStyleClass().add("category-title-label");

            Region catSpacer = new Region();
            HBox.setHgrow(catSpacer, Priority.ALWAYS);
            rootCat.getChildren().addAll(iconCat, lblCatTitle, catSpacer, lblCatBadge);

            // Hit item layout setup
            rootHit.setAlignment(Pos.CENTER_LEFT);
            app.ui.components.Icons.bind(iconHit, app.ui.components.IconScale.MD);
            HBox.setHgrow(textContainer, Priority.ALWAYS);

            lblHitName.getStyleClass().add("object-hit-title");

            topRow.setAlignment(Pos.CENTER_LEFT);
            Region hitSpacer = new Region();
            HBox.setHgrow(hitSpacer, Priority.ALWAYS);
            topRow.getChildren().addAll(lblHitName, hitSpacer, lblHitBadge);

            lblSnippet.getStyleClass().add("object-hit-snippet");
            lblSnippet.maxWidthProperty().bind(widthProperty().subtract(60));

            textContainer.getChildren().addAll(topRow, lblSnippet);
            rootHit.getChildren().addAll(iconHit, textContainer);
        }

        @Override
        protected void updateItem(ObjectSearchNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                getStyleClass().remove("category-tree-cell");
            } else if (item.isCategory()) {
                if (!getStyleClass().contains("category-tree-cell")) {
                    getStyleClass().add("category-tree-cell");
                }
                iconCat.setIconLiteral(item.getIconLiteral());
                lblCatTitle.setText(item.getTitle() + " (" + item.getObjectCount() + ")");
                lblCatBadge.setVisible(false);
                lblCatBadge.setManaged(false);

                if (item.getObjectCount() == 0) {
                    rootCat.setOpacity(0.5);
                } else {
                    rootCat.setOpacity(1.0);
                }

                setGraphic(rootCat);
                setText(null);
            } else {
                getStyleClass().remove("category-tree-cell");
                ObjectSearchHit hit = item.getHit();
                String type = hit.getType();
                if ("VIEW".equalsIgnoreCase(type)) {
                    iconHit.setIconLiteral("fth-eye");
                } else if ("PROCEDURE".equalsIgnoreCase(type)) {
                    iconHit.setIconLiteral("fth-cpu");
                } else if ("FUNCTION".equalsIgnoreCase(type)) {
                    iconHit.setIconLiteral("fth-code");
                } else if ("TRIGGER".equalsIgnoreCase(type)) {
                    iconHit.setIconLiteral("fth-zap");
                } else {
                    iconHit.setIconLiteral("fth-file-text");
                }

                lblHitName.setText(hit.getQualifiedName());
                lblHitBadge.setText(hit.getMatchCount() + " kết quả");
                lblHitBadge.setVariant(Badge.Variant.NEUTRAL);

                String snippet = hit.getFirstSnippet();
                if (snippet != null && !snippet.isBlank()) {
                    lblSnippet.setText(snippet);
                    lblSnippet.setVisible(true);
                    lblSnippet.setManaged(true);
                } else {
                    lblSnippet.setText(hit.getType());
                }

                rootHit.setOpacity(1.0);
                setGraphic(rootHit);
                setText(null);
            }
        }
    }

    // Getters for UI and testing
    public SearchField getSearchField() { return searchField; }
    public ToggleButton getBtnMatchCase() { return btnMatchCase; }
    public ToggleButton getBtnWholeWord() { return btnWholeWord; }
    public ToggleButton getBtnRegex() { return btnRegex; }
    public ObservableList<ObjectSearchHit> getSearchResults() { return searchResults; }
    public TreeView<ObjectSearchNode> getHitTreeView() { return hitTreeView; }
    public TableView<SearchMatchLocation> getOccurrencesTable() { return occurrencesTable; }
    public EmbeddedMonaco getDdlEditor() { return ddlEditor; }
    public Label getLblMatchStepper() { return lblMatchStepper; }
    public int getCurrentMatchIndex() { return currentMatchIndex; }
    public ToggleButton getBtnToggleOccurrences() { return btnToggleOccurrences; }
    public SplitPane getDetailSplitPane() { return detailSplitPane; }
}
