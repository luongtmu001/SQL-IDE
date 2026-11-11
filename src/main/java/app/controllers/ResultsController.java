package app.controllers;

import app.models.ConsoleMessage;
import app.models.PhysicalExecutionPlan;
import app.models.PlanOperatorNode;
import app.models.QueryResult;
import app.models.QueryResultColumn;
import app.models.QueryResultRow;
import app.services.QueryResultMockService;
import app.ui.results.DesktopGridExportService;
import app.ui.results.GridDataExportUtils;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * ResultsController — Controller for Query Results Data Grid (Single & Multi-Grid),
 * SQL Output Messages, Execution Timeline, and Execution Plan.
 */
public class ResultsController implements Initializable {

    // TabPane
    @FXML private TabPane resultsTabPane;
    @FXML private Tab resultsTab;
    @FXML private Tab messagesTab;       // New: Plaintext SQL Messages (PRINT, RAISERROR, row count)
    @FXML private Tab timelineTab;       // Renamed: Execution Timeline table
    @FXML private Tab executionPlanTab;
    @FXML private Tab terminalTab;

    // Results Tab Components
    @FXML private TableView<QueryResultRow> resultsTable;
    @FXML private ScrollPane multiGridScroll;
    @FXML private VBox multiGridBox;
    /** "Local · B10_PhucLoi · dbo" shown in the footer (set by MainController before each run). */
    private String footerContext = null;
    @FXML private TextField resultsFilterField;
    @FXML private Label resultsStatsLabel;
    @FXML private FontIcon maximizeIcon;
    @FXML private VBox emptyStateBox;
    @FXML private StackPane resultsStackPane;
    @FXML private VBox loadingOverlay;
    private app.ui.components.LoadingOverlay loadingSupport;

    // Footer Execution Stats Bar (matching user screenshot)
    @FXML private HBox resultsFooterBar;
    @FXML private Label footerRowsLabel;
    @FXML private Label footerDurationBadge;
    @FXML private Label footerSizeBadge;
    @FXML private Label footerConnLabel;

    // SQL Output Messages Tab Components
    @FXML private TextArea sqlMessagesArea;
    @FXML private ToggleButton sqlMessagesWrapToggle;

    private Runnable onUndockHandler;
    private BiConsumer<String, String> onOpenInNewTabHandler;

    // Timeline Tab Components
    @FXML private TableView<ConsoleMessage> messagesTable;
    @FXML private TableColumn<ConsoleMessage, String> msgTimeCol;
    @FXML private TableColumn<ConsoleMessage, ConsoleMessage.Level> msgLevelCol;
    @FXML private TableColumn<ConsoleMessage, String> msgTextCol;
    @FXML private TableColumn<ConsoleMessage, String> msgDurationCol;

    // Terminal Tab Components
    @FXML private TextArea terminalOutput;
    @FXML private TextField terminalInput;

    // Execution Plan Tab Components
    @FXML private VBox planContainer;
    @FXML private Button btnFetchEstimatedPlan;
    @FXML private Button btnFetchActualPlan;
    @FXML private ToggleButton togglePlanViewMode;
    @FXML private Button btnCopyPlan;
    @FXML private Label planDialectBadge;
    @FXML private Label planTypeBadge;
    @FXML private Label planTotalCostLabel;
    @FXML private Label planOperatorsCountLabel;
    @FXML private Label planDurationLabel;
    @FXML private VBox planEmptyStateBox;
    @FXML private VBox planLoadingBox;
    @FXML private SplitPane planVisualSplitPane;
    @FXML private TreeView<PlanOperatorNode> planTreeView;
    @FXML private Label inspectorOpBadge;
    @FXML private TableView<Map.Entry<String, String>> inspectorPropertiesTable;
    @FXML private TableColumn<Map.Entry<String, String>, String> propNameCol;
    @FXML private TableColumn<Map.Entry<String, String>, String> propValueCol;
    @FXML private VBox planRawBox;
    @FXML private TextArea planRawTextArea;

    // Execution Plan State
    private PhysicalExecutionPlan currentExecutionPlan;
    private final ObservableList<Map.Entry<String, String>> inspectorPropertiesList = FXCollections.observableArrayList();
    private Runnable onFetchEstimatedPlanCallback;
    private Runnable onFetchActualPlanCallback;

    // Data lists
    /** Rows of the single grid (swapped with the grid when another editor tab is shown, D4.3). */
    private ObservableList<QueryResultRow> masterRows = FXCollections.observableArrayList();
    private FilteredList<QueryResultRow> filteredRows;
    private final ObservableList<ConsoleMessage> messagesList = FXCollections.observableArrayList();

    // Column filtering state
    /** Filters of the single grid; stacked / tabbed grids have their own (see subFilterStates). */
    private app.ui.results.GridFilterState mainFilterState;
    private final List<app.ui.results.GridFilterState> subFilterStates = new ArrayList<>();
    /** Tabs mode for several result sets (setting grid.resultLayout = tabs), created on first use. */
    private TabPane multiGridTabs;

    // Multi-cell range selection anchors
    private int anchorRow = -1;
    private int anchorCol = -1;

    // Terminal history
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    private QueryResult currentResult;
    private List<QueryResult> currentResultSets = new ArrayList<>();
    private final Set<TableColumn<QueryResultRow, ?>> pinnedColumns = new HashSet<>();

