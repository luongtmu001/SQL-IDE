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

    private final ToggleButton chipViews = new ToggleButton("Views");
    private final ToggleButton chipProcs = new ToggleButton("Procedures");
    private final ToggleButton chipFuncs = new ToggleButton("Functions");
    private final ToggleButton chipTriggers = new ToggleButton("Triggers");

    private final ComboBox<String> cbCatalog = new ComboBox<>();
    private final ComboBox<String> cbSchema = new ComboBox<>();
    private final AppButton btnSearch = new AppButton("Tìm kiếm", "fth-search", AppButton.Variant.PRIMARY);

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
    private EmbeddedMonaco ddlEditor;

    // SplitPane for Monaco (Top) and Bottom Occurrences Table (Bottom)
    private final SplitPane detailSplitPane = new SplitPane();
    private VBox bottomOccurrencesPanel;
    private final TableView<SearchMatchLocation> occurrencesTable = new TableView<>();
    private final Label lblOccurrencesBadge = new Label("0 vị trí");

    // UI Controls - Footer
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

        root.getChildren().addAll(toolbar, mainSplitPane, footer);

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

        // Type Filter Chips
        chipViews.setSelected(true);
        chipProcs.setSelected(true);
        chipFuncs.setSelected(true);
        chipTriggers.setSelected(true);

        chipViews.getStyleClass().add("filter-chip");
        chipProcs.getStyleClass().add("filter-chip");
        chipFuncs.getStyleClass().add("filter-chip");
        chipTriggers.getStyleClass().add("filter-chip");

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

        // Attach LoadingOverlay to masterContentStack
        loadingOverlay = LoadingOverlay.over(masterContentStack);

        pane.getChildren().addAll(headerBox, masterContentStack);
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
        FontIcon listIcon = new FontIcon("fth-list");
        listIcon.setIconSize(13);
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

        pane.getChildren().addAll(navBar, detailSplitPane);
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

        FontIcon icon = new FontIcon("fth-list");
        icon.setIconSize(13);

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

        // Column # (Row index)
        TableColumn<SearchMatchLocation, Integer> colIndex = new TableColumn<>("#");
        colIndex.setPrefWidth(50);
        colIndex.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(occurrencesTable.getItems().indexOf(data.getValue()) + 1));
        colIndex.setStyle("-fx-alignment: CENTER;");

        // Column Line
        TableColumn<SearchMatchLocation, Integer> colLine = new TableColumn<>("Dòng");
        colLine.setPrefWidth(90);
        colLine.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().line()));
        colLine.setStyle("-fx-alignment: CENTER-RIGHT; -fx-padding: 0 8 0 0;");

        // Column Col
        TableColumn<SearchMatchLocation, Integer> colCol = new TableColumn<>("Cột");
        colCol.setPrefWidth(80);
        colCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().column()));
        colCol.setStyle("-fx-alignment: CENTER-RIGHT; -fx-padding: 0 8 0 0;");

        // Column Code Snippet
        TableColumn<SearchMatchLocation, String> colSnippet = new TableColumn<>("Đoạn mã");
        colSnippet.setPrefWidth(450);
        colSnippet.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().snippet()));
        colSnippet.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().add("occurrences-code-cell");
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

    private HBox buildFooter() {
        HBox footer = new HBox(12);
        footer.getStyleClass().add("object-search-footer");
        footer.setAlignment(Pos.CENTER_LEFT);

        lblFooterStatus.setText("Kết nối: " + (profile != null ? profile.name() : "Chưa kết nối"));
        footer.getChildren().add(lblFooterStatus);
        return footer;
    }

    private void setupEvents(VBox root) {
        // Execute search on button click or Enter key in SearchField
        btnSearch.setOnAction(e -> executeSearchAsync());
        searchField.getInputField().setOnAction(e -> executeSearchAsync());

        // Quick filter in list
        quickFilterField.setOnTextChange(filterText -> rebuildGroupedTree(filterText));

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
        long startTime = System.currentTimeMillis();

        CompletableFuture.supplyAsync(() -> {
            try {
                if (MockMetadataProvider.isEnabled() || BackendFlags.mock(BackendFlags.Area.METADATA) || profile == null) {
                    Thread.sleep(200);
                    return searchService.search(null, "sqlserver", query);
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
                    return searchService.search(raw, engine, query);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).whenComplete((results, throwable) -> Platform.runLater(() -> {
            loadingOverlay.hide();
            btnSearch.setDisable(false);
            long elapsedMs = System.currentTimeMillis() - startTime;

            if (throwable != null) {
                log.error("Object content search error: {}", throwable.getMessage(), throwable);
                NotificationService.getInstance().showError("Lỗi tìm kiếm", throwable.getMessage());
                lblFooterStatus.setText("Lỗi tìm kiếm: " + throwable.getMessage());
                return;
            }

            searchResults.setAll(results != null ? results : Collections.emptyList());
            rebuildGroupedTree(quickFilterField.getText());

            int totalMatches = 0;
            if (results != null) {
                for (ObjectSearchHit h : results) {
                    totalMatches += h.getMatchCount();
                }
            }

            lblFooterStatus.setText(String.format("Tìm thấy %d đối tượng (%d matches) trong %d ms",
                    searchResults.size(), totalMatches, elapsedMs));

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
        }));
    }

    private void cancelSearch() {
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
                {"VIEW", "VIEWS", "fth-eye"},
                {"PROCEDURE", "STORED PROCEDURES", "fth-cpu"},
                {"FUNCTION", "FUNCTIONS", "fth-code"},
                {"TRIGGER", "TRIGGERS", "fth-zap"}
        };

        int totalVisibleHits = 0;
        int totalVisibleMatches = 0;
        TreeItem<ObjectSearchNode> firstHitItem = null;

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

        // Select first hit if none selected
        if (firstHitItem != null && (currentHit == null || !searchResults.contains(currentHit))) {
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
        private final Label lblCatCount = new Label();
        private final Label lblCatBadge = new Label();

        private final HBox rootHit = new HBox(6);
        private final FontIcon iconHit = new FontIcon();
        private final VBox textContainer = new VBox(2);
        private final HBox topRow = new HBox(6);
        private final Label lblHitName = new Label();
        private final Label lblHitBadge = new Label();
        private final Label lblSnippet = new Label();

        public ObjectTreeCell() {
            // Category layout setup
            rootCat.setAlignment(Pos.CENTER_LEFT);
            iconCat.setIconSize(13);
            lblCatTitle.getStyleClass().add("category-title-label");
            lblCatCount.getStyleClass().add("category-count-label");
            lblCatBadge.getStyleClass().addAll("badge", "badge-subtle");

            Region catSpacer = new Region();
            HBox.setHgrow(catSpacer, Priority.ALWAYS);
            rootCat.getChildren().addAll(iconCat, lblCatTitle, lblCatCount, catSpacer, lblCatBadge);

            // Hit item layout setup
            rootHit.setAlignment(Pos.CENTER_LEFT);
            iconHit.setIconSize(14);
            HBox.setHgrow(textContainer, Priority.ALWAYS);

            lblHitName.getStyleClass().add("object-hit-title");
            lblHitBadge.getStyleClass().addAll("badge", "badge-warning");

            topRow.setAlignment(Pos.CENTER_LEFT);
            Region hitSpacer = new Region();
            HBox.setHgrow(hitSpacer, Priority.ALWAYS);
            topRow.getChildren().addAll(lblHitName, hitSpacer, lblHitBadge);

            lblSnippet.getStyleClass().add("object-hit-snippet");
            lblSnippet.setMaxWidth(260);

            textContainer.getChildren().addAll(topRow, lblSnippet);
            rootHit.getChildren().addAll(iconHit, textContainer);
        }

        @Override
        protected void updateItem(ObjectSearchNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                getStyleClass().removeAll("category-tree-cell");
            } else if (item.isCategory()) {
                getStyleClass().add("category-tree-cell");
                iconCat.setIconLiteral(item.getIconLiteral());
                lblCatTitle.setText(item.getTitle());
                lblCatCount.setText("(" + item.getObjectCount() + ")");
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
                getStyleClass().removeAll("category-tree-cell");
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
