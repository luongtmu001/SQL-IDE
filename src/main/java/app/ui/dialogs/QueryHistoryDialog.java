package app.ui.dialogs;

import app.models.HistoryDocument;
import app.models.HistoryEntry;
import app.models.HistoryTag;
import app.services.HistoryLoggingService;
import app.services.NotificationService;
import app.services.SettingsService;
import app.ui.components.SearchField;
import app.ui.editor.EmbeddedMonaco;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

/**
 * Query History Window & Manager (Phase 2 - Feature 10).
 * Implements full parity with Python Query History dialog (1050x680px):
 * Recent queries tree/list, history entries, instant filtering,
 * embedded read-only Monaco preview, metadata footer, tags, notes,
 * and JSON import/export.
 */
public final class QueryHistoryDialog {

    private static final Logger log = LoggerFactory.getLogger(QueryHistoryDialog.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private final Window owner;
    private final Consumer<String> openInNewTabHandler;
    private final HistoryLoggingService historyService;

    private DialogChrome chrome;

    // Top controls
    private SearchField searchField;
    private Button filterFavBtn;
    private Button advancedFilterBtn;
    private Button exportBtn;
    private Button importBtn;
    private Button refreshBtn;

    // Filter drawer
    private VBox filterDrawer;
    private ComboBox<String> serverCombo;
    private ComboBox<String> databaseCombo;
    private ComboBox<String> dateRangeCombo;
    private ComboBox<String> tagCombo;
    private Button resetFiltersBtn;
    private boolean filterDrawerVisible = false;

    // Document list (Top-Left)
    private final ObservableList<HistoryDocument> documentsList = FXCollections.observableArrayList();
    private ListView<HistoryDocument> documentsListView;
    private Label docCountLabel;

    // Entry list (Bottom-Left)
    private final ObservableList<HistoryEntry> entriesList = FXCollections.observableArrayList();
    private ListView<HistoryEntry> entriesListView;
    private Label entriesDocTitle;
    private Label entryCountPill;
    private String currentFastFilter = "all"; // all, ok, error, selection, favorite

    // Fast filter buttons
    private Button btnFilterAll;
    private Button btnFilterOk;
    private Button btnFilterErr;
    private Button btnFilterSel;
    private Button btnFilterFav;

    // Right column preview
    private Label previewTitle;
    private Label previewStatusPill;
    private Label previewTimestamp;
    private Button previewStarBtn;
    private FontIcon previewStarIcon;
    private Button btnCopyPreview;
    private Button btnWrapPreview;

    private HBox noteBar;
    private HBox tagsContainer;
    private TextField noteInput;
    private Button btnSaveNote;
    private Button btnAddTag;

    private HBox errorBanner;
    private Label errorMsgLabel;

    private EmbeddedMonaco monacoPreview;

    private Label metaServer;
    private Label metaDb;
    private Label metaUser;
    private Label metaDuration;
    private Label metaSize;

    private boolean favOnlyMode = false;
    private HistoryDocument selectedDocument = null;
    private HistoryEntry selectedEntry = null;

    public static QueryHistoryDialog show(Window owner, Consumer<String> openInNewTabHandler) {
        QueryHistoryDialog dlg = new QueryHistoryDialog(owner, openInNewTabHandler);
        dlg.open();
        return dlg;
    }

    public DialogChrome getChrome() {
        return chrome;
    }

    public void close() {
        if (chrome != null) {
            chrome.close();
        }
    }

    private QueryHistoryDialog(Window owner, Consumer<String> openInNewTabHandler) {
        this.owner = owner;
        this.openInNewTabHandler = openInNewTabHandler;
        this.historyService = HistoryLoggingService.getInstance();
    }

    private void open() {
        chrome = DialogChrome.create(owner, "Query History", "fth-clock")
                .subtitle("F5 Execution Logger")
                .size(1050, 680);

        chrome.stage().setMinWidth(850);
        chrome.stage().setMinHeight(550);

        Node content = buildContent();
        chrome.content(content);

        // Footer buttons
        Button deleteBtn = DialogChrome.secondary("Xóa");
        deleteBtn.setGraphic(new FontIcon("fth-trash-2"));
        deleteBtn.setOnAction(e -> handleDeleteCurrentSelection());

        Button copySqlBtn = DialogChrome.secondary("Sao chép SQL");
        copySqlBtn.setGraphic(new FontIcon("fth-copy"));
        copySqlBtn.setOnAction(e -> handleCopyCurrentSql());

        Button openTabBtn = DialogChrome.primary("Mở vào Tab mới");
        openTabBtn.setGraphic(new FontIcon("fth-external-link"));
        openTabBtn.setDefaultButton(true);
        openTabBtn.setOnAction(e -> handleOpenInNewTab());

        Button closeBtn = DialogChrome.cancel("Đóng");

        chrome.footer(new Node[]{deleteBtn}, copySqlBtn, closeBtn, openTabBtn);

        // Load stylesheets & keybindings
        Scene scene = chrome.stage().getScene();
        if (scene != null) {
            var css = getClass().getResource("/css/query-history.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKeys);
        }

        // Live refresh listener
        Runnable changeListener = () -> {
            if (chrome != null) {
                refreshDocumentList(false);
            }
        };
        historyService.addChangeListener(changeListener);
        chrome.stage().setOnHidden(e -> historyService.removeChangeListener(changeListener));

        // Initial load
        refreshDocumentList(true);

        chrome.show();
    }

    private Node buildContent() {
        VBox root = new VBox();
        root.getStyleClass().add("query-history-root");
        VBox.setVgrow(root, Priority.ALWAYS);
        root.setPrefSize(1050, 680);

        // Top Toolbar
        HBox toolbar = buildTopToolbar();

        // Filter Drawer
        filterDrawer = buildFilterDrawer();

        // Main Workspace SplitPane (Left vs Right)
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        VBox.setVgrow(mainSplit, Priority.ALWAYS);

        Node leftCol = buildLeftColumn();
        Node rightCol = buildRightColumn();

        mainSplit.getItems().addAll(leftCol, rightCol);
        mainSplit.setDividerPositions(0.38);

        // Bottom Statusbar
        HBox statusbar = buildBottomStatusbar();

        root.getChildren().addAll(toolbar, filterDrawer, mainSplit, statusbar);
        return root;
    }

    private HBox buildTopToolbar() {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("qh-toolbar");

        searchField = new SearchField();
        searchField.getStyleClass().add("qh-search-field");
        searchField.setPromptText("Tìm theo tên file, server, database, SQL...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshDocumentList(false));

        filterFavBtn = new Button("Favorites");
        FontIcon starIcon = new FontIcon("fth-star");
        starIcon.setStyle("-fx-icon-color: -color-warning-fg;");
        filterFavBtn.setGraphic(starIcon);
        filterFavBtn.getStyleClass().add("qh-tool-btn");
        filterFavBtn.setOnAction(e -> {
            favOnlyMode = !favOnlyMode;
            if (favOnlyMode) {
                filterFavBtn.getStyleClass().add("active");
            } else {
                filterFavBtn.getStyleClass().remove("active");
            }
            refreshDocumentList(false);
        });

        advancedFilterBtn = new Button("Lọc");
        advancedFilterBtn.setGraphic(new FontIcon("fth-filter"));
        advancedFilterBtn.getStyleClass().add("qh-tool-btn");
        advancedFilterBtn.setOnAction(e -> toggleFilterDrawer());

        exportBtn = new Button("Xuất");
        exportBtn.setGraphic(new FontIcon("fth-upload"));
        exportBtn.getStyleClass().add("qh-tool-btn");
        exportBtn.setOnAction(e -> handleExportJson());

        importBtn = new Button("Nhập");
        importBtn.setGraphic(new FontIcon("fth-download"));
        importBtn.getStyleClass().add("qh-tool-btn");
        importBtn.setOnAction(e -> handleImportJson());

        refreshBtn = new Button("Làm mới");
        refreshBtn.setGraphic(new FontIcon("fth-refresh-cw"));
        refreshBtn.getStyleClass().add("qh-tool-btn");
        refreshBtn.setOnAction(e -> refreshDocumentList(false));

        bar.getChildren().addAll(searchField, filterFavBtn, advancedFilterBtn, exportBtn, importBtn, refreshBtn);
        return bar;
    }

    private VBox buildFilterDrawer() {
        VBox drawer = new VBox(8);
        drawer.getStyleClass().add("qh-filter-drawer");
        drawer.setVisible(false);
        drawer.setManaged(false);

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        // Server
        Label srvLabel = new Label("Server:");
        srvLabel.getStyleClass().add("qh-filter-label");
        serverCombo = new ComboBox<>();
        serverCombo.getStyleClass().add("qh-filter-combo");
        serverCombo.setOnAction(e -> refreshDocumentList(false));

        // Database
        Label dbLabel = new Label("Database:");
        dbLabel.getStyleClass().add("qh-filter-label");
        databaseCombo = new ComboBox<>();
        databaseCombo.getStyleClass().add("qh-filter-combo");
        databaseCombo.setOnAction(e -> refreshDocumentList(false));

        // Date Range
        Label dateLabel = new Label("Thời gian:");
        dateLabel.getStyleClass().add("qh-filter-label");
        dateRangeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Tất cả thời gian", "Hôm nay", "Hôm qua", "7 ngày qua", "30 ngày qua"
        ));
        dateRangeCombo.setValue("Tất cả thời gian");
        dateRangeCombo.getStyleClass().add("qh-filter-combo");
        dateRangeCombo.setOnAction(e -> refreshDocumentList(false));

        // Tag
        Label tagLabel = new Label("Nhãn:");
        tagLabel.getStyleClass().add("qh-filter-label");
        tagCombo = new ComboBox<>();
        tagCombo.getStyleClass().add("qh-filter-combo");
        tagCombo.setOnAction(e -> refreshDocumentList(false));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        resetFiltersBtn = new Button("Đặt lại");
        resetFiltersBtn.setGraphic(new FontIcon("fth-rotate-ccw"));
        resetFiltersBtn.getStyleClass().add("qh-tool-btn");
        resetFiltersBtn.setOnAction(e -> resetFilters());

        Button closeDrawerBtn = new Button();
        closeDrawerBtn.setGraphic(new FontIcon("fth-x"));
        closeDrawerBtn.getStyleClass().add("qh-icon-btn");
        closeDrawerBtn.setOnAction(e -> toggleFilterDrawer());

        row.getChildren().addAll(
                srvLabel, serverCombo,
                dbLabel, databaseCombo,
                dateLabel, dateRangeCombo,
                tagLabel, tagCombo,
                spacer, resetFiltersBtn, closeDrawerBtn
        );

        drawer.getChildren().add(row);
        return drawer;
    }

    private void toggleFilterDrawer() {
        filterDrawerVisible = !filterDrawerVisible;
        filterDrawer.setVisible(filterDrawerVisible);
        filterDrawer.setManaged(filterDrawerVisible);
        if (filterDrawerVisible) {
            advancedFilterBtn.getStyleClass().add("active");
            populateFilterOptions();
        } else {
            advancedFilterBtn.getStyleClass().remove("active");
        }
    }

    private void populateFilterOptions() {
        List<String> servers = new ArrayList<>();
        servers.add("Tất cả Server");
        servers.addAll(historyService.getDistinctServers());
        serverCombo.setItems(FXCollections.observableArrayList(servers));
        serverCombo.setValue("Tất cả Server");

        List<String> dbs = new ArrayList<>();
        dbs.add("Tất cả Database");
        dbs.addAll(historyService.getDistinctDatabases());
        databaseCombo.setItems(FXCollections.observableArrayList(dbs));
        databaseCombo.setValue("Tất cả Database");

        List<String> tags = new ArrayList<>();
        tags.add("Tất cả nhãn");
        for (HistoryTag t : historyService.getAllTags()) {
            tags.add(t.getTagName());
        }
        tagCombo.setItems(FXCollections.observableArrayList(tags));
        tagCombo.setValue("Tất cả nhãn");
    }

    private void resetFilters() {
        serverCombo.setValue("Tất cả Server");
        databaseCombo.setValue("Tất cả Database");
        dateRangeCombo.setValue("Tất cả thời gian");
        tagCombo.setValue("Tất cả nhãn");
        searchField.clear();
        refreshDocumentList(false);
    }

    private Node buildLeftColumn() {
        SplitPane leftSplit = new SplitPane();
        leftSplit.setOrientation(Orientation.VERTICAL);
        VBox.setVgrow(leftSplit, Priority.ALWAYS);

        // 1. Top-Left: Documents
        VBox docsPanel = new VBox();
        docsPanel.getStyleClass().add("qh-panel");
        VBox.setVgrow(docsPanel, Priority.ALWAYS);

        HBox docsHeader = new HBox(8);
        docsHeader.getStyleClass().add("qh-panel-header");

        FontIcon folderIcon = new FontIcon("fth-folder");
        Label docsTitle = new Label("Recent Queries");
        docsTitle.getStyleClass().add("qh-panel-title");
        docCountLabel = new Label("0 files");
        docCountLabel.getStyleClass().add("qh-count-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openFolderBtn = new Button();
        openFolderBtn.setGraphic(new FontIcon("fth-folder-plus"));
        openFolderBtn.setTooltip(new Tooltip("Mở thư mục chứa file"));
        openFolderBtn.getStyleClass().add("qh-icon-btn");
        openFolderBtn.setOnAction(e -> handleOpenContainingFolder());

        Button deleteDocBtn = new Button();
        deleteDocBtn.setGraphic(new FontIcon("fth-trash-2"));
        deleteDocBtn.setTooltip(new Tooltip("Xóa lịch sử của document đang chọn"));
        deleteDocBtn.getStyleClass().add("qh-icon-btn");
        deleteDocBtn.setOnAction(e -> handleDeleteDocument());

        docsHeader.getChildren().addAll(folderIcon, docsTitle, docCountLabel, spacer, openFolderBtn, deleteDocBtn);

        documentsListView = new ListView<>(documentsList);
        documentsListView.getStyleClass().add("qh-list-view");
        VBox.setVgrow(documentsListView, Priority.ALWAYS);
        documentsListView.setCellFactory(lv -> new DocumentListCell());
        documentsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedDocument = newVal;
            loadEntriesForSelectedDocument();
        });

        docsPanel.getChildren().addAll(docsHeader, documentsListView);

        // 2. Bottom-Left: Entries
        VBox entriesPanel = new VBox();
        entriesPanel.getStyleClass().add("qh-panel");
        VBox.setVgrow(entriesPanel, Priority.ALWAYS);

        HBox entriesHeader = new HBox(8);
        entriesHeader.getStyleClass().add("qh-panel-header");

        FontIcon listIcon = new FontIcon("fth-list");
        listIcon.setStyle("-fx-icon-color: -color-accent-fg;");
        Label entriesForLabel = new Label("History for:");
        entriesForLabel.getStyleClass().add("qh-panel-title");
        entriesDocTitle = new Label("None");
        entriesDocTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-fg-default;");
        entriesDocTitle.setMaxWidth(160);
        entryCountPill = new Label("0 lần");
        entryCountPill.getStyleClass().add("qh-count-badge");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        entriesHeader.getChildren().addAll(listIcon, entriesForLabel, entriesDocTitle, entryCountPill, spacer2);

        // Fast Filter Pills Bar
        HBox pillsBar = new HBox(6);
        pillsBar.getStyleClass().add("qh-filter-pills-bar");

        btnFilterAll = new Button("Tất cả");
        btnFilterAll.getStyleClass().addAll("qh-pill-btn", "active");
        btnFilterAll.setOnAction(e -> setFastFilter("all"));

        btnFilterOk = new Button("Thành công");
        btnFilterOk.setGraphic(new FontIcon("fth-check"));
        btnFilterOk.getStyleClass().add("qh-pill-btn");
        btnFilterOk.setOnAction(e -> setFastFilter("ok"));

        btnFilterErr = new Button("Lỗi");
        btnFilterErr.setGraphic(new FontIcon("fth-alert-circle"));
        btnFilterErr.getStyleClass().add("qh-pill-btn");
        btnFilterErr.setOnAction(e -> setFastFilter("error"));

        btnFilterSel = new Button("Selection");
        btnFilterSel.getStyleClass().add("qh-pill-btn");
        btnFilterSel.setOnAction(e -> setFastFilter("selection"));

        btnFilterFav = new Button("⭐ Yêu thích");
        btnFilterFav.getStyleClass().add("qh-pill-btn");
        btnFilterFav.setOnAction(e -> setFastFilter("favorite"));

        pillsBar.getChildren().addAll(btnFilterAll, btnFilterOk, btnFilterErr, btnFilterSel, btnFilterFav);

        entriesListView = new ListView<>(entriesList);
        entriesListView.getStyleClass().add("qh-list-view");
        VBox.setVgrow(entriesListView, Priority.ALWAYS);
        entriesListView.setCellFactory(lv -> new EntryListCell());
        entriesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedEntry = newVal;
            loadEntryPreview(newVal);
        });

        entriesPanel.getChildren().addAll(entriesHeader, pillsBar, entriesListView);

        leftSplit.getItems().addAll(docsPanel, entriesPanel);
        leftSplit.setDividerPositions(0.48);

        return leftSplit;
    }

    private void setFastFilter(String filter) {
        currentFastFilter = filter;
        btnFilterAll.getStyleClass().remove("active");
        btnFilterOk.getStyleClass().remove("active");
        btnFilterErr.getStyleClass().remove("active");
        btnFilterSel.getStyleClass().remove("active");
        btnFilterFav.getStyleClass().remove("active");

        switch (filter) {
            case "all" -> btnFilterAll.getStyleClass().add("active");
            case "ok" -> btnFilterOk.getStyleClass().add("active");
            case "error" -> btnFilterErr.getStyleClass().add("active");
            case "selection" -> btnFilterSel.getStyleClass().add("active");
            case "favorite" -> btnFilterFav.getStyleClass().add("active");
        }
        loadEntriesForSelectedDocument();
    }

    private Node buildRightColumn() {
        VBox previewPanel = new VBox();
        previewPanel.getStyleClass().add("qh-preview-panel");
        VBox.setVgrow(previewPanel, Priority.ALWAYS);

        // 1. Preview Header
        HBox header = new HBox(8);
        header.getStyleClass().add("qh-preview-header");

        FontIcon fileIcon = new FontIcon("fth-file-text");
        fileIcon.setStyle("-fx-icon-color: -color-warning-fg;");
        previewTitle = new Label("Untitled.sql");
        previewTitle.getStyleClass().add("qh-preview-title");

        previewStatusPill = new Label("Thành công");
        previewStatusPill.getStyleClass().addAll("qh-status-pill", "qh-status-ok");

        previewTimestamp = new Label("");
        previewTimestamp.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        previewStarBtn = new Button();
        previewStarIcon = new FontIcon("fth-star");
        previewStarBtn.setGraphic(previewStarIcon);
        previewStarBtn.getStyleClass().add("qh-icon-btn");
        previewStarBtn.setTooltip(new Tooltip("Đánh dấu yêu thích lần chạy này"));
        previewStarBtn.setOnAction(e -> handleToggleEntryFavorite());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnCopyPreview = new Button("Sao chép");
        btnCopyPreview.setGraphic(new FontIcon("fth-copy"));
        btnCopyPreview.getStyleClass().add("qh-tool-btn");
        btnCopyPreview.setOnAction(e -> handleCopyCurrentSql());

        btnWrapPreview = new Button("Wrap");
        btnWrapPreview.setGraphic(new FontIcon("fth-align-left"));
        btnWrapPreview.getStyleClass().add("qh-tool-btn");

        header.getChildren().addAll(
                fileIcon, previewTitle, previewStatusPill, previewTimestamp, previewStarBtn,
                spacer, btnWrapPreview, btnCopyPreview
        );

        // 2. Note & Tag Bar
        noteBar = new HBox(8);
        noteBar.getStyleClass().add("qh-note-bar");

        tagsContainer = new HBox(4);
        tagsContainer.setAlignment(Pos.CENTER_LEFT);

        noteInput = new TextField();
        noteInput.setPromptText("Thêm ghi chú cho lần chạy này... (Enter để lưu)");
        noteInput.getStyleClass().add("qh-note-input");
        HBox.setHgrow(noteInput, Priority.ALWAYS);
        noteInput.setOnAction(e -> handleSaveNote());

        btnAddTag = new Button("+ Tag");
        btnAddTag.setGraphic(new FontIcon("fth-tag"));
        btnAddTag.getStyleClass().add("qh-tool-btn");
        btnAddTag.setOnAction(e -> handleAddTagPrompt());

        btnSaveNote = new Button("Lưu");
        btnSaveNote.setGraphic(new FontIcon("fth-check"));
        btnSaveNote.getStyleClass().add("qh-tool-btn");
        btnSaveNote.setOnAction(e -> handleSaveNote());

        noteBar.getChildren().addAll(tagsContainer, noteInput, btnAddTag, btnSaveNote);

        // 3. Error Banner (hidden by default)
        errorBanner = new HBox(8);
        errorBanner.getStyleClass().add("qh-error-banner");
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);

        FontIcon alertIcon = new FontIcon("fth-alert-triangle");
        alertIcon.setStyle("-fx-icon-color: -color-danger-fg;");
        errorMsgLabel = new Label();
        errorMsgLabel.getStyleClass().add("qh-error-text");
        HBox.setHgrow(errorMsgLabel, Priority.ALWAYS);
        errorBanner.getChildren().addAll(alertIcon, errorMsgLabel);

        // 4. Monaco Editor Preview
        monacoPreview = new EmbeddedMonaco("history-preview", "sql", "-- Chọn một truy vấn để xem nội dung");
        monacoPreview.setReadOnly(true);
        VBox.setVgrow(monacoPreview.node(), Priority.ALWAYS);

        // 5. Metadata Footer
        HBox metaBar = new HBox(16);
        metaBar.getStyleClass().add("qh-preview-footer");

        metaServer = new Label("-");
        metaServer.getStyleClass().add("qh-meta-value");
        metaDb = new Label("-");
        metaDb.getStyleClass().add("qh-meta-value");
        metaUser = new Label("-");
        metaUser.getStyleClass().add("qh-meta-value");
        metaDuration = new Label("-");
        metaDuration.getStyleClass().add("qh-meta-value");
        metaSize = new Label("-");
        metaSize.getStyleClass().add("qh-meta-value");

        metaBar.getChildren().addAll(
                buildMetaItem("Server:", metaServer),
                buildMetaItem("Database:", metaDb),
                buildMetaItem("User:", metaUser),
                buildMetaItem("Duration:", metaDuration),
                buildMetaItem("Size:", metaSize)
        );

        previewPanel.getChildren().addAll(header, noteBar, errorBanner, monacoPreview.node(), metaBar);
        return previewPanel;
    }

    private HBox buildMetaItem(String labelText, Label valLabel) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("qh-meta-label");
        HBox box = new HBox(4, lbl, valLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private HBox buildBottomStatusbar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("qh-bottom-statusbar");

        Label hint1 = createKbdHint("Enter", "Mở tab mới");
        Label hint2 = createKbdHint("Ctrl+D", "Yêu thích");
        Label hint3 = createKbdHint("Ctrl+F", "Tìm kiếm");
        Label hint4 = createKbdHint("Delete", "Xóa");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label engineLabel = new Label("Storage: SQLite (WAL: " + HistoryLoggingService.HISTORY_DB_NAME + ") · Max retention: 200 runs / doc");
        engineLabel.setStyle("-fx-text-fill: -color-fg-muted;");

        bar.getChildren().addAll(hint1, hint2, hint3, hint4, spacer, engineLabel);
        return bar;
    }

    private Label createKbdHint(String key, String desc) {
        Label lbl = new Label();
        lbl.setText(key + " " + desc);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
        return lbl;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Data Loading & Filtering
    // ─────────────────────────────────────────────────────────────────────────────

    private void refreshDocumentList(boolean autoSelectFirst) {
        String search = searchField != null ? searchField.getText() : null;
        String srv = serverCombo != null && serverCombo.getValue() != null && !"Tất cả Server".equals(serverCombo.getValue())
                ? serverCombo.getValue() : null;
        String db = databaseCombo != null && databaseCombo.getValue() != null && !"Tất cả Database".equals(databaseCombo.getValue())
                ? databaseCombo.getValue() : null;

        Long fromMs = null;
        Long toMs = null;
        if (dateRangeCombo != null && dateRangeCombo.getValue() != null) {
            long now = System.currentTimeMillis();
            switch (dateRangeCombo.getValue()) {
                case "Hôm nay" -> fromMs = now - 86400000L;
                case "Hôm qua" -> {
                    fromMs = now - (2 * 86400000L);
                    toMs = now - 86400000L;
                }
                case "7 ngày qua" -> fromMs = now - (7 * 86400000L);
                case "30 ngày qua" -> fromMs = now - (30 * 86400000L);
            }
        }

        List<HistoryDocument> docs = historyService.getDocuments(search, favOnlyMode, srv, db, fromMs, toMs);
        documentsList.setAll(docs);
        docCountLabel.setText(docs.size() + " files");

        if (!docs.isEmpty() && autoSelectFirst) {
            documentsListView.getSelectionModel().select(0);
        } else if (docs.isEmpty()) {
            selectedDocument = null;
            entriesList.clear();
            entryCountPill.setText("0 lần");
            clearPreview();
        }
    }

    private void loadEntriesForSelectedDocument() {
        if (selectedDocument == null) {
            entriesList.clear();
            entriesDocTitle.setText("None");
            entryCountPill.setText("0 lần");
            clearPreview();
            return;
        }

        entriesDocTitle.setText(selectedDocument.getDisplayName());

        Integer statusFilter = null;
        Integer entryType = null;
        boolean favOnly = false;

        switch (currentFastFilter) {
            case "ok" -> statusFilter = HistoryEntry.STATUS_OK;
            case "error" -> statusFilter = HistoryEntry.STATUS_ERROR;
            case "selection" -> {
                // selection filter
            }
            case "favorite" -> favOnly = true;
        }

        String search = searchField != null ? searchField.getText() : null;
        String tag = tagCombo != null && tagCombo.getValue() != null && !"Tất cả nhãn".equals(tagCombo.getValue())
                ? tagCombo.getValue() : null;

        List<HistoryEntry> entries = historyService.getEntries(
                selectedDocument.getDocId(), statusFilter, search, null, null, null, null, tag, entryType, favOnly
        );

        if ("selection".equals(currentFastFilter)) {
            entries = entries.stream().filter(HistoryEntry::isSelection).toList();
        }

        entriesList.setAll(entries);
        entryCountPill.setText(entries.size() + " lần");

        if (!entries.isEmpty()) {
            entriesListView.getSelectionModel().select(0);
        } else {
            selectedEntry = null;
            clearPreview();
        }
    }

    private void loadEntryPreview(HistoryEntry entry) {
        if (entry == null) {
            clearPreview();
            return;
        }

        HistoryEntry detail = historyService.getEntryDetail(entry.getEntryId());
        if (detail == null) {
            clearPreview();
            return;
        }

        previewTitle.setText(selectedDocument != null ? selectedDocument.getDisplayName() : "Untitled.sql");
        previewTimestamp.setText(DATE_FORMAT.format(new Date(detail.getExecutedAt())));

        // Status pill
        previewStatusPill.getStyleClass().removeAll("qh-status-ok", "qh-status-error", "qh-status-cancelled");
        if (detail.isSuccess()) {
            previewStatusPill.setText("Thành công");
            previewStatusPill.getStyleClass().add("qh-status-ok");
            errorBanner.setVisible(false);
            errorBanner.setManaged(false);
        } else if (detail.isError()) {
            previewStatusPill.setText("Lỗi");
            previewStatusPill.getStyleClass().add("qh-status-error");
            errorMsgLabel.setText(detail.getErrorSummary() != null ? detail.getErrorSummary() : "Lỗi thực thi SQL");
            errorBanner.setVisible(true);
            errorBanner.setManaged(true);
        } else {
            previewStatusPill.setText("Đã hủy");
            previewStatusPill.getStyleClass().add("qh-status-cancelled");
            errorBanner.setVisible(false);
            errorBanner.setManaged(false);
        }

        // Star favorite icon
        updatePreviewStarIcon(detail.isFavorite());

        // Note input
        noteInput.setText(detail.getNote() != null ? detail.getNote() : "");

        // Tags
        tagsContainer.getChildren().clear();
        for (String t : detail.getTags()) {
            HBox chip = new HBox(4);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("qh-tag-chip");
            Label lbl = new Label(t);
            Button removeBtn = new Button();
            removeBtn.setGraphic(new FontIcon("fth-x"));
            removeBtn.getStyleClass().add("qh-icon-btn");
            removeBtn.setStyle("-fx-padding: 0;");
            removeBtn.setOnAction(e -> {
                historyService.removeEntryTag(detail.getEntryId(), t);
                loadEntryPreview(entry);
            });
            chip.getChildren().addAll(lbl, removeBtn);
            tagsContainer.getChildren().add(chip);
        }

        // Monaco content
        String sql = detail.getSql() != null ? detail.getSql() : "";
        monacoPreview.setText(sql);

        // Metadata
        metaServer.setText(detail.getServer() != null && !detail.getServer().isBlank() ? detail.getServer() : "-");
        metaDb.setText(detail.getDatabaseName() != null && !detail.getDatabaseName().isBlank() ? detail.getDatabaseName() : "-");
        metaUser.setText(detail.getLoginName() != null && !detail.getLoginName().isBlank() ? detail.getLoginName() : "-");
        metaDuration.setText(detail.getDurationMs() != null ? detail.getDurationMs() + " ms" : "-");
        metaSize.setText(detail.getRawSize() > 0 ? formatBytes(detail.getRawSize()) : "-");
    }

    private void updatePreviewStarIcon(boolean isFav) {
        if (isFav) {
            previewStarIcon.setIconLiteral("fth-star");
            previewStarIcon.setStyle("-fx-icon-color: -color-warning-fg;");
        } else {
            previewStarIcon.setIconLiteral("fth-star");
            previewStarIcon.setStyle("-fx-icon-color: -color-fg-muted;");
        }
    }

    private void clearPreview() {
        previewTitle.setText("Untitled.sql");
        previewTimestamp.setText("");
        previewStatusPill.setText("-");
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);
        noteInput.clear();
        tagsContainer.getChildren().clear();
        monacoPreview.setText("-- Chọn một truy vấn để xem nội dung");
        metaServer.setText("-");
        metaDb.setText("-");
        metaUser.setText("-");
        metaDuration.setText("-");
        metaSize.setText("-");
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Actions & Handlers
    // ─────────────────────────────────────────────────────────────────────────────

    private void handleOpenInNewTab() {
        if (selectedEntry == null) {
            NotificationService.getInstance().showWarning("Query History", "Vui lòng chọn một truy vấn để mở.");
            return;
        }
        HistoryEntry detail = historyService.getEntryDetail(selectedEntry.getEntryId());
        if (detail == null || detail.getSql() == null || detail.getSql().isBlank()) {
            NotificationService.getInstance().showWarning("Query History", "Không có nội dung SQL để mở.");
            return;
        }

        if (openInNewTabHandler != null) {
            openInNewTabHandler.accept(detail.getSql());
            chrome.close();
        }
    }

    private void handleCopyCurrentSql() {
        String sql = monacoPreview.getText();
        if (sql == null || sql.isBlank()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(sql);
        Clipboard.getSystemClipboard().setContent(content);
        NotificationService.getInstance().showSuccess("Đã sao chép", "Đã sao chép câu lệnh SQL vào Clipboard.");
    }

    private void handleToggleEntryFavorite() {
        if (selectedEntry == null) return;
        boolean res = historyService.toggleEntryFavorite(selectedEntry.getEntryId());
        selectedEntry.setFavorite(res);
        updatePreviewStarIcon(res);
        entriesListView.refresh();
    }

    private void handleSaveNote() {
        if (selectedEntry == null) return;
        String note = noteInput.getText();
        historyService.updateEntryNote(selectedEntry.getEntryId(), note);
        selectedEntry.setNote(note);
        entriesListView.refresh();
        NotificationService.getInstance().showSuccess("Ghi chú", "Đã lưu ghi chú cho lần chạy này.");
    }

    private void handleAddTagPrompt() {
        if (selectedEntry == null) return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Gắn nhãn (Tag)");
        dialog.setHeaderText("Nhập tên nhãn cho truy vấn này:");
        dialog.setContentText("Tag name:");
        dialog.initOwner(chrome.stage());

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(tag -> {
            if (!tag.isBlank()) {
                historyService.addEntryTag(selectedEntry.getEntryId(), tag.trim());
                loadEntryPreview(selectedEntry);
                entriesListView.refresh();
            }
        });
    }

    private void handleOpenContainingFolder() {
        if (selectedDocument == null || selectedDocument.getFilePath() == null) {
            NotificationService.getInstance().showWarning("File Folder", "Tài liệu này là tab tạm chưa lưu ra file trên ổ đĩa.");
            return;
        }
        try {
            Path path = Path.of(selectedDocument.getFilePath());
            if (Files.exists(path)) {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(path.getParent().toFile());
                }
            } else {
                NotificationService.getInstance().showWarning("File Folder", "File không còn tồn tại trên ổ đĩa: " + path);
            }
        } catch (Exception e) {
            NotificationService.getInstance().showError("File Folder", "Không thể mở thư mục: " + e.getMessage());
        }
    }

    private void handleDeleteCurrentSelection() {
        if (selectedEntry != null) {
            boolean confirmed = ConfirmDialog.show(chrome.stage(), "Xóa lịch sử",
                    "Bạn có chắc muốn xóa bản ghi lịch sử này không?",
                    "Hành động này không thể hoàn tác.", "Xóa", true);
            if (confirmed) {
                historyService.deleteEntry(selectedEntry.getEntryId());
                loadEntriesForSelectedDocument();
                NotificationService.getInstance().showSuccess("Đã xóa", "Đã xóa bản ghi lịch sử.");
            }
        } else if (selectedDocument != null) {
            handleDeleteDocument();
        }
    }

    private void handleDeleteDocument() {
        if (selectedDocument == null) return;
        boolean confirmed = ConfirmDialog.show(chrome.stage(), "Xóa lịch sử document",
                "Bạn có chắc muốn xóa toàn bộ lịch sử của '" + selectedDocument.getDisplayName() + "' không?",
                "Tất cả các lần chạy của document này sẽ bị xóa vĩnh viễn.", "Xóa toàn bộ", true);
        if (confirmed) {
            historyService.deleteDocument(selectedDocument.getDocId());
            refreshDocumentList(true);
            NotificationService.getInstance().showSuccess("Đã xóa", "Đã xóa toàn bộ lịch sử document.");
        }
    }

    private void handleExportJson() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Xuất lịch sử truy vấn ra JSON");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        chooser.setInitialFileName("query_history_" + System.currentTimeMillis() + ".json");
        File file = chooser.showSaveDialog(chrome.stage());
        if (file == null) return;

        try {
            historyService.exportJson(file);
            NotificationService.getInstance().showSuccess("Xuất lịch sử", "Đã xuất lịch sử thành công ra file:\n" + file.getName());
        } catch (Exception e) {
            NotificationService.getInstance().showError("Xuất lịch sử thất bại", e.getMessage());
        }
    }

    private void handleImportJson() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Nhập lịch sử truy vấn từ JSON");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = chooser.showOpenDialog(chrome.stage());
        if (file == null) return;

        try {
            int count = historyService.importJson(file);
            refreshDocumentList(true);
            NotificationService.getInstance().showSuccess("Nhập lịch sử", "Đã nhập thành công " + count + " bản ghi lịch sử.");
        } catch (Exception e) {
            NotificationService.getInstance().showError("Nhập lịch sử thất bại", e.getMessage());
        }
    }

    private void handleGlobalKeys(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.F) {
            searchField.requestFocus();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.D) {
            handleToggleEntryFavorite();
            event.consume();
        } else if (event.getCode() == KeyCode.DELETE) {
            handleDeleteCurrentSelection();
            event.consume();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Custom List Cells
    // ─────────────────────────────────────────────────────────────────────────────

    private class DocumentListCell extends ListCell<HistoryDocument> {
        private final HBox cellBox = new HBox(8);
        private final FontIcon starIcon = new FontIcon("fth-star");
        private final Button starBtn = new Button("", starIcon);
        private final FontIcon docIcon = new FontIcon("fth-file-text");
        private final VBox textBox = new VBox(2);
        private final Label nameLabel = new Label();
        private final Label subLabel = new Label();
        private final Label countBadge = new Label();
        private final Region spacer = new Region();

        public DocumentListCell() {
            cellBox.setAlignment(Pos.CENTER_LEFT);
            cellBox.getStyleClass().add("qh-doc-cell");

            starBtn.getStyleClass().add("qh-icon-btn");
            starBtn.setOnAction(e -> {
                HistoryDocument doc = getItem();
                if (doc != null) {
                    boolean res = historyService.toggleDocStar(doc.getDocId());
                    doc.setStarred(res);
                    updateStarStyle(res);
                }
            });

            nameLabel.getStyleClass().add("qh-doc-name");
            subLabel.getStyleClass().add("qh-doc-sub");
            textBox.getChildren().addAll(nameLabel, subLabel);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            countBadge.getStyleClass().add("qh-count-badge");

            cellBox.getChildren().addAll(starBtn, docIcon, textBox, countBadge);

            // Context Menu
            ContextMenu cm = new ContextMenu();
            MenuItem miStar = new MenuItem("Đánh dấu sao");
            miStar.setOnAction(e -> {
                if (getItem() != null) {
                    boolean res = historyService.toggleDocStar(getItem().getDocId());
                    getItem().setStarred(res);
                    updateStarStyle(res);
                }
            });
            MenuItem miFolder = new MenuItem("Mở thư mục chứa file");
            miFolder.setOnAction(e -> handleOpenContainingFolder());
            MenuItem miDel = new MenuItem("Xóa lịch sử document");
            miDel.setOnAction(e -> handleDeleteDocument());
            cm.getItems().addAll(miStar, miFolder, new SeparatorMenuItem(), miDel);
            setContextMenu(cm);
        }

        private void updateStarStyle(boolean starred) {
            if (starred) {
                starIcon.setStyle("-fx-icon-color: -color-warning-fg;");
            } else {
                starIcon.setStyle("-fx-icon-color: -color-fg-muted;");
            }
        }

        @Override
        protected void updateItem(HistoryDocument doc, boolean empty) {
            super.updateItem(doc, empty);
            if (empty || doc == null) {
                setGraphic(null);
            } else {
                nameLabel.setText(doc.getDisplayName());
                updateStarStyle(doc.isStarred());

                String sub = doc.getFilePath() != null ? doc.getFilePath()
                        : (doc.getLastServer() != null ? doc.getLastServer() + " / " + (doc.getLastDatabase() != null ? doc.getLastDatabase() : "") : "");
                subLabel.setText(sub);
                countBadge.setText(doc.getEntryCount() + " runs");
                setGraphic(cellBox);
            }
        }
    }

    private class EntryListCell extends ListCell<HistoryEntry> {
        private final HBox cellBox = new HBox(8);
        private final FontIcon statusIcon = new FontIcon("fth-check-circle");
        private final VBox textBox = new VBox(2);
        private final HBox topRow = new HBox(6);
        private final Label timeLabel = new Label();
        private final Label durLabel = new Label();
        private final Label runCountBadge = new Label();
        private final FontIcon starIcon = new FontIcon("fth-star");
        private final Label sqlSnippetLabel = new Label();
        private final Label errSnippetLabel = new Label();
        private final HBox tagsRow = new HBox(4);

        public EntryListCell() {
            cellBox.setAlignment(Pos.CENTER_LEFT);
            cellBox.getStyleClass().add("qh-entry-cell");

            topRow.setAlignment(Pos.CENTER_LEFT);
            timeLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;");
            durLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
            runCountBadge.getStyleClass().add("qh-run-badge");
            starIcon.setStyle("-fx-icon-color: -color-warning-fg;");

            topRow.getChildren().addAll(statusIcon, timeLabel, durLabel, runCountBadge, starIcon);

            sqlSnippetLabel.setStyle("-fx-font-size: 11px; -fx-font-family: monospace; -fx-text-fill: -color-fg-muted;");
            sqlSnippetLabel.setMaxWidth(260);

            errSnippetLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-danger-fg;");
            errSnippetLabel.setMaxWidth(260);

            tagsRow.setAlignment(Pos.CENTER_LEFT);

            textBox.getChildren().addAll(topRow, sqlSnippetLabel, errSnippetLabel, tagsRow);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            cellBox.getChildren().addAll(textBox);

            // Context Menu
            ContextMenu cm = new ContextMenu();
            MenuItem miOpen = new MenuItem("Mở vào Tab mới");
            miOpen.setOnAction(e -> handleOpenInNewTab());
            MenuItem miCopy = new MenuItem("Sao chép SQL");
            miCopy.setOnAction(e -> handleCopyCurrentSql());
            MenuItem miFav = new MenuItem("Yêu thích");
            miFav.setOnAction(e -> handleToggleEntryFavorite());
            MenuItem miTag = new MenuItem("Gắn nhãn (Tag)");
            miTag.setOnAction(e -> handleAddTagPrompt());
            MenuItem miNote = new MenuItem("Thêm ghi chú");
            miNote.setOnAction(e -> {
                if (getItem() != null) noteInput.requestFocus();
            });
            MenuItem miDel = new MenuItem("Xóa lần chạy này");
            miDel.setOnAction(e -> handleDeleteCurrentSelection());
            cm.getItems().addAll(miOpen, miCopy, miFav, miTag, miNote, new SeparatorMenuItem(), miDel);
            setContextMenu(cm);
        }

        @Override
        protected void updateItem(HistoryEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
            } else {
                timeLabel.setText(TIME_FORMAT.format(new Date(entry.getExecutedAt())));
                durLabel.setText(entry.getDurationMs() != null ? entry.getDurationMs() + "ms" : "-");

                if (entry.getEntryType() == HistoryEntry.TYPE_SNAPSHOT) {
                    statusIcon.setIconLiteral("fth-file");
                    statusIcon.setStyle("-fx-icon-color: -color-accent-fg;");
                } else if (entry.isSuccess()) {
                    statusIcon.setIconLiteral("fth-check-circle");
                    statusIcon.setStyle("-fx-icon-color: -color-success-fg;");
                } else if (entry.isError()) {
                    statusIcon.setIconLiteral("fth-x-circle");
                    statusIcon.setStyle("-fx-icon-color: -color-danger-fg;");
                } else {
                    statusIcon.setIconLiteral("fth-slash");
                    statusIcon.setStyle("-fx-icon-color: -color-warning-fg;");
                }

                if (entry.getRunCount() > 1) {
                    runCountBadge.setText("x" + entry.getRunCount());
                    runCountBadge.setVisible(true);
                    runCountBadge.setManaged(true);
                } else {
                    runCountBadge.setVisible(false);
                    runCountBadge.setManaged(false);
                }

                starIcon.setVisible(entry.isFavorite());
                starIcon.setManaged(entry.isFavorite());

                // Subtitle: Note or SQL or Error
                if (entry.getNote() != null && !entry.getNote().isBlank()) {
                    sqlSnippetLabel.setText("📝 " + entry.getNote());
                } else {
                    sqlSnippetLabel.setText(entry.getServer() + " · " + entry.getDatabaseName());
                }

                if (entry.isError() && entry.getErrorSummary() != null) {
                    errSnippetLabel.setText(entry.getErrorSummary());
                    errSnippetLabel.setVisible(true);
                    errSnippetLabel.setManaged(true);
                } else {
                    errSnippetLabel.setVisible(false);
                    errSnippetLabel.setManaged(false);
                }

                // Tags chips
                tagsRow.getChildren().clear();
                for (String t : entry.getTags()) {
                    Label chip = new Label(t);
                    chip.getStyleClass().add("qh-tag-chip");
                    tagsRow.getChildren().add(chip);
                }

                setGraphic(cellBox);
            }
        }
    }
}