    public record SelectedRangeData(
        List<String> selectedColNames,
        List<List<Object>> selectedRowsData,
        Object activeCellValue,
        String activeColName,
        TableColumn<QueryResultRow, ?> activeTableColumn
    ) {}

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupResultsTable();
        setupMessagesTable();
        setupTerminal();
        setupSqlMessagesArea();
        setupExecutionPlan();
        loadInitialData();
        setupLayoutIntegration();
        setupSettingsIntegration();
        setupResultsTabHeader();
        if (loadingOverlay != null) loadingSupport = app.ui.components.LoadingOverlay.attach(loadingOverlay);
    }

    // ------------------------------------------------------------------ Results tab header: "Results (n)" badge

    /** Number of result sets of the last run, in a circle after "Results" (user 2026-11-06); hidden when 0. */
    private Label resultsCountBadge;

    private void setupResultsTabHeader() {
        if (resultsTab == null) return;
        javafx.scene.Node icon = resultsTab.getGraphic();
        Label title = new Label("Results");
        title.getStyleClass().add("results-tab-title");
        resultsCountBadge = new Label();
        resultsCountBadge.getStyleClass().add("results-count-badge");
        resultsCountBadge.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox box = new HBox(5);
        if (icon != null) box.getChildren().add(icon);
        box.getChildren().addAll(title, resultsCountBadge);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        resultsTab.getStyleClass().add("results-main-tab");
        resultsTab.setText("");
        resultsTab.setGraphic(box);
        setResultsTableCount(0);
    }

    private void setResultsTableCount(int tables) {
        if (resultsCountBadge == null) return;
        resultsCountBadge.setText(tables > 99 ? "99+" : String.valueOf(tables));
        resultsCountBadge.setVisible(tables > 0);
        resultsCountBadge.setManaged(tables > 0);
        if (resultsTab != null) {
            resultsTab.setTooltip(tables > 0 ? new Tooltip(tables + " bảng kết quả") : null);
        }
    }

    private void setupSettingsIntegration() {
        app.services.SettingsService.getInstance().addSettingsListener(this::applySettings);
        applySettings(app.services.SettingsService.getInstance().getCurrentSettings());
    }

    public void applySettings(app.models.IdeSettings settings) {
        if (settings == null) return;

        // 1. Grid Font & Size
        String gridFont = settings.getGrid() != null ? settings.getGrid().getFontFamily() : "Inter";
        int gridFontSize = settings.getGrid() != null ? settings.getGrid().getFontSize() : 12;
        String gridStyle = String.format(java.util.Locale.ROOT, "-fx-font-family: '%s'; -fx-font-size: %dpx;", gridFont, gridFontSize);
        // cells inherit this font (components.css sets none on .table-cell); the row height follows the size
        double rowHeight = Math.max(24, gridFontSize * 2);
        if (resultsTable != null) {
            resultsTable.setStyle(gridStyle);
            if (resultsTable.getFixedCellSize() > 0) resultsTable.setFixedCellSize(rowHeight);
            if (!resultsTable.getColumns().isEmpty() && "#".equals(resultsTable.getColumns().get(0).getText())) {
                resultsTable.getColumns().get(0).setVisible(settings.isShowRowNumbers());
            }
        }
        List<javafx.scene.Node> gridHosts = new ArrayList<>();
        if (multiGridBox != null) gridHosts.add(multiGridBox);
        if (multiGridTabs != null) { // tabs mode: grids already built (the others are built with the current setting)
            for (Tab t : multiGridTabs.getTabs()) if (t.getContent() != null) gridHosts.add(t.getContent());
        }
        for (javafx.scene.Node host : gridHosts) {
            for (javafx.scene.Node g : host.lookupAll(".results-table")) {
                if (g instanceof TableView<?> tv) {
                    tv.setStyle(gridStyle);
                    if (tv.getFixedCellSize() > 0) tv.setFixedCellSize(rowHeight);
                    if (!tv.getColumns().isEmpty() && "#".equals(tv.getColumns().get(0).getText())) {
                        tv.getColumns().get(0).setVisible(settings.isShowRowNumbers());
                    }
                }
            }
        }

        // 2. Messages Font & Size
        String msgFont = settings.getMessages() != null ? settings.getMessages().getFontFamily() : "Fira Code";
        int msgFontSize = settings.getMessages() != null ? settings.getMessages().getFontSize() : 11;
        if (sqlMessagesArea != null) {
            sqlMessagesArea.setStyle(String.format(java.util.Locale.ROOT, "-fx-font-family: '%s', monospace; -fx-font-size: %dpx;", msgFont, msgFontSize));
        }
    }

    private void setupLayoutIntegration() {
        app.services.LayoutService layout = app.services.LayoutService.getInstance();
        layout.resultsMaximizedProperty().addListener((obs, oldVal, newVal) -> {
            if (maximizeIcon != null) {
                maximizeIcon.setIconLiteral(newVal ? "fth-minimize-2" : "fth-maximize-2");
            }
        });

        int savedTabIdx = layout.getActiveResultTabIndex();
        if (savedTabIdx >= 0 && savedTabIdx < resultsTabPane.getTabs().size()) {
            resultsTabPane.getSelectionModel().select(savedTabIdx);
        }

        resultsTabPane.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            if (newIdx != null && newIdx.intValue() >= 0) {
                layout.setActiveResultTabIndex(newIdx.intValue());
            }
        });
    }

    public void setOnUndockHandler(Runnable onUndockHandler) {
        this.onUndockHandler = onUndockHandler;
    }

    public void setOnOpenInNewTabHandler(BiConsumer<String, String> onOpenInNewTabHandler) {
        this.onOpenInNewTabHandler = onOpenInNewTabHandler;
    }

    @FXML
    public void handleToggleMaximize() {
        app.services.LayoutService.getInstance().toggleMaximizeResults();
    }

    @FXML
    public void handleUndock() {
        if (onUndockHandler != null) {
            onUndockHandler.run();
        }
    }

    private void setupSqlMessagesArea() {
        if (sqlMessagesArea != null) {
            sqlMessagesArea.setEditable(false);
            sqlMessagesArea.setWrapText(false);
            appendSqlMessage("SQL Query Console initialized.");
        }
    }

    private void setupResultsTable() {
        resultsTable.getSelectionModel().setCellSelectionEnabled(true);
        resultsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        filteredRows = new FilteredList<>(masterRows, p -> true);
        resultsTable.setItems(filteredRows);
        mainFilterState = new app.ui.results.GridFilterState(masterRows, filteredRows);
        mainFilterState.setOnChanged(this::updateStatsLabel);
        app.ui.results.GridFilterState.attach(resultsTable, mainFilterState);

        if (resultsFilterField != null) {
            // debounced: each run scans every cell of every row (33k x 279 per keystroke was seconds of freeze)
            quickFilterDebounce.setOnFinished(e -> applyCombinedFilter());
            resultsFilterField.textProperty().addListener((obs, oldVal, newVal) -> quickFilterDebounce.playFromStart());
        }

        installGrid(resultsTable);
    }

    // ------------------------------------------------------------------ active grid (2026-11-05)
    /** Grid the context menu / shortcuts act on: the single grid or the stacked / tabbed one clicked last. */
    private TableView<QueryResultRow> activeGrid;
    private static final String GRID_RESULT_KEY = "grid.result";
    private static final String GRID_PINNED_KEY = "grid.pinned";

    private TableView<QueryResultRow> grid() {
        return activeGrid != null ? activeGrid : resultsTable;
    }

    private ObservableList<QueryResultRow> gridRows() {
        return grid().getItems();
    }

    private ObservableList<QueryResultRow> gridMaster() {
        var st = app.ui.results.GridFilterState.of(grid());
        return st != null ? st.master() : masterRows;
    }

    private QueryResult gridResult() {
        Object r = grid().getProperties().get(GRID_RESULT_KEY);
        return r instanceof QueryResult q ? q : currentResult;
    }

    @SuppressWarnings("unchecked")
    private Set<TableColumn<QueryResultRow, ?>> gridPinned() {
        return (Set<TableColumn<QueryResultRow, ?>>) grid().getProperties()
                .computeIfAbsent(GRID_PINNED_KEY, k -> new HashSet<TableColumn<QueryResultRow, ?>>());
    }

    /** Context menu (LLTool grid menu) + Ctrl+C / Ctrl+Shift+C / Ctrl+A / Ctrl+Shift+S for one grid. */
    private void installGrid(TableView<QueryResultRow> table) {
        installAutoFit(table);
        table.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> activeGrid = table);
        table.focusedProperty().addListener((o, was, now) -> {
            if (now) activeGrid = table;
        });
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("grid-context-menu");
        table.setOnContextMenuRequested(event -> {
            activeGrid = table;
            menu.getItems().clear();
            populateContextMenu(menu);
        });
        table.setContextMenu(menu);
        table.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            activeGrid = table;
            if (event.isShortcutDown()) {
                if (event.getCode() == KeyCode.C) {
                    event.consume();
                    if (event.isShiftDown()) handleCopyWithHeaders(); else handleCopy();
                } else if (event.getCode() == KeyCode.A) {
                    event.consume();
                    handleSelectAll();
                } else if (event.getCode() == KeyCode.S && event.isShiftDown()) {
                    event.consume();
                    handleSaveResultsAs();
                } else if (event.getCode() == KeyCode.F) {
                    event.consume();
                    focusFilterField();
                } else if (event.getCode() == KeyCode.P) {
                    event.consume();
                    handlePrintGrid();
                }
            }
        });
    }

    private void setupKeyboardShortcuts() {
        // handled per grid in installGrid()
    }

    public void focusFilterField() {
        if (resultsFilterField != null) {
            resultsFilterField.requestFocus();
            resultsFilterField.selectAll();
        }
    }

    private static MenuItem gridItem(String text, String icon, KeyCombination accel, Runnable action) {
        MenuItem mi = new MenuItem(text);
        if (icon != null) {
            FontIcon fi = new FontIcon(icon);
            fi.getStyleClass().add("grid-menu-icon");
            mi.setGraphic(fi);
        }
        if (accel != null) mi.setAccelerator(accel);
        mi.setOnAction(e -> action.run());
        return mi;
    }

    /** Results grid context menu, same groups and order as LLTool (screenshot 2026-11-05). */
    private void populateContextMenu(ContextMenu contextMenu) {
        SelectedRangeData range = getSelectedRangeData();
        TableColumn<QueryResultRow, ?> activeCol = range.activeTableColumn();

        // 0. freeze column
        if (activeCol != null && !"#".equals(activeCol.getText())) {
            boolean isPinned = gridPinned().contains(activeCol);
            contextMenu.getItems().addAll(
                    gridItem(isPinned ? "Bỏ cố định cột [" + getColumnTitle(activeCol) + "]"
                                    : "Cố định cột [" + getColumnTitle(activeCol) + "] (Freeze)",
                            isPinned ? "mdi2p-pin-off-outline" : "mdi2p-pin-outline", null, () -> toggleFreezeColumn(activeCol)),
                    new SeparatorMenuItem());
        }

        // 1. headers and cell copy
        contextMenu.getItems().addAll(
                gridItem("Copy all Headers", "mdi2t-table-large", null, this::copyAllHeaders),
                gridItem("Copy selected Headers", "mdi2t-table-column", null, this::copySelectedHeaders),
                gridItem("Copy current cell 1:1", "mdi2n-numeric-1-box-outline", null, this::copyCurrentCell11),
                gridItem("Copy as SQL values List", "mdi2c-code-parentheses", null, this::copySqlValuesList),
                new SeparatorMenuItem());

        // 1b. structured value (JSON / XML) in the active cell
        String structFormat = GridDataExportUtils.detectStructuredCandidate(range.activeCellValue());
        if (structFormat != null) {
            contextMenu.getItems().addAll(
                    gridItem("Xem " + structFormat.toUpperCase() + " (Quick View)", "mdi2c-code-braces", null,
                            () -> showVisualizeDialog(structFormat)),
                    gridItem("Mở " + structFormat.toUpperCase() + " trong tab mới", "fth-external-link", null, () -> {
                        if (onOpenInNewTabHandler != null && range.activeCellValue() != null) {
                            onOpenInNewTabHandler.accept("view_" + structFormat + "." + structFormat, range.activeCellValue().toString());
                        }
                    }),
                    new SeparatorMenuItem());
        }

        // 2. find, aggregates, script, Excel XML, visualize
        Menu visualize = new Menu("Visualize as");
        FontIcon eye = new FontIcon("fth-eye");
        eye.getStyleClass().add("grid-menu-icon");
        visualize.setGraphic(eye);
        visualize.getItems().addAll(
                gridItem("Text", "mdi2f-format-text", null, () -> showVisualizeDialog("text")),
                gridItem("Hex", "mdi2b-barcode", null, () -> showVisualizeDialog("hex")),
                gridItem("JSON", "mdi2c-code-json", null, () -> showVisualizeDialog("json")),
                gridItem("XML", "mdi2x-xml", null, () -> showVisualizeDialog("xml")),
                gridItem("Image", "mdi2i-image-outline", null, () -> showVisualizeDialog("image")));
        contextMenu.getItems().addAll(
                gridItem("Find data in Results Grid", "fth-search", new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), this::focusFilterField),
                gridItem("Find column in Results Grid", "fth-filter", null, this::handleFindColumn),
                gridItem("Show grid aggregates", "mdi2c-calculator-variant-outline", null, this::showGridAggregates),
                gridItem("Script grid data", "mdi2s-script-text-outline", null, () -> handleScriptAsInsert(true)),
                gridItem("Copy selection as Xml Spreadsheet (Excel)", "mdi2m-microsoft-excel", null, this::copySelectionAsXmlSpreadsheet),
                visualize,
                new SeparatorMenuItem());

        // 3. clipboard / export
        contextMenu.getItems().addAll(
                gridItem("Copy", "fth-copy", new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN), this::handleCopy),
                gridItem("Copy with Headers", "mdi2c-content-copy", new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), this::handleCopyWithHeaders),
                gridItem("Select All", "mdi2c-cursor-default-outline", new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN), this::handleSelectAll),
                gridItem("Script as INSERT", "mdi2d-database-outline", null, () -> handleScriptAsInsert(false)),
                gridItem("Open in Excel", "mdi2m-microsoft-excel", null, this::handleOpenInExcel),
                gridItem("Save Results As...", "fth-save", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), this::handleSaveResultsAs),
                new SeparatorMenuItem());

        // 4. print
        contextMenu.getItems().addAll(
                gridItem("Page Setup...", "mdi2t-tune-variant", null, this::handlePageSetup),
                gridItem("Print...", "fth-printer", new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), this::handlePrintGrid));
    }

    /** Visualize as Image: byte[] or base64 / data-URI text (PNG, JPEG, GIF, BMP). */
    private void showImageDialog(Object raw, String colName) {
        byte[] bytes = null;
        try {
            if (raw instanceof byte[] b) {
                bytes = b;
            } else if (raw instanceof app.framework.query.CellValues.BinaryValue bv) {
                bytes = bv.bytes();
            } else if (raw != null) {
                String t = raw.toString().trim();
                int comma = t.startsWith("data:") ? t.indexOf(',') : -1;
                if (comma > 0) t = t.substring(comma + 1);
                if (t.startsWith("0x") || t.startsWith("0X")) {
                    String hex = t.substring(2);
                    bytes = new byte[hex.length() / 2];
                    for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
                } else {
                    bytes = java.util.Base64.getMimeDecoder().decode(t);
                }
            }
        } catch (Exception ignored) {
            bytes = null;
        }
        javafx.scene.image.Image img = bytes != null && bytes.length > 0
                ? new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)) : null;
        if (img == null || img.isError() || img.getWidth() <= 0) {
            showError("Ô [" + (colName != null ? colName : "?") + "] không chứa ảnh hợp lệ (cần byte[], base64 hoặc 0x…)");
            return;
        }
        javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(img);
        view.setPreserveRatio(true);
        view.setFitWidth(Math.min(720, img.getWidth()));
        view.setFitHeight(Math.min(520, img.getHeight()));
        Label info = new Label(String.format("%.0f × %.0f px · %,d bytes", img.getWidth(), img.getHeight(), bytes.length));
        info.getStyleClass().add("dialog-hint");
        app.ui.dialogs.DialogChrome chrome = app.ui.dialogs.DialogChrome.create(
                grid().getScene() != null ? grid().getScene().getWindow() : null,
                "Visualize as Image", "mdi2i-image-outline").subtitle(colName);
        Button close = app.ui.dialogs.DialogChrome.cancel("Đóng");
        close.setOnAction(e -> chrome.close());
        ScrollPane sp = new ScrollPane(view);
        sp.setMaxSize(740, 540);
        chrome.content(new VBox(8, sp, info)).footer(null, close);
        chrome.showAndWait();
    }

    /** Page setup of the system printer dialog (no real printing in the prototype). */
    public void handlePageSetup() {
        javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
        if (job == null) {
            showNotice("Không tìm thấy máy in trên máy này");
            return;
        }
        Window win = grid().getScene() != null ? grid().getScene().getWindow() : null;
        job.showPageSetupDialog(win);
        job.cancelJob();
    }

    private String getColumnTitle(TableColumn<QueryResultRow, ?> col) {
        if (col == null) return "";
        if (col.getGraphic() instanceof HBox hbox && !hbox.getChildren().isEmpty() && hbox.getChildren().get(0) instanceof Label lbl) {
            return lbl.getText();
        }
        return col.getText();
    }

    private void toggleFreezeColumn(TableColumn<QueryResultRow, ?> col) {
        if (col == null || "#".equals(col.getText())) return;
        String title = getColumnTitle(col);

        if (gridPinned().contains(col)) {
            gridPinned().remove(col);
            showNotice("Unpinned column: " + title);
        } else {
            gridPinned().add(col);
            grid().getColumns().remove(col);
            grid().getColumns().add(1, col); // Insert right after '#'
            showNotice("Pinned column: " + title);
        }
    }

    public SelectedRangeData getSelectedRangeData() {
        var posList = grid().getSelectionModel().getSelectedCells();
        if (posList.isEmpty() || gridResult() == null) {
            List<String> allCols = gridResult() != null
                    ? gridResult().getColumns().stream().map(QueryResultColumn::getName).toList()
                    : List.of();
            List<List<Object>> allRows = gridMaster().stream().map(QueryResultRow::getValues).toList();
            return new SelectedRangeData(allCols, allRows, null, null, null);
        }

        Set<Integer> rowIndices = new TreeSet<>();
        Set<Integer> colIndices = new TreeSet<>();
        Object activeVal = null;
        String activeColName = null;
        TableColumn<QueryResultRow, ?> activeTableCol = null;

        for (TablePosition<?, ?> pos : posList) {
            rowIndices.add(pos.getRow());
            int colIdx = pos.getColumn();
            if (colIdx >= 1) {
                colIndices.add(colIdx);
            }
        }

        TablePosition<?, ?> lastPos = posList.get(posList.size() - 1);
        if (lastPos.getColumn() >= 1 && lastPos.getRow() >= 0 && lastPos.getRow() < gridRows().size()) {
            activeTableCol = (TableColumn<QueryResultRow, ?>) lastPos.getTableColumn();
            int dataColIdx = lastPos.getColumn() - 1;
            if (dataColIdx >= 0 && dataColIdx < gridResult().getColumns().size()) {
                activeColName = gridResult().getColumns().get(dataColIdx).getName();
            }
            activeVal = activeTableCol.getCellData(lastPos.getRow());
        }

        if (colIndices.isEmpty()) {
            for (int i = 1; i <= gridResult().getColumns().size(); i++) {
                colIndices.add(i);
            }
        }

        List<String> selectedColNames = new ArrayList<>();
        for (int colIdx : colIndices) {
            int dataColIdx = colIdx - 1;
            if (dataColIdx >= 0 && dataColIdx < gridResult().getColumns().size()) {
                selectedColNames.add(gridResult().getColumns().get(dataColIdx).getName());
            }
        }

        List<List<Object>> selectedRowsData = new ArrayList<>();
        for (int r : rowIndices) {
            if (r >= 0 && r < gridRows().size()) {
                QueryResultRow row = gridRows().get(r);
                List<Object> rowVals = new ArrayList<>();
                for (int colIdx : colIndices) {
                    int dataColIdx = colIdx - 1;
                    rowVals.add(row.getValue(dataColIdx));
                }
                selectedRowsData.add(rowVals);
            }
        }

        return new SelectedRangeData(selectedColNames, selectedRowsData, activeVal, activeColName, activeTableCol);
    }

    public void selectRange(int startRow, int startCol, int endRow, int endCol) {
        int minR = Math.max(0, Math.min(startRow, endRow));
        int maxR = Math.min(gridRows().size() - 1, Math.max(startRow, endRow));
        int minC = Math.max(1, Math.min(startCol, endCol));
        int maxC = Math.min(grid().getColumns().size() - 1, Math.max(startCol, endCol));

        grid().getSelectionModel().clearSelection();
        if (maxR < minR || maxC < minC) return;
        // one batched change (rows x columns single select() calls fire one event each: 33k rows froze the UI)
        grid().getSelectionModel().selectRange(minR, grid().getColumns().get(minC), maxR, grid().getColumns().get(maxC));
    }

    public void copyAllHeaders() {
        if (gridResult() == null || gridResult().getColumns().isEmpty()) return;
        List<String> colNames = gridResult().getColumns().stream()
                .map(QueryResultColumn::getName)
                .toList();
        copyTextToClipboard(String.join("\t", colNames));
        showNotice("Copied all " + colNames.size() + " headers to clipboard");
    }

    public void copySelectedHeaders() {
        SelectedRangeData range = getSelectedRangeData();
        if (range.selectedColNames().isEmpty()) return;
        copyTextToClipboard(String.join("\t", range.selectedColNames()));
        showNotice("Copied " + range.selectedColNames().size() + " selected headers to clipboard");
    }

    public void copyCurrentCell11() {
        SelectedRangeData range = getSelectedRangeData();
        Object val = range.activeCellValue();
        String text = (val == null) ? "" : val.toString();
        copyTextToClipboard(text);
        showNotice("Copied current cell value 1:1");
    }

    public void copySqlValuesList() {
        SelectedRangeData range = getSelectedRangeData();
        String sql = GridDataExportUtils.formatSqlValuesList(range.selectedColNames(), range.selectedRowsData());
        if (!sql.isEmpty()) {
            copyTextToClipboard(sql);
            showNotice("Copied SQL values list to clipboard");
        }
    }

    public void handleCopy() {
        SelectedRangeData range = getSelectedRangeData();
        String tsv = GridDataExportUtils.formatTsv(range.selectedColNames(), range.selectedRowsData(), false);
        copyTextToClipboard(tsv);
        showNotice("Copied " + range.selectedRowsData().size() + " rows to clipboard");
    }

    public void handleCopyWithHeaders() {
        SelectedRangeData range = getSelectedRangeData();
        String tsv = GridDataExportUtils.formatTsv(range.selectedColNames(), range.selectedRowsData(), true);
        copyTextToClipboard(tsv);
        showNotice("Copied " + range.selectedRowsData().size() + " rows with headers to clipboard");
    }

    public void handleSelectAll() {
        grid().getSelectionModel().selectAll(); // one batched change, not rows x columns events
    }

    public void handleScriptAsInsert(boolean targetAll) {
        if (gridResult() == null) return;
        List<String> cols;
        List<List<Object>> rows;
        if (targetAll) {
            cols = gridResult().getColumns().stream().map(QueryResultColumn::getName).toList();
            rows = gridMaster().stream().map(QueryResultRow::getValues).toList();
        } else {
            SelectedRangeData range = getSelectedRangeData();
            cols = range.selectedColNames();
            rows = range.selectedRowsData();
        }
        String script = GridDataExportUtils.formatScriptAsInsert("#tabletmp", cols, rows);
        if (!script.isEmpty()) {
            copyTextToClipboard(script);
            showNotice("Generated INSERT script (" + rows.size() + " rows) to clipboard");
        }
    }

    public void copySelectionAsXmlSpreadsheet() {
        SelectedRangeData range = getSelectedRangeData();
        String xml = GridDataExportUtils.formatXmlSpreadsheet(range.selectedColNames(), range.selectedRowsData());
        copyTextToClipboard(xml);
        showNotice("Copied XML Spreadsheet (Excel) to clipboard");
    }

    public void handleOpenInExcel() {
        if (gridResult() == null) return;
        SelectedRangeData range = getSelectedRangeData();
        boolean isFull = range.selectedRowsData().size() <= 1 && range.selectedColNames().size() <= 1;
        List<String> cols = isFull
                ? gridResult().getColumns().stream().map(QueryResultColumn::getName).toList()
                : range.selectedColNames();
        List<List<Object>> rows = isFull
                ? gridMaster().stream().map(QueryResultRow::getValues).toList()
                : range.selectedRowsData();

        var res = DesktopGridExportService.getInstance().openInExcel(cols, rows);
        if (res.success()) {
            showNotice("Opening temporary Read-Only results file in Excel. Use 'Save As' to preserve edits.");
        } else {
            showError("Failed to open in Excel: " + res.errorMessage());
        }
    }

    public void handleSaveResultsAs() {
        if (gridResult() == null) return;
        SelectedRangeData range = getSelectedRangeData();
        boolean isFull = range.selectedRowsData().size() <= 1 && range.selectedColNames().size() <= 1;
        List<String> cols = isFull
                ? gridResult().getColumns().stream().map(QueryResultColumn::getName).toList()
                : range.selectedColNames();
        List<List<Object>> rows = isFull
                ? gridMaster().stream().map(QueryResultRow::getValues).toList()
                : range.selectedRowsData();

        Window win = grid().getScene() != null ? grid().getScene().getWindow() : null;
        var res = DesktopGridExportService.getInstance().saveResultsAs(win, cols, rows, "Query_Results.csv");
        if (res.success()) {
            showNotice("Successfully saved results to " + res.filePath());
        } else if (!res.cancelled()) {
            showError("Failed to save results: " + res.errorMessage());
        }
    }

    public void showGridAggregates() {
        SelectedRangeData range = getSelectedRangeData();
        var agg = GridDataExportUtils.calculateAggregates(range.selectedRowsData());

        String[][] rows = {
                {"Số ô (Count)", String.valueOf(agg.totalCells())},
                {"Số ô kiểu số", String.valueOf(agg.numericCount())},
                {"Tổng (Sum)", agg.getDisplaySum()},
                {"Trung bình (Avg)", agg.getDisplayAverage()},
                {"Nhỏ nhất (Min)", agg.getDisplayMin()},
                {"Lớn nhất (Max)", agg.getDisplayMax()}};
        GridPane grid = new GridPane();
        grid.getStyleClass().add("about-grid");
        grid.setHgap(12);
        javafx.scene.layout.ColumnConstraints c0 = new javafx.scene.layout.ColumnConstraints();
        c0.setHalignment(javafx.geometry.HPos.RIGHT);
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
        c1.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);
        StringBuilder copyText = new StringBuilder();
        for (int i = 0; i < rows.length; i++) {
            Label k = new Label(rows[i][0]);
            k.getStyleClass().add("about-key");
            Label v = new Label(rows[i][1]);
            v.getStyleClass().addAll("about-value", "mono");
            grid.addRow(i, k, v);
            copyText.append(rows[i][0]).append(": ").append(rows[i][1]).append('\n');
        }
        app.ui.dialogs.DialogChrome chrome = app.ui.dialogs.DialogChrome.create(
                grid().getScene() != null ? grid().getScene().getWindow() : null,
                "Thống kê vùng chọn", "mdi2s-sigma").subtitle(agg.totalCells() + " ô");
        Button copy = app.ui.dialogs.DialogChrome.secondary("Sao chép");
        copy.setOnAction(e -> {
            copyTextToClipboard(copyText.toString().trim());
            showNotice("Đã sao chép thống kê");
        });
        Button ok = app.ui.dialogs.DialogChrome.primary("OK");
        ok.setCancelButton(true);
        ok.setOnAction(e -> chrome.close());
        chrome.content(grid).footer(null, copy, ok).size(360, -1);
        chrome.showAndWait();
    }

    public void showVisualizeDialog(String mode) {
        SelectedRangeData range = getSelectedRangeData();
        Object rawVal = range.activeCellValue();
        String sVal = (rawVal == null) ? "" : rawVal.toString();

        if ("image".equalsIgnoreCase(mode)) {
            showImageDialog(rawVal, range.activeColName());
            return;
        }
        String content;
        if ("hex".equalsIgnoreCase(mode)) {
            content = GridDataExportUtils.formatHexDump(sVal);
        } else {
            content = sVal;
        }

        TextArea area = new TextArea(content);
        area.setEditable(false);
        area.setPrefSize(560, 340);
        area.getStyleClass().add("message-detail");
        area.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 12px;");
        app.ui.dialogs.DialogChrome chrome = app.ui.dialogs.DialogChrome.create(
                grid().getScene() != null ? grid().getScene().getWindow() : null,
                "Visualize as " + mode.toUpperCase(), "fth-eye")
                .subtitle(range.activeColName() != null ? range.activeColName() : "ô hiện tại");
        Button copy = app.ui.dialogs.DialogChrome.secondary("Sao chép");
        copy.setOnAction(e -> {
            copyTextToClipboard(sVal);
            showNotice("Đã sao chép nội dung ô");
        });
        Button ok = app.ui.dialogs.DialogChrome.primary("OK");
        ok.setCancelButton(true);
        ok.setOnAction(e -> chrome.close());
        chrome.content(area).footer(new javafx.scene.Node[]{copy}, ok).size(600, -1);
        chrome.showAndWait();
    }

    public void handleFindColumn() {
        if (gridResult() == null || gridResult().getColumns().isEmpty()) return;
        app.ui.dialogs.TextPromptDialog.show(grid().getScene() != null ? grid().getScene().getWindow() : null,
                "Tìm cột", "fth-search", "Tên cột (một phần tên cũng được)", "", "Tìm").ifPresent(query -> {
            String q = query.trim().toLowerCase();
            for (TableColumn<QueryResultRow, ?> col : grid().getColumns()) {
                String title = getColumnTitle(col).toLowerCase();
                if (title.contains(q)) {
                    grid().scrollToColumn(col);
                    grid().getSelectionModel().select(0, col);
                    showNotice("Jumped to column: " + getColumnTitle(col));
                    return;
                }
            }
            showError("Column not found matching: " + query);
        });
    }

    public void handlePrintGrid() {
        SelectedRangeData range = getSelectedRangeData();
        boolean isFull = range.selectedRowsData().size() <= 1 && range.selectedColNames().size() <= 1;
        List<String> cols = isFull
                ? gridResult().getColumns().stream().map(QueryResultColumn::getName).toList()
                : range.selectedColNames();
        List<List<Object>> rows = isFull
                ? gridMaster().stream().map(QueryResultRow::getValues).toList()
                : range.selectedRowsData();

        String md = GridDataExportUtils.formatMarkdownTable(cols, rows);
        copyTextToClipboard(md);
        showNotice("Prepared grid data (" + rows.size() + " rows) formatted for printing (Copied to Clipboard)");
    }

    private void setupMessagesTable() {
        msgTimeCol.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTimestamp()));
        msgDurationCol.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getDuration()));
        msgTextCol.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getMessage()));

        msgLevelCol.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getLevel()));
        msgLevelCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ConsoleMessage.Level level, boolean empty) {
                super.updateItem(level, empty);
                if (empty || level == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(level.getLabel());
                    badge.getStyleClass().addAll("badge-pill", level.getStyleClass());
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        messagesTable.setItems(messagesList);
    }

    private void setupTerminal() {
        terminalOutput.setEditable(false);
        terminalOutput.appendText("PostgreSQL 16.2 Interactive SQL Terminal (Mock)\nType 'help' or '\\?' for help. Type 'clear' to clear screen.\n\n");

        terminalInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleTerminalCommand();
            } else if (event.getCode() == KeyCode.UP) {
                navigateHistory(-1);
            } else if (event.getCode() == KeyCode.DOWN) {
                navigateHistory(1);
            }
        });
    }

    private void loadInitialData() {
        setQueryResult(QueryResultMockService.getInstance().generateDefaultQueryResult());
        messagesList.addAll(QueryResultMockService.getInstance().generateInitialMessages());
    }

    /**
     * Supports multi-grid: displays multiple result sets in a vertical SplitPane,
     * or single table view if only 1 result set.
     */
    public void setQueryResults(List<QueryResult> results) {
        this.currentResultSets = results != null ? new ArrayList<>(results) : new ArrayList<>();
        activeGrid = null;
        if (currentResultSets.isEmpty()) {
            setQueryResult(null);
            return;
        }

        subFilterStates.clear();
        if (currentResultSets.size() == 1) {
            hideResultTabs();
            if (multiGridScroll != null) {
                multiGridScroll.setVisible(false);
                multiGridScroll.setManaged(false);
            }
            resultsTable.setVisible(true);
            resultsTable.setManaged(true);
            setQueryResult(currentResultSets.get(0));
        } else {
            // Multi-grid mode
            resultsTable.setVisible(false);
            resultsTable.setManaged(false);

            if (emptyStateBox != null) {
                emptyStateBox.setVisible(false);
                emptyStateBox.setManaged(false);
            }

            boolean asTabs = app.services.SettingsService.getInstance().getCurrentSettings().getGrid() != null
                    && app.services.SettingsService.getInstance().getCurrentSettings().getGrid().isResultTabs();
            if (asTabs) {
                if (multiGridScroll != null) {
                    multiGridScroll.setVisible(false);
                    multiGridScroll.setManaged(false);
                    multiGridBox.getChildren().clear();
                }
                showResultTabs(currentResultSets);
                int total = currentResultSets.stream().mapToInt(QueryResult::getRowCount).sum();
                long maxMs = currentResultSets.stream().mapToLong(QueryResult::getExecutionDurationMs).max().orElse(0);
                setResultsTableCount(currentResultSets.size());
                updateFooterStats(total, maxMs, "multi-result");
            } else if (multiGridScroll != null) {
                hideResultTabs();
                multiGridScroll.setVisible(true);
                multiGridScroll.setManaged(true);
                multiGridBox.getChildren().clear();
                // D4 (SSMS / LLTool): the grids fill the panel, splitters between them, minimize docks at the bottom
                multiGridScroll.setFitToWidth(true);
                multiGridScroll.setFitToHeight(true);
                multiGridScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                multiGridScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                app.ui.results.StackedGridsPane stack = new app.ui.results.StackedGridsPane();
                VBox.setVgrow(stack, javafx.scene.layout.Priority.ALWAYS);
                multiGridBox.getChildren().add(stack);

                int totalAllRows = 0;
                long maxDuration = 0;

                for (int i = 0; i < currentResultSets.size(); i++) {
                    QueryResult res = currentResultSets.get(i);
                    totalAllRows += res.getRowCount();
                    if (res.getExecutionDurationMs() > maxDuration) {
                        maxDuration = res.getExecutionDurationMs();
                    }

                    VBox paneBox = createSubGridPane(res, i + 1);
                    var entry = stack.add(paneBox, (javafx.scene.Node) paneBox.getProperties().get(SUBPANE_BODY_KEY));
                    wireSubPaneMinimize(paneBox, stack, entry);
                }

                setResultsTableCount(currentResultSets.size());

                updateFooterStats(totalAllRows, maxDuration, "multi-result");
            }
        }
    }

    /** Max visible rows of one stacked grid before it scrolls on its own (LLTool: ~12). */
    private static final int STACKED_VISIBLE_ROWS = 12;

    /**
     * One stacked result set (LLTool "Bảng #1 · 12 dòng"): header with chevron to collapse,
     * row count, duration and the start of the statement; the grid below sized to its rows.
     */
    private VBox createSubGridPane(QueryResult res, int tableNumber) {
        VBox box = new VBox();
        box.getStyleClass().add("grid-subpane");
        box.setFillWidth(true);

        HBox header = new HBox(8);
        header.getStyleClass().add("grid-subpane-header");
        header.setAlignment(Pos.CENTER_LEFT);

        FontIcon chevron = new FontIcon("fth-chevron-down");
        chevron.getStyleClass().add("grid-subpane-chevron");
        FontIcon tableIcon = new FontIcon("fth-grid");
        tableIcon.getStyleClass().add("grid-subpane-icon");
        Label title = new Label("Bảng " + tableNumber);
        title.getStyleClass().add("grid-subpane-title");
        // source: only the table name for now (temp tables / table variables in routines: later, see plan)
        String source = res.getSourceTable() != null && !res.getSourceTable().isBlank()
                ? res.getSourceTable()
                : sourceTableName(res.getQuery());
        Label src = new Label(source);
        src.getStyleClass().add("grid-subpane-source");
        src.setMinWidth(0);
        src.setVisible(!source.isEmpty());
        src.setManaged(!source.isEmpty());
        if (res.getQuery() != null) src.setTooltip(new Tooltip(res.getQuery()));
        Label rows = new Label(String.format("%,d dòng", res.getRowCount()));
        rows.getStyleClass().add("grid-subpane-count");
        Label timeBadge = new Label(res.getExecutionDurationMs() + " ms");
        timeBadge.getStyleClass().add("grid-stat-badge");
        javafx.scene.layout.Region headerGap = new javafx.scene.layout.Region();
        HBox.setHgrow(headerGap, javafx.scene.layout.Priority.ALWAYS);
        header.getChildren().addAll(chevron, tableIcon, title, src, rows, timeBadge, headerGap);

        TableView<QueryResultRow> subTable = new TableView<>();
        subTable.getStyleClass().addAll("results-table", "stacked-grid");
        subTable.getSelectionModel().setCellSelectionEnabled(true);
        subTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        subTable.getProperties().put(GRID_INDEX_KEY, tableNumber - 1);
        populateTableViewWithResult(subTable, res);
        var subState = attachRows(subTable, res);
        installGrid(subTable);
        bindCountBadge(rows, subState);
        // SSMS-like: each grid is as tall as its rows but never taller than the panel, so every
        // grid has its own scrollbar and the outer scroll moves between grids
        // D4: rows have a fixed height (populateTableViewWithResult); the "#" numbers are drawn by
        // ResultGridRow, so horizontal virtualization no longer hides them (T11).
        // D4: the height comes from StackedGridsPane (default share of the panel, splitters, minimize)
        VBox.setVgrow(subTable, javafx.scene.layout.Priority.ALWAYS);
        subTable.setMinHeight(0);

        Button minBtn = new Button();
        minBtn.getStyleClass().add("grid-subpane-min-btn");
        minBtn.setFocusTraversable(false);
        minBtn.setGraphic(new FontIcon("mdi2w-window-minimize"));
        minBtn.setTooltip(new Tooltip("Thu nhỏ / Ẩn bảng này xuống dưới"));
        header.getChildren().add(minBtn);
        box.getProperties().put(SUBPANE_BODY_KEY, subTable);
        box.getProperties().put(SUBPANE_MIN_BTN_KEY, minBtn);
        box.getProperties().put(SUBPANE_CHEVRON_KEY, chevron);
        box.getProperties().put(SUBPANE_HEADER_KEY, header);

        box.getChildren().addAll(header, subTable);
        return box;
    }

    private static final String SUBPANE_BODY_KEY = "sqlide.subpane.body";
    private static final String SUBPANE_MIN_BTN_KEY = "sqlide.subpane.minBtn";
    private static final String SUBPANE_CHEVRON_KEY = "sqlide.subpane.chevron";
    private static final String SUBPANE_HEADER_KEY = "sqlide.subpane.header";

    /** Header click or its minimize button: the grid docks at the bottom as a header, or comes back (LLTool). */
    private void wireSubPaneMinimize(VBox box, app.ui.results.StackedGridsPane stack, app.ui.results.StackedGridsPane.Entry entry) {
        Button minBtn = (Button) box.getProperties().get(SUBPANE_MIN_BTN_KEY);
        FontIcon chevron = (FontIcon) box.getProperties().get(SUBPANE_CHEVRON_KEY);
        HBox header = (HBox) box.getProperties().get(SUBPANE_HEADER_KEY);
        Runnable toggle = () -> {
            stack.toggleMinimized(entry);
            boolean min = entry.isMinimized();
            if (chevron != null) chevron.setIconLiteral(min ? "fth-chevron-right" : "fth-chevron-down");
            if (minBtn != null) {
                minBtn.setGraphic(new FontIcon(min ? "mdi2w-window-maximize" : "mdi2w-window-minimize"));
                minBtn.getTooltip().setText(min ? "Mở rộng / Hiện lại bảng này" : "Thu nhỏ / Ẩn bảng này xuống dưới");
            }
            box.getStyleClass().remove("collapsed");
            if (min) box.getStyleClass().add("collapsed");
        };
        if (minBtn != null) {
            minBtn.setOnAction(e -> toggle.run());
            minBtn.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume); // not the header too
        }
        if (header != null) header.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) toggle.run();
        });
    }

    /** "7 / 12 dòng" when filtered; tooltip lists every filter and its row count (2026-11-05). */
    private void bindCountBadge(Label badge, app.ui.results.GridFilterState state) {
        Tooltip tip = new Tooltip();
        tip.setShowDelay(javafx.util.Duration.millis(250));
        tip.setShowDuration(javafx.util.Duration.seconds(30));
        Runnable refresh = () -> {
            boolean on = state.hasFilters();
            badge.setText(on ? String.format("%,d / %,d dòng", state.shownCount(), state.totalCount())
                    : String.format("%,d dòng", state.totalCount()));
            badge.getStyleClass().remove("filtered");
            if (on) badge.getStyleClass().add("filtered");
            tip.setText(on ? state.describe() : "Chưa lọc · " + String.format("%,d", state.totalCount()) + " dòng");
        };
        state.setOnChanged(refresh);
        badge.setTooltip(tip);
        refresh.run();
    }

    /** Table name after the first FROM (schema / quotes removed); empty when not found. */
    static String sourceTableName(String sql) {
        if (sql == null) return "";
        String noComments = sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\n]*", " ");
        var m = java.util.regex.Pattern.compile(
                "(?is)\\bFROM\\s+((?:\\[[^\\]]+\\]|\"[^\"]+\"|`[^`]+`|[\\w#@$]+)(?:\\s*\\.\\s*(?:\\[[^\\]]+\\]|\"[^\"]+\"|`[^`]+`|[\\w#@$]+))*)")
                .matcher(noComments);
        if (!m.find()) return "";
        String[] parts = m.group(1).split("\\s*\\.\\s*");
        String last = parts[parts.length - 1];
        return last.replaceAll("^[\\[\"`]|[\\]\"`]$", "");
    }

    /** Own rows + filter state for a stacked / tabbed grid. */
    private app.ui.results.GridFilterState attachRows(TableView<QueryResultRow> table, QueryResult res) {
        ObservableList<QueryResultRow> rows = FXCollections.observableArrayList(res.getRows());
        FilteredList<QueryResultRow> filtered = new FilteredList<>(rows, p -> true);
        var state = new app.ui.results.GridFilterState(rows, filtered);
        String q = resultsFilterField != null ? resultsFilterField.getText() : null;
        if (q != null && !q.isBlank()) state.setGlobalQuery(q);
        app.ui.results.GridFilterState.attach(table, state);
        state.setColumnNames(res.getColumns().stream().map(c -> c.getName()).toList(),
                app.services.SettingsService.getInstance().getCurrentSettings().getNullDisplayString());
        subFilterStates.add(state);
        table.setItems(filtered);
        return state;
    }

    /** Tabs mode: one tab per result set; the grid is built only when its tab is first opened. */
    private void showResultTabs(List<QueryResult> sets) {
        if (multiGridTabs == null) {
            multiGridTabs = new TabPane();
            multiGridTabs.getStyleClass().add("result-set-tabs");
            multiGridTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            if (resultsStackPane != null) resultsStackPane.getChildren().add(0, multiGridTabs);
        }
        multiGridTabs.getTabs().clear();
        for (int i = 0; i < sets.size(); i++) {
            QueryResult res = sets.get(i);
            String titlePrefix = res.getSourceTable() != null && !res.getSourceTable().isBlank()
                    ? res.getSourceTable()
                    : String.format("Bảng %d", i + 1);
            Tab tab = new Tab(String.format("%s (%,d)", titlePrefix, res.getRowCount()));
            FontIcon ic = new FontIcon("fth-grid");
            tab.setGraphic(ic);
            if (res.getQuery() != null) tab.setTooltip(new Tooltip(res.getQuery()));
            Runnable build = () -> {
                if (tab.getContent() != null) return;
                TableView<QueryResultRow> t = new TableView<>();
                t.getStyleClass().addAll("results-table", "tabbed-grid");
                t.getSelectionModel().setCellSelectionEnabled(true);
                t.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                t.getProperties().put(GRID_INDEX_KEY, sets.indexOf(res));
                populateTableViewWithResult(t, res);
                var st = attachRows(t, res);
                installGrid(t);
                int n = tab.getTabPane() != null ? tab.getTabPane().getTabs().indexOf(tab) + 1 : 0;
                String effectivePrefix = res.getSourceTable() != null && !res.getSourceTable().isBlank()
                        ? res.getSourceTable()
                        : String.format("Bảng %d", n);
                Tooltip tip = new Tooltip();
                tip.setShowDelay(javafx.util.Duration.millis(250));
                tip.setShowDuration(javafx.util.Duration.seconds(30));
                st.setOnChanged(() -> {
                    boolean on = st.hasFilters();
                    tab.setText(on ? String.format("%s (%,d / %,d)", effectivePrefix, st.shownCount(), st.totalCount())
                            : String.format("%s (%,d)", effectivePrefix, st.totalCount()));
                    tip.setText(on ? st.describe() : (res.getQuery() != null ? res.getQuery() : ""));
                });
                tip.setText(res.getQuery() != null ? res.getQuery() : "");
                tab.setTooltip(tip);
                tab.setContent(t);
            };
            tab.selectedProperty().addListener((o, a, b) -> {
                if (b) build.run();
            });
            multiGridTabs.getTabs().add(tab);
            if (i == 0) build.run();
        }
        multiGridTabs.setVisible(true);
        multiGridTabs.setManaged(true);
        multiGridTabs.getSelectionModel().selectFirst();
    }

    private void hideResultTabs() {
        if (multiGridTabs != null) {
            multiGridTabs.getTabs().clear();
            multiGridTabs.setVisible(false);
            multiGridTabs.setManaged(false);
        }
    }

    /** Connection / database / schema of the run, for the footer ("Local · B10_PhucLoi · dbo"). */
    public void setExecutionContext(String connection, String database, String schema) {
        StringBuilder sb = new StringBuilder();
        if (connection != null && !connection.isBlank()) sb.append(connection);
        if (database != null && !database.isBlank()) sb.append(sb.length() > 0 ? " · " : "").append(database);
        if (schema != null && !schema.isBlank()) sb.append(sb.length() > 0 ? " · " : "").append(schema);
        footerContext = sb.length() > 0 ? sb.toString() : null;
        if (footerConnLabel != null && footerContext != null) footerConnLabel.setText(footerContext);
    }

    public void setQueryResult(QueryResult result) {
        // a single result always uses the main grid
        hideResultTabs();
        if (multiGridScroll != null) {
            multiGridScroll.setVisible(false);
            multiGridScroll.setManaged(false);
            multiGridBox.getChildren().clear();
        }
        subFilterStates.clear();
        activeGrid = null;
        resultsTable.setVisible(true);
        resultsTable.setManaged(true);
        this.currentResult = result;
        masterRows.clear();
        resultsTable.getColumns().clear();
        pinnedColumns.clear();
        if (mainFilterState != null) mainFilterState.clearAll();

        // D4 (LLTool parity): a result WITH COLUMNS is shown even with 0 rows, so an empty table still shows its
        // structure; the empty state is only for "no result set at all"
        if (result == null || result.getColumns() == null || result.getColumns().isEmpty()) {
            if (emptyStateBox != null) {
                emptyStateBox.setVisible(true);
                emptyStateBox.setManaged(true);
            }
            if (resultsStatsLabel != null) {
                resultsStatsLabel.setText("No query executed yet");
            }
            setResultsTableCount(0);
            updateFooterStats(0, 0, null);
            return;
        }

        if (emptyStateBox != null) {
            emptyStateBox.setVisible(false);
            emptyStateBox.setManaged(false);
        }

        populateTableViewWithResult(resultsTable, result);
        if (mainFilterState != null) {
            mainFilterState.setColumnNames(result.getColumns().stream().map(c -> c.getName()).toList(),
                    app.services.SettingsService.getInstance().getCurrentSettings().getNullDisplayString());
        }

        masterRows.addAll(result.getRows());
        updateStatsLabel();

        if (resultsTab != null) {
            setResultsTableCount(1);
        }

        updateFooterStats(result.getRowCount(), result.getExecutionDurationMs(), null);
    }

    // ------------------------------------------------------------------ D4.3: results per editor tab

    /**
     * What one editor tab shows in the results panel (LLTool query.js restoreTabResults / tabGridContainers).
     * The stacked grids are kept as live nodes, so going back to a tab keeps their scroll, sizes and filters;
     * a single result is drawn again from its data.
     */
    private record TabResultsState(List<QueryResult> sets, javafx.scene.Node stackedNode, MainGrid mainGrid,
                                   List<ConsoleMessage> messages, String sqlMessages, int selectedView,
                                   PhysicalExecutionPlan plan, String filterText) {
    }

    /**
     * The single-result grid with its rows and filters. Each editor tab keeps its own: switching tabs puts
     * the tab's live grid back instead of building it again (rebuilding 30+ columns and thousands of rows
     * on every switch made tab changes stutter).
     */
    private record MainGrid(TableView<QueryResultRow> table, ObservableList<QueryResultRow> master,
                            FilteredList<QueryResultRow> filtered, app.ui.results.GridFilterState state,
                            QueryResult result, Set<TableColumn<QueryResultRow, ?>> pinned) {
    }

    private MainGrid currentMainGrid() {
        return new MainGrid(resultsTable, masterRows, filteredRows, mainFilterState, currentResult, new HashSet<>(pinnedColumns));
    }

    /** A new, empty single grid configured like the one from results.fxml. */
    private MainGrid newMainGrid() {
        TableView<QueryResultRow> t = new TableView<>();
        t.getStyleClass().setAll(resultsTable.getStyleClass());
        t.setId(resultsTable.getId());
        t.setVisible(resultsTable.isVisible());
        t.setManaged(resultsTable.isManaged());
        t.getSelectionModel().setCellSelectionEnabled(true);
        t.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ObservableList<QueryResultRow> master = FXCollections.observableArrayList();
        FilteredList<QueryResultRow> filtered = new FilteredList<>(master, p -> true);
        t.setItems(filtered);
        var state = new app.ui.results.GridFilterState(master, filtered);
        state.setOnChanged(this::updateStatsLabel);
        app.ui.results.GridFilterState.attach(t, state);
        installGrid(t);
        return new MainGrid(t, master, filtered, state, null, new HashSet<>());
    }

    /** Puts {@code g} where the single grid is and makes it the one every grid action uses. */
    private void swapMainGrid(MainGrid g) {
        if (g.table() != resultsTable && resultsTable.getParent() instanceof javafx.scene.layout.Pane parent) {
            int i = parent.getChildren().indexOf(resultsTable);
            if (parent instanceof StackPane) StackPane.setAlignment(g.table(), StackPane.getAlignment(resultsTable));
            if (i >= 0) parent.getChildren().set(i, g.table());
        }
        resultsTable = g.table();
        masterRows = g.master();
        filteredRows = g.filtered();
        mainFilterState = g.state();
        currentResult = g.result();
        pinnedColumns.clear();
        pinnedColumns.addAll(g.pinned());
        activeGrid = null;
    }

    /** The grid on screen belongs to a saved tab: give the next tab a fresh one so the saved one stays intact. */
    private void detachCachedMainGrid() {
        for (TabResultsState st : tabStates.values()) {
            if (st.mainGrid() != null && st.mainGrid().table() == resultsTable) {
                swapMainGrid(newMainGrid());
                return;
            }
        }
    }

    private final Map<String, TabResultsState> tabStates = new HashMap<>();

    /** Remembers what the panel shows for {@code tabId} (called when another tab is selected). */
    public void saveTabState(String tabId) {
        if (tabId == null) return;
        javafx.scene.Node stacked = null;
        if (multiGridScroll != null && multiGridScroll.isVisible() && multiGridBox != null && !multiGridBox.getChildren().isEmpty()) {
            stacked = multiGridBox.getChildren().get(0);
        }
        boolean singleShown = stacked == null && currentResult != null && resultsTable.isVisible()
                && (multiGridTabs == null || !multiGridTabs.isVisible());
        tabStates.put(tabId, new TabResultsState(
                new ArrayList<>(currentResultSets),
                stacked,
                singleShown ? currentMainGrid() : null,
                new ArrayList<>(messagesList),
                sqlMessagesArea != null ? sqlMessagesArea.getText() : "",
                resultsTabPane != null ? resultsTabPane.getSelectionModel().getSelectedIndex() : 0,
                currentExecutionPlan,
                resultsFilterField != null ? resultsFilterField.getText() : ""));
    }

    /** Shows what {@code tabId} showed last time; a tab that never ran gets an empty panel. */
    public void restoreTabState(String tabId) {
        TabResultsState st = tabId != null ? tabStates.get(tabId) : null;
        resetStreaming();
        showLoading(false);
        detachCachedMainGrid();
        if (resultsFilterField != null) resultsFilterField.setText(st != null && st.filterText() != null ? st.filterText() : "");
        if (st == null) {
            setQueryResults(List.of());
            messagesList.clear();
            if (timelineTab != null) timelineTab.setText("Timeline (0)");
            if (sqlMessagesArea != null) sqlMessagesArea.clear();
            clearPlanView();
            setResultsTableCount(0);
            return;
        }
        if (st.mainGrid() != null) {
            // the tab's own live grid: no rebuild
            hideResultTabs();
            if (multiGridScroll != null) {
                multiGridScroll.setVisible(false);
                multiGridScroll.setManaged(false);
                multiGridBox.getChildren().clear();
            }
            subFilterStates.clear();
            swapMainGrid(st.mainGrid());
            resultsTable.setVisible(true);
            resultsTable.setManaged(true);
            currentResultSets = new ArrayList<>(st.sets());
            boolean has = currentResult != null;
            if (emptyStateBox != null) {
                emptyStateBox.setVisible(!has);
                emptyStateBox.setManaged(!has);
            }
            if (has) {
                updateStatsLabel();
                setResultsTableCount(Math.max(1, currentResultSets.size()));
                updateFooterStats(currentResult.getRowCount(), currentResult.getExecutionDurationMs(), null);
            }
        } else if (st.stackedNode() != null && multiGridScroll != null && multiGridBox != null) {
            // the live stacked grids of that tab come back as they were
            setQueryResult(null);
            hideResultTabs();
            resultsTable.setVisible(false);
            resultsTable.setManaged(false);
            if (emptyStateBox != null) {
                emptyStateBox.setVisible(false);
                emptyStateBox.setManaged(false);
            }
            currentResultSets = new ArrayList<>(st.sets());
            multiGridBox.getChildren().setAll(st.stackedNode());
            for (javafx.scene.Node g : st.stackedNode().lookupAll(".results-table")) { // quick filter reaches them again
                if (g instanceof TableView<?> tv) {
                    var fs = app.ui.results.GridFilterState.of(tv);
                    if (fs != null) subFilterStates.add(fs);
                }
            }
            multiGridScroll.setVisible(true);
            multiGridScroll.setManaged(true);
            int total = st.sets().stream().mapToInt(QueryResult::getRowCount).sum();
            long maxMs = st.sets().stream().mapToLong(QueryResult::getExecutionDurationMs).max().orElse(0);
            setResultsTableCount(st.sets().size());
            updateFooterStats(total, maxMs, "multi-result");
        } else {
            setQueryResults(st.sets());
            if (st.sets().isEmpty()) setResultsTableCount(0);
        }
        messagesList.setAll(st.messages());
        if (timelineTab != null) timelineTab.setText("Timeline (" + messagesList.size() + ")");
        if (sqlMessagesArea != null) sqlMessagesArea.setText(st.sqlMessages());
        if (st.plan() != null) setExecutionPlan(st.plan()); else clearPlanView();
        if (resultsTabPane != null && st.selectedView() >= 0 && st.selectedView() < resultsTabPane.getTabs().size()) {
            resultsTabPane.getSelectionModel().select(st.selectedView());
        }
    }

    /** The tab was closed: its results are dropped. */
    public void dropTabState(String tabId) {
        if (tabId != null) tabStates.remove(tabId);
    }

    /** Execution plan view back to "no plan yet". */
    private void clearPlanView() {
        currentExecutionPlan = null;
        if (planEmptyStateBox != null) {
            planEmptyStateBox.setVisible(true);
            planEmptyStateBox.setManaged(true);
        }
        if (planVisualSplitPane != null) {
            planVisualSplitPane.setVisible(false);
            planVisualSplitPane.setManaged(false);
        }
        if (planRawBox != null) {
            planRawBox.setVisible(false);
            planRawBox.setManaged(false);
        }
        if (planLoadingBox != null) {
            planLoadingBox.setVisible(false);
            planLoadingBox.setManaged(false);
        }
    }

    // ------------------------------------------------------------------ D4: streaming (rows arrive in batches)

    /** The first result set of the running script is being filled while rows arrive. */
    private boolean streaming;
    /** Live rows of the streamed first result (also the list behind currentResult). */
    private List<QueryResultRow> streamedRows;
    private long streamStartNanos;

    /** A new run starts: nothing is streamed yet. */
    public void resetStreaming() {
        streaming = false;
        streamedRows = null;
    }

    public boolean isStreaming() {
        return streaming;
    }

    /**
     * The first result set of the run starts: show its columns at once (LLTool shows the grid before the
     * rows). Later result sets are drawn when the script ends.
     */
    public void streamResultStarted(int index, String sql, List<QueryResultColumn> columns) {
        if (index != 0) return;
        streamedRows = new ArrayList<>();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        setQueryResults(List.of(new QueryResult(sql, columns, streamedRows, 0, ts)));
        streaming = true;
        streamStartNanos = System.nanoTime();
        showLoading(false); // the grid itself shows the progress from here
        selectResultsTab();
    }

    /** Next rows of the first result set: appended to the grid, the counters follow. */
    public void streamRows(int index, List<QueryResultRow> rows) {
        if (index != 0 || !streaming || streamedRows == null) return;
        streamedRows.addAll(rows);
        masterRows.addAll(rows);
        updateStatsLabel();
        setResultsTableCount(1);
        updateFooterStats(streamedRows.size(), (System.nanoTime() - streamStartNanos) / 1_000_000, null);
    }

    /**
     * The script ended. One result set: the streamed grid stays as it is (no redraw, scroll position kept),
     * only the final figures are shown. Several: they are drawn together now.
     */
    public void finishStreaming(List<QueryResult> all) {
        boolean wasStreaming = streaming;
        resetStreaming();
        if (!wasStreaming || all == null || all.size() != 1) {
            setQueryResults(all);
            return;
        }
        QueryResult fin = all.get(0);
        currentResultSets = new ArrayList<>(all);
        currentResult = fin;
        resultsTable.getProperties().put(GRID_RESULT_KEY, fin);
        if (masterRows.size() != fin.getRowCount()) { // a batch was lost or the run was cut: show what was read
            masterRows.setAll(fin.getRows());
        }
        updateStatsLabel();
        setResultsTableCount(1);
        updateFooterStats(fin.getRowCount(), fin.getExecutionDurationMs(), null);
    }

    // ------------------------------------------------------------------ D4: column widths

    private static final String GRID_INDEX_KEY = "sqlide.gridIndex";
    private static final String WIDTHS_READY_KEY = "sqlide.widthsReady";
    /** Widths the user dragged: tab id → ("gridIndex_column" or "column") → px (LLTool columnWidths[tabId]). */
    private static final Map<String, Map<String, Double>> COLUMN_WIDTHS = new HashMap<>();
    private String widthScope = "";

    /** The tab whose results are shown next (column widths are remembered per tab). */
    public void setColumnWidthScope(String tabId) {
        widthScope = tabId != null ? tabId : "";
    }

    private Double savedColumnWidth(int gridIndex, String colName) {
        Map<String, Double> m = COLUMN_WIDTHS.get(widthScope);
        if (m == null) return null;
        Double w = m.get(gridIndex + "_" + colName);
        return w != null ? w : m.get(colName);
    }

    private void rememberColumnWidth(int gridIndex, String colName, double w) {
        Map<String, Double> m = COLUMN_WIDTHS.computeIfAbsent(widthScope, k -> new HashMap<>());
        m.put(gridIndex + "_" + colName, w);
        m.put(colName, w);
    }

    /** Rows measured by "fit to content": enough for what is on screen and a little more, never a full scan. */
    private static final int AUTOFIT_ROWS = 1000;

    /**
     * Double-click on a column's right edge in the header: the column fits its header and the first
     * {@value #AUTOFIT_ROWS} rows (JavaFX's own double-click measures every row, slow on large results).
     */
    private void installAutoFit(TableView<QueryResultRow> table) {
        table.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2) return;
            if (!(e.getTarget() instanceof javafx.scene.shape.Rectangle r) || !insideHeader(r)) return;
            TableColumn<QueryResultRow, ?> col = columnAtRightEdge(table, e.getSceneX(), e.getSceneY());
            if (col == null || "#".equals(col.getText())) return;
            e.consume();
            autoFitColumn(table, col);
        });
    }

    private static boolean insideHeader(javafx.scene.Node n) {
        for (javafx.scene.Node p = n; p != null; p = p.getParent()) {
            if (p.getStyleClass().contains("column-header-background")) return true;
            if (p instanceof TableView<?>) return false;
        }
        return false;
    }

    private static TableColumn<QueryResultRow, ?> columnAtRightEdge(TableView<QueryResultRow> table, double sceneX, double sceneY) {
        javafx.scene.Node root = table.lookup(".nested-column-header");
        if (root == null) return null;
        double x = root.sceneToLocal(sceneX, sceneY).getX();
        double edge = 0;
        TableColumn<QueryResultRow, ?> best = null;
        double bestDist = 8;
        for (TableColumn<QueryResultRow, ?> c : table.getVisibleLeafColumns()) {
            edge += c.getWidth();
            double d = Math.abs(edge - x);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    private void autoFitColumn(TableView<QueryResultRow> table, TableColumn<QueryResultRow, ?> col) {
        javafx.scene.text.Font font = null;
        for (javafx.scene.Node n : table.lookupAll(".table-cell")) {
            if (n instanceof TableCell<?, ?> c && c.getTableColumn() == col && !c.isEmpty()) {
                font = c.getFont();
                break;
            }
        }
        if (font == null) font = javafx.scene.text.Font.getDefault();
        javafx.scene.text.Text probe = new javafx.scene.text.Text();
        probe.setFont(font);
        String nullText = app.services.SettingsService.getInstance().getCurrentSettings().getNullDisplayString();
        double max = 0;
        int n = Math.min(table.getItems().size(), AUTOFIT_ROWS);
        for (int i = 0; i < n; i++) {
            Object v = col.getCellData(i);
            String t = v == null ? (nullText != null ? nullText : "NULL") : v.toString();
            if (t.length() > 300) t = t.substring(0, 300);
            probe.setText(t);
            max = Math.max(max, probe.getLayoutBounds().getWidth());
        }
        // header: name (bold) + type + filter button
        probe.setFont(javafx.scene.text.Font.font(font.getFamily(), javafx.scene.text.FontWeight.BOLD, font.getSize()));
        probe.setText(col.getText() != null ? col.getText() : "");
        double header = probe.getLayoutBounds().getWidth() + 44;
        double w = Math.max(col.getMinWidth(), Math.min(800, Math.max(max + 18, header)));
        col.setPrefWidth(w);
    }

    private static final javafx.css.PseudoClass PC_NULL = javafx.css.PseudoClass.getPseudoClass("null-value");
    private static final javafx.css.PseudoClass PC_NUMBER = javafx.css.PseudoClass.getPseudoClass("number");
    private static final javafx.css.PseudoClass PC_TRUE = javafx.css.PseudoClass.getPseudoClass("bool-true");
    private static final javafx.css.PseudoClass PC_FALSE = javafx.css.PseudoClass.getPseudoClass("bool-false");

    private void populateTableViewWithResult(TableView<QueryResultRow> table, QueryResult result) {
        table.getColumns().clear();
        // D4: an empty result keeps its column headers (and can be scrolled sideways); the body says "0 dòng"
        table.setPlaceholder(new app.ui.results.EmptyGridPlaceholder(table, "0 dòng"));
        table.getProperties().put(GRID_RESULT_KEY, result);
        table.getProperties().remove(GRID_PINNED_KEY);

        app.models.IdeSettings curSettings = app.services.SettingsService.getInstance().getCurrentSettings();
        final int gridIndex = table.getProperties().get(GRID_INDEX_KEY) instanceof Integer gi ? gi : 0;
        table.getProperties().remove(WIDTHS_READY_KEY);
        Platform.runLater(() -> table.getProperties().put(WIDTHS_READY_KEY, Boolean.TRUE));
        String gridFont = curSettings.getGrid() != null ? curSettings.getGrid().getFontFamily() : "Inter";
        int gridFontSize = curSettings.getGrid() != null ? curSettings.getGrid().getFontSize() : 12;
        table.setStyle(String.format(java.util.Locale.ROOT, "-fx-font-family: '%s'; -fx-font-size: %dpx;", gridFont, gridFontSize));

        // D4 (lazy grid): fixed row height → JavaFX lays out only the rows AND the columns in view
        // (279 columns no longer means 279 cells per row) and scrolling needs no per-row measuring.
        table.setFixedCellSize(Math.max(24, gridFontSize * 2));

        // 1. Column 0: '#'. The column only reserves the space and holds the frozen header; the numbers are
        //    drawn by ResultGridRow so they survive horizontal virtualization (T11).
        TableColumn<QueryResultRow, Integer> rowNumCol = new TableColumn<>("#");
        // fixed while scrolling horizontally, ~50px by default, user can drag it wider (2026-11-05)
        rowNumCol.setPrefWidth(30);
        rowNumCol.setMinWidth(24);
        rowNumCol.setResizable(true);
        rowNumCol.setReorderable(false);
        rowNumCol.setSortable(false);
        rowNumCol.getStyleClass().add("row-number-column");
        rowNumCol.setVisible(curSettings.isShowRowNumbers());
        rowNumCol.setCellFactory(column -> new TableCell<>()); // empty: the row draws the number

        Label rowNumHeaderLabel = new Label("#");
        rowNumHeaderLabel.setStyle("-fx-cursor: hand; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");
        rowNumHeaderLabel.setOnMouseClicked(event -> {
            table.getSelectionModel().selectAll();
            event.consume();
        });
        rowNumCol.setGraphic(rowNumHeaderLabel);

        // Clicking a row number selects the whole row (Ctrl/Cmd adds it to the selection)
        table.setRowFactory(tv -> new app.ui.results.ResultGridRow(table, rowNumCol, (rIdx, event) -> {
            if (!(event.isControlDown() || event.isShortcutDown())) table.getSelectionModel().clearSelection();
            var cols = table.getColumns();
            if (!cols.isEmpty()) table.getSelectionModel().selectRange(rIdx, cols.get(0), rIdx, cols.get(cols.size() - 1)); // one batched change
        }));

        List<TableColumn<QueryResultRow, ?>> newColumns = new ArrayList<>();
        newColumns.add(rowNumCol);

        // 2. Dynamic Columns from query result
        for (int i = 0; i < result.getColumns().size(); i++) {
            final int colIndex = i;
            QueryResultColumn colMeta = result.getColumns().get(i);

            TableColumn<QueryResultRow, Object> col = new TableColumn<>(colMeta.getHeaderLabel());
            // D4 (LLTool query.js): width from the column NAME, never from the data (no scan of the rows);
            // a width the user dragged is remembered per tab + grid + column and used again
            String colName = colMeta.getName() != null ? colMeta.getName() : "";
            double minW = Math.max(38, Math.min(160, colName.length() * 8 + 36));
            Double saved = savedColumnWidth(gridIndex, colName);
            col.setMinWidth(minW);
            col.setPrefWidth(saved != null ? Math.max(minW, saved) : Math.max(minW, 120));
            col.widthProperty().addListener((o, was, now) -> {
                if (Boolean.TRUE.equals(table.getProperties().get(WIDTHS_READY_KEY))) {
                    rememberColumnWidth(gridIndex, colName, now.doubleValue());
                }
            });
            col.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getValue(colIndex)));

            // Header (user 2026-11-06): the name uses the whole width; the data type (80 % size, italic, muted) and
            // the filter button sit in a tray over the right edge, shown on hover over a gradient that fades the
            // end of the name. An active filter keeps its button (and the gradient) visible.
            Label titleLabel = new Label(colMeta.getName());
            titleLabel.getStyleClass().add("col-header-name");
            titleLabel.setMinWidth(0);
            titleLabel.setTooltip(new Tooltip(colMeta.getHeaderLabel()));
            Label typeLabel = new Label(colMeta.getDataType() != null ? colMeta.getDataType().toLowerCase(java.util.Locale.ROOT) : "");
            typeLabel.getStyleClass().add("col-header-type");
            typeLabel.setMinWidth(0);
            boolean hasType = colMeta.getDataType() != null && !colMeta.getDataType().isBlank();
            typeLabel.setVisible(hasType);
            typeLabel.setManaged(hasType);

            // Clicking the title selects the entire column
            titleLabel.setOnMouseClicked(event -> {
                if (!(event.isControlDown() || event.isShortcutDown())) table.getSelectionModel().clearSelection();
                int last = table.getItems().size() - 1;
                if (last >= 0) table.getSelectionModel().selectRange(0, col, last, col); // one batched change
                event.consume();
            });

            HBox headerTray = new HBox(4);
            Button filterBtn = new Button();
            filterBtn.getStyleClass().add("col-header-filter-btn");
            FontIcon filterIcon = new FontIcon("fth-filter");
            filterBtn.setGraphic(filterIcon);
            filterBtn.setFocusTraversable(false);
            filterBtn.setTooltip(new Tooltip("Lọc [" + colMeta.getName() + "]"));
            filterBtn.setOnAction(event -> {
                var state = app.ui.results.GridFilterState.of(table);
                if (state == null) return;
                String nullText = app.services.SettingsService.getInstance().getCurrentSettings().getNullDisplayString();
                app.ui.results.ColumnFilterPopup.show(filterBtn, state, colIndex, colMeta.getName(), nullText, () -> {
                    boolean on = state.isFiltered(colIndex);
                    filterBtn.getStyleClass().remove("active-filter");
                    headerTray.getStyleClass().remove("has-filter");
                    col.getStyleClass().remove("column-header-filtered");
                    if (on) {
                        filterBtn.getStyleClass().add("active-filter");
                        headerTray.getStyleClass().add("has-filter");
                        col.getStyleClass().add("column-header-filtered");
                    }
                    updateStatsLabel();
                });
            });

            headerTray.getChildren().addAll(typeLabel, filterBtn);
            headerTray.setAlignment(Pos.CENTER_RIGHT);
            headerTray.getStyleClass().add("col-header-tray");
            headerTray.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            headerTray.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            headerTray.setMaxHeight(Double.MAX_VALUE);
            javafx.scene.layout.StackPane headerBox = new javafx.scene.layout.StackPane(titleLabel, headerTray);
            javafx.scene.layout.StackPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
            javafx.scene.layout.StackPane.setAlignment(headerTray, Pos.CENTER_RIGHT);
            headerBox.getStyleClass().add("col-header-box");
            headerBox.setMinWidth(0);
            headerBox.prefWidthProperty().bind(col.widthProperty().subtract(12));
            headerBox.maxWidthProperty().bind(col.widthProperty().subtract(12));
            col.setGraphic(headerBox);

            // Custom Cell Factory: Handles NULL values, booleans, and numbers
            col.setCellFactory(column -> {
                TableCell<QueryResultRow, Object> cell = new TableCell<>() {
                    @Override
                    protected void updateItem(Object item, boolean empty) {
                        super.updateItem(item, empty);
                        // D4: pseudo-classes instead of adding / removing style classes on every update, which
                        // made each scrolled cell go through a full CSS pass
                        boolean isNull = false, isNum = false, isTrue = false, isFalse = false;
                        if (empty) {
                            setText(null);
                        } else if (item == null) {
                            String nullText = app.services.SettingsService.getInstance().getCurrentSettings().getNullDisplayString();
                            setText(nullText != null ? nullText : "NULL");
                            isNull = true;
                        } else {
                            String text = item.toString();
                            setText(text);
                            if (item instanceof Boolean b) {
                                isTrue = b;
                                isFalse = !b;
                            } else if (item instanceof Number) {
                                isNum = true;
                            } else if (text.length() <= 5) {
                                isTrue = "true".equalsIgnoreCase(text);
                                isFalse = "false".equalsIgnoreCase(text);
                            }
                        }
                        setGraphic(null);
                        setAlignment(isNum ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                        pseudoClassStateChanged(PC_NULL, isNull);
                        pseudoClassStateChanged(PC_NUMBER, isNum);
                        pseudoClassStateChanged(PC_TRUE, isTrue);
                        pseudoClassStateChanged(PC_FALSE, isFalse);
                    }
                };

                // Multi-cell Shift+Click and mouse range drag selection
                cell.setOnMousePressed(event -> {
                    if (event.getButton() == MouseButton.PRIMARY && !cell.isEmpty()) {
                        int r = cell.getIndex();
                        int c = table.getColumns().indexOf(col);
                        if (event.isShiftDown() && anchorRow >= 0 && anchorCol >= 0) {
                            selectRange(anchorRow, anchorCol, r, c);
                            event.consume();
                        } else {
                            anchorRow = r;
                            anchorCol = c;
                            if (!event.isControlDown() && !event.isShortcutDown()) {
                                table.getSelectionModel().clearSelection();
                                table.getSelectionModel().select(r, col);
                            }
                        }
                    }
                });

                cell.setOnMouseDragEntered(event -> {
                    if (!cell.isEmpty() && anchorRow >= 0 && anchorCol >= 0) {
                        int r = cell.getIndex();
                        int c = table.getColumns().indexOf(col);
                        selectRange(anchorRow, anchorCol, r, c);
                    }
                });

                return cell;
            });

            newColumns.add(col);
        }

        // one change for all columns: adding 279 columns one by one re-ran the frozen-header hook 279 times
        table.getColumns().setAll(newColumns);
        app.ui.results.FrozenColumnSupport.install(table, rowNumCol);

        table.setOnDragDetected(event -> table.startFullDrag());
    }

    /** The quick filter above the grid applies to every visible grid. */
    private final javafx.animation.PauseTransition quickFilterDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));

    private void applyCombinedFilter() {
        quickFilterDebounce.stop();
        String globalQuery = (resultsFilterField != null) ? resultsFilterField.getText() : null;
        if (mainFilterState != null) mainFilterState.setGlobalQuery(globalQuery);
        for (var st : subFilterStates) st.setGlobalQuery(globalQuery);
        updateStatsLabel();
    }

    private void updateStatsLabel() {
        if (resultsStatsLabel != null && mainFilterState != null && currentResult != null) {
            if (mainFilterState.hasFilters()) {
                if (resultsStatsLabel.getTooltip() == null) {
                    Tooltip tip = new Tooltip();
                    tip.setShowDelay(javafx.util.Duration.millis(250));
                    tip.setShowDuration(javafx.util.Duration.seconds(30));
                    // describe() counts rows per filter (a full scan each): only when the tooltip opens
                    tip.setOnShowing(ev -> {
                        if (mainFilterState != null) tip.setText(mainFilterState.describe());
                    });
                    resultsStatsLabel.setTooltip(tip);
                }
            } else {
                resultsStatsLabel.setTooltip(null);
            }
        }
        if (resultsStatsLabel != null && currentResult != null) {
            int shown = filteredRows.size();
            int total = currentResult.getRowCount();
            if (shown == total) {
                resultsStatsLabel.setText(String.format("%,d rows (%d ms)", total, currentResult.getExecutionDurationMs()));
            } else {
                resultsStatsLabel.setText(String.format("Showing %,d of %,d rows (%d ms)", shown, total, currentResult.getExecutionDurationMs()));
            }
        }
    }

    private void updateFooterStats(int rowCount, long durationMs, String note) {
        if (footerRowsLabel != null) {
            footerRowsLabel.setText(String.format("%,d dòng", rowCount));
        }
        if (footerDurationBadge != null) {
            footerDurationBadge.setText(durationMs + " ms");
        }
        if (footerSizeBadge != null) {
            long approxBytes = (long) rowCount * 256;
            if (approxBytes < 1024) {
                footerSizeBadge.setText(approxBytes + " B");
            } else if (approxBytes < 1024 * 1024) {
                footerSizeBadge.setText(String.format("%.2f KB", approxBytes / 1024.0));
            } else {
                footerSizeBadge.setText(String.format("%.2f MB", approxBytes / (1024.0 * 1024.0)));
            }
        }
        if (footerConnLabel != null) {
            footerConnLabel.setText(footerContext != null ? footerContext : "Chưa có kết nối");
        }
    }

    @FXML
    public void handleCopyExecutionStats() {
        if (currentResult == null) return;
        String json = String.format("{\n  \"rows\": %d,\n  \"durationMs\": %d,\n  \"columns\": %d,\n  \"timestamp\": \"%s\"\n}",
                currentResult.getRowCount(),
                currentResult.getExecutionDurationMs(),
                currentResult.getColumnCount(),
                currentResult.getTimestamp());
        copyTextToClipboard(json);
        showNotice("Copied execution metrics JSON to clipboard");
    }

    public void appendSqlMessage(String message) {
        if (sqlMessagesArea != null && message != null) {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            sqlMessagesArea.appendText("[" + ts + "] " + message + "\n");
            sqlMessagesArea.positionCaret(sqlMessagesArea.getLength());
        }
    }

    @FXML
    public void handleClearSqlMessages() {
        if (sqlMessagesArea != null) {
            sqlMessagesArea.clear();
        }
    }

    @FXML
    public void handleCopySqlMessages() {
        if (sqlMessagesArea != null) {
            copyTextToClipboard(sqlMessagesArea.getText());
            showNotice("Copied all SQL messages to clipboard");
        }
    }

    @FXML
    public void handleToggleSqlMessagesWrap() {
        if (sqlMessagesArea != null && sqlMessagesWrapToggle != null) {
            sqlMessagesArea.setWrapText(sqlMessagesWrapToggle.isSelected());
        }
    }

    public void simulateQueryExecution(String sql, Runnable onComplete) {
        showLoading(true);

        new Thread(() -> {
            try {
                Thread.sleep(350);
            } catch (InterruptedException ignored) {}

            Platform.runLater(() -> {
                showLoading(false);
                QueryResult newResult = QueryResultMockService.getInstance().generateDefaultQueryResult();
                setQueryResult(newResult);

                addMessage(ConsoleMessage.Level.SUCCESS, "Executed: " + (sql != null ? sql.lines().findFirst().orElse("SELECT ...") : "SELECT ..."), "38ms");
                appendSqlMessage("Query executed successfully. (" + newResult.getRowCount() + " rows affected)");

                if (onComplete != null) {
                    onComplete.run();
                }

                app.services.NotificationService.getInstance().showSuccess(
                    "Query Executed",
                    String.format("%d rows retrieved in %dms", newResult.getRowCount(), newResult.getExecutionDurationMs())
                );
            });
        }).start();
    }

    public void showLoading(boolean loading) {
        showLoading(loading, "Đang chạy truy vấn…");
    }

    /** DLG-014: shared overlay with elapsed time and "Hủy" (cancels the running query). */
    public void showLoading(boolean loading, String message) {
        if (loadingOverlay == null) return;
        if (loadingSupport == null) loadingSupport = app.ui.components.LoadingOverlay.attach(loadingOverlay);
        if (loading) {
            loadingSupport.show(message, () -> app.framework.query.DesktopQueryExecutionService.getInstance().cancel());
        } else {
            loadingSupport.hide();
        }
    }

    public void addMessage(ConsoleMessage.Level level, String text, String duration) {
        messagesList.add(new ConsoleMessage(level, text, duration));
        if (timelineTab != null) {
            timelineTab.setText("Timeline (" + messagesList.size() + ")");
        }
        appendSqlMessage(text);
    }

    public void selectResultsTab() {
        if (resultsTabPane != null && resultsTab != null) {
            resultsTabPane.getSelectionModel().select(resultsTab);
        }
    }

    public void selectMessagesTab() {
        if (resultsTabPane != null && messagesTab != null) {
            resultsTabPane.getSelectionModel().select(messagesTab);
        }
    }

    public void selectTimelineTab() {
        if (resultsTabPane != null && timelineTab != null) {
            resultsTabPane.getSelectionModel().select(timelineTab);
        }
    }

    // Terminal interaction
    private void handleTerminalCommand() {
        String input = terminalInput.getText().trim();
        if (input.isEmpty()) return;

        terminalOutput.appendText("postgres=# " + input + "\n");
        commandHistory.add(input);
        historyIndex = commandHistory.size();
        terminalInput.clear();

        String lower = input.toLowerCase();
        if (lower.equals("clear")) {
            terminalOutput.clear();
        } else if (lower.equals("help") || lower.equals("\\?")) {
            terminalOutput.appendText(
                "Available commands:\n" +
                "  \\l         List databases\n" +
                "  \\dt        List tables in current schema\n" +
                "  \\d <tbl>   Describe table columns\n" +
                "  SELECT ... Execute SQL statement\n" +
                "  clear      Clear terminal screen\n\n"
            );
        } else if (lower.equals("\\l")) {
            terminalOutput.appendText("List of databases:\n  Name     | Owner    | Encoding | Collate\n  ---------+----------+----------+--------\n  postgres | postgres | UTF8     | en_US.utf8\n  dev_db   | postgres | UTF8     | en_US.utf8\n  audit_dw | analytics| UTF8     | en_US.utf8\n(3 rows)\n\n");
        } else if (lower.equals("\\dt")) {
            terminalOutput.appendText("List of relations:\n  Schema | Name        | Type  | Owner\n  -------+-------------+-------+---------\n  public | users       | table | postgres\n  public | orders      | table | postgres\n  public | order_items | table | postgres\n  public | products    | table | postgres\n(4 rows)\n\n");
        } else {
            terminalOutput.appendText("Result: 50 rows returned in 18ms.\n\n");
        }

        terminalOutput.positionCaret(terminalOutput.getLength());
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) return;
        historyIndex += direction;
        if (historyIndex < 0) historyIndex = 0;
        if (historyIndex >= commandHistory.size()) {
            historyIndex = commandHistory.size();
            terminalInput.clear();
            return;
        }
        terminalInput.setText(commandHistory.get(historyIndex));
        terminalInput.positionCaret(terminalInput.getText().length());
    }

    @FXML
    public void handleExportCsv() {
        if (currentResult == null) return;
        List<String> cols = currentResult.getColumns().stream().map(QueryResultColumn::getName).toList();
        List<List<Object>> rows = filteredRows.stream().map(QueryResultRow::getValues).toList();
        String csv = GridDataExportUtils.formatCsv(cols, rows, true);
        copyTextToClipboard(csv);
        showNotice("Exported " + filteredRows.size() + " rows to CSV (Copied to Clipboard)");
    }

    @FXML
    public void handleExportJson() {
        if (currentResult == null) return;
        List<String> cols = currentResult.getColumns().stream().map(QueryResultColumn::getName).toList();
        List<List<Object>> rows = filteredRows.stream().map(QueryResultRow::getValues).toList();
        String json = GridDataExportUtils.formatJson(cols, rows, true);
        copyTextToClipboard(json);
        showNotice("Exported " + filteredRows.size() + " rows to JSON (Copied to Clipboard)");
    }

    @FXML
    public void handleExportMarkdown() {
        if (currentResult == null) return;
        List<String> cols = currentResult.getColumns().stream().map(QueryResultColumn::getName).toList();
        List<List<Object>> rows = filteredRows.stream().map(QueryResultRow::getValues).toList();
        String md = GridDataExportUtils.formatMarkdownTable(cols, rows);
        copyTextToClipboard(md);
        showNotice("Exported " + filteredRows.size() + " rows to Markdown Table (Copied to Clipboard)");
    }

    @FXML
    public void handleClearMessages() {
        messagesList.clear();
        if (timelineTab != null) {
            timelineTab.setText("Timeline (0)");
        }
    }

    @FXML
    public void handleCopyAllMessages() {
        StringBuilder sb = new StringBuilder();
        for (ConsoleMessage msg : messagesList) {
            sb.append(msg.toString()).append("\n");
        }
        copyTextToClipboard(sb.toString());
        showNotice("Copied " + messagesList.size() + " timeline records to clipboard");
    }

    private void copyTextToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void showNotice(String text) {
        app.services.NotificationService.getInstance().showSuccess("Results", text);
    }

    private void showError(String text) {
        app.services.NotificationService.getInstance().showError("Results Error", text);
    }

    public TableView<QueryResultRow> getResultsTable() {
        return resultsTable;
    }

    public TabPane getResultsTabPane() {
        return resultsTabPane;
    }

    public ObservableList<QueryResultRow> getMasterRows() {
        return masterRows;
    }

    public Tab getExecutionPlanTab() {
        return executionPlanTab;
    }

    // =========================================================================
    // Execution Plan Setup & Management (Feature 7)
    // =========================================================================

    private void setupExecutionPlan() {
        if (planTreeView != null) {
            planTreeView.setCellFactory(tv -> new TreeCell<PlanOperatorNode>() {
                @Override
                protected void updateItem(PlanOperatorNode item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("");
                    } else {
                        HBox row = new HBox(8);
                        row.setAlignment(Pos.CENTER_LEFT);

                        // Operator Icon
                        FontIcon icon = new FontIcon(item.getIconLiteral());
                        icon.getStyleClass().add("plan-op-icon");

                        // Name and Relation
                        Label nameLabel = new Label(item.getDisplayName());
                        nameLabel.getStyleClass().add("plan-op-name");

                        // Cost % Badge
                        Label costBadge = new Label(String.format("%.1f%%", item.getCostPercent()));
                        costBadge.getStyleClass().addAll("badge-pill", "plan-cost-badge-" + item.getCostSeverity());

                        // Subtree Cost & Rows
                        String rowsInfo = (item.getActualRows() != null)
                                ? String.format("Act: %,d / Est: %,d", item.getActualRows().longValue(), (long) item.getEstimatedRows())
                                : String.format("Est: %,d rows", (long) item.getEstimatedRows());
                        Label rowsLabel = new Label(rowsInfo);
                        rowsLabel.getStyleClass().add("text-muted");
                        rowsLabel.setStyle("-fx-font-size: 10.5px;");

                        row.getChildren().addAll(icon, nameLabel, costBadge, rowsLabel);
                        setGraphic(row);
                        setText(null);
                    }
                }
            });

            planTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.getValue() != null) {
                    populateInspector(newVal.getValue());
                }
            });
        }

        if (inspectorPropertiesTable != null) {
            if (propNameCol != null) {
                propNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getKey()));
            }
            if (propValueCol != null) {
                propValueCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValue()));
            }
            inspectorPropertiesTable.setItems(inspectorPropertiesList);
        }
    }

    public void setExecutionPlan(PhysicalExecutionPlan plan) {
        this.currentExecutionPlan = plan;
        if (plan == null) return;

        if (planDialectBadge != null) {
            planDialectBadge.setText(plan.getDialect().toUpperCase());
            planDialectBadge.setVisible(true);
            planDialectBadge.setManaged(true);
        }
        if (planTypeBadge != null) {
            String typeText = plan.getPlanType().toUpperCase();
            if (plan.isSimulated()) {
                typeText += " (MÔ PHỎNG)";
            }
            planTypeBadge.setText(typeText);
            planTypeBadge.setVisible(true);
            planTypeBadge.setManaged(true);
        }
        if (planTotalCostLabel != null) {
            planTotalCostLabel.setText(String.format("Cost: %.2f", plan.getTotalCost()));
        }
        if (planOperatorsCountLabel != null) {
            planOperatorsCountLabel.setText(plan.getNodeCount() + " operators");
        }
        if (planDurationLabel != null) {
            if (plan.getExecutionDurationMs() != null) {
                planDurationLabel.setText(plan.getExecutionDurationMs() + " ms");
            } else {
                planDurationLabel.setText("");
            }
        }

        // Build Visual Tree
        if (planTreeView != null && plan.getRootNode() != null) {
            TreeItem<PlanOperatorNode> rootItem = buildTreeItem(plan.getRootNode());
            planTreeView.setRoot(rootItem);
            rootItem.setExpanded(true);

            // Select highest cost node
            PlanOperatorNode highest = plan.findHighestCostNode();
            if (highest != null) {
                selectTreeNode(rootItem, highest);
                populateInspector(highest);
            } else {
                populateInspector(plan.getRootNode());
            }
        }

        // Set Raw Text
        if (planRawTextArea != null) {
            String raw = plan.getRawPlanText();
            if (raw == null || raw.isBlank()) {
                raw = plan.toTextTree();
            }
            planRawTextArea.setText(raw);
        }

        // Switch to Visual View
        if (planEmptyStateBox != null) {
            planEmptyStateBox.setVisible(false);
            planEmptyStateBox.setManaged(false);
        }
        if (planLoadingBox != null) {
            planLoadingBox.setVisible(false);
            planLoadingBox.setManaged(false);
        }

        boolean showRaw = togglePlanViewMode != null && togglePlanViewMode.isSelected();
        if (planVisualSplitPane != null) {
            planVisualSplitPane.setVisible(!showRaw);
            planVisualSplitPane.setManaged(!showRaw);
        }
        if (planRawBox != null) {
            planRawBox.setVisible(showRaw);
            planRawBox.setManaged(showRaw);
        }
    }

    private TreeItem<PlanOperatorNode> buildTreeItem(PlanOperatorNode node) {
        TreeItem<PlanOperatorNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        for (PlanOperatorNode child : node.getChildren()) {
            item.getChildren().add(buildTreeItem(child));
        }
        return item;
    }

    private boolean selectTreeNode(TreeItem<PlanOperatorNode> current, PlanOperatorNode target) {
        if (current == null || target == null) return false;
        if (current.getValue() == target) {
            planTreeView.getSelectionModel().select(current);
            return true;
        }
        for (TreeItem<PlanOperatorNode> child : current.getChildren()) {
            if (selectTreeNode(child, target)) return true;
        }
        return false;
    }

    private void populateInspector(PlanOperatorNode op) {
        inspectorPropertiesList.clear();
        if (op == null) return;

        if (inspectorOpBadge != null) {
            inspectorOpBadge.setText(op.getDisplayName());
        }

        addInspectorProp("Toán tử (Operator)", op.getOperatorType());
        addInspectorProp("Physical Op", op.getPhysicalOp());
        addInspectorProp("Logical Op", op.getLogicalOp());
        addInspectorProp("Bảng / Quan hệ", op.getRelationName().isEmpty() ? "(None)" : op.getRelationName());
        addInspectorProp("Schema / Database", (op.getSchema().isEmpty() ? "dbo" : op.getSchema()) + " · " + op.getDatabase());
        addInspectorProp("Chi phí (% Cost)", String.format("%.1f%% (%s)", op.getCostPercent(), op.getCostSeverity().toUpperCase()));
        addInspectorProp("Operator Cost", String.format("%.4f", op.getOperatorCost()));
        addInspectorProp("Subtree Cost", String.format("%.4f", op.getSubtreeCost()));
        addInspectorProp("Dòng ước tính (Est Rows)", String.format("%,d", (long) op.getEstimatedRows()));
        addInspectorProp("Dòng thực tế (Act Rows)", op.getActualRows() != null ? String.format("%,d", op.getActualRows().longValue()) : "N/A (Estimated Plan)");
        addInspectorProp("Thời gian chạy (Time)", op.getActualTimeMs() != null ? String.format("%.2f ms", op.getActualTimeMs()) : "N/A (Estimated Plan)");
        addInspectorProp("Số vòng lặp (Loops)", op.getActualLoops() != null ? String.valueOf(op.getActualLoops()) : "N/A");
        addInspectorProp("Cảnh báo (Warnings)", op.getWarnings().isEmpty() ? "None" : String.join(", ", op.getWarnings()));
        addInspectorProp("Output Columns", op.getOutputColumns().isEmpty() ? "All" : String.join(", ", op.getOutputColumns()));
    }

    private void addInspectorProp(String name, String value) {
        inspectorPropertiesList.add(new AbstractMap.SimpleEntry<>(name, value != null ? value : ""));
    }

    public void showPlanLoading(boolean loading) {
        if (planLoadingBox != null) {
            planLoadingBox.setVisible(loading);
            planLoadingBox.setManaged(loading);
        }
        if (loading) {
            if (planEmptyStateBox != null) {
                planEmptyStateBox.setVisible(false);
                planEmptyStateBox.setManaged(false);
            }
            if (planVisualSplitPane != null) {
                planVisualSplitPane.setVisible(false);
                planVisualSplitPane.setManaged(false);
            }
            if (planRawBox != null) {
                planRawBox.setVisible(false);
                planRawBox.setManaged(false);
            }
        }
    }

    public void selectExecutionPlanTab() {
        if (resultsTabPane != null && executionPlanTab != null) {
            resultsTabPane.getSelectionModel().select(executionPlanTab);
        }
    }

    @FXML
    public void handleFetchEstimatedPlan() {
        if (onFetchEstimatedPlanCallback != null) {
            onFetchEstimatedPlanCallback.run();
        } else {
            // Default demo / fallback execution plan
            var plan = app.framework.plan.DesktopExecutionPlanService.getInstance()
                    .generateSimulatedPlan("SELECT * FROM Customer c JOIN Orders o ON c.Id = o.CustomerId", "sqlserver", "estimated");
            setExecutionPlan(plan);
            selectExecutionPlanTab();
        }
    }

    @FXML
    public void handleFetchActualPlan() {
        if (onFetchActualPlanCallback != null) {
            onFetchActualPlanCallback.run();
        } else {
            // Default demo / fallback actual plan
            var plan = app.framework.plan.DesktopExecutionPlanService.getInstance()
                    .generateSimulatedPlan("SELECT * FROM Customer c JOIN Orders o ON c.Id = o.CustomerId", "sqlserver", "actual");
            setExecutionPlan(plan);
            selectExecutionPlanTab();
        }
    }

    @FXML
    public void handleTogglePlanViewMode() {
        boolean showRaw = togglePlanViewMode != null && togglePlanViewMode.isSelected();
        if (planVisualSplitPane != null) {
            planVisualSplitPane.setVisible(!showRaw);
            planVisualSplitPane.setManaged(!showRaw);
        }
        if (planRawBox != null) {
            planRawBox.setVisible(showRaw);
            planRawBox.setManaged(showRaw);
        }
        if (togglePlanViewMode != null) {
            togglePlanViewMode.setText(showRaw ? "Visual Tree" : "Raw Text");
        }
    }

    @FXML
    public void handleCopyExecutionPlan() {
        if (currentExecutionPlan != null) {
            copyTextToClipboard(currentExecutionPlan.toTextTree());
            showNotice("Đã sao chép Execution Plan vào Clipboard");
        } else if (planRawTextArea != null && !planRawTextArea.getText().isBlank()) {
            copyTextToClipboard(planRawTextArea.getText());
            showNotice("Đã sao chép Execution Plan vào Clipboard");
        } else {
            showNotice("Không có Execution Plan nào để sao chép");
        }
    }

    public void setOnFetchEstimatedPlanCallback(Runnable callback) {
        this.onFetchEstimatedPlanCallback = callback;
    }

    public void setOnFetchActualPlanCallback(Runnable callback) {
        this.onFetchActualPlanCallback = callback;
    }

    public PhysicalExecutionPlan getCurrentExecutionPlan() {
        return currentExecutionPlan;
    }
}
