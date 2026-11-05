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
    private final ObservableList<QueryResultRow> masterRows = FXCollections.observableArrayList();
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
        if (loadingOverlay != null) loadingSupport = app.ui.components.LoadingOverlay.attach(loadingOverlay);
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
        if (resultsTable != null) {
            resultsTable.setStyle(gridStyle);
            if (!resultsTable.getColumns().isEmpty() && "#".equals(resultsTable.getColumns().get(0).getText())) {
                resultsTable.getColumns().get(0).setVisible(settings.isShowRowNumbers());
            }
        }
        if (multiGridBox != null) {
            for (javafx.scene.Node n : multiGridBox.getChildren()) {
                if (n instanceof VBox b) {
                    for (javafx.scene.Node sub : b.getChildren()) {
                        if (sub instanceof TableView<?> tv) {
                            tv.setStyle(gridStyle);
                            if (!tv.getColumns().isEmpty() && "#".equals(tv.getColumns().get(0).getText())) {
                                tv.getColumns().get(0).setVisible(settings.isShowRowNumbers());
                            }
                        }
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
            resultsFilterField.textProperty().addListener((obs, oldVal, newVal) -> {
                applyCombinedFilter();
            });
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
        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                grid().getSelectionModel().select(r, grid().getColumns().get(c));
            }
        }
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
        grid().getSelectionModel().clearSelection();
        for (int r = 0; r < gridRows().size(); r++) {
            for (TableColumn<QueryResultRow, ?> col : grid().getColumns()) {
                grid().getSelectionModel().select(r, col);
            }
        }
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
                if (resultsTab != null) {
                    resultsTab.setText(String.format("Results (%d trong %d bảng)", total, currentResultSets.size()));
                }
                updateFooterStats(total, maxMs, "multi-result");
            } else if (multiGridScroll != null) {
                hideResultTabs();
                multiGridScroll.setVisible(true);
                multiGridScroll.setManaged(true);
                multiGridBox.getChildren().clear();

                int totalAllRows = 0;
                long maxDuration = 0;

                for (int i = 0; i < currentResultSets.size(); i++) {
                    QueryResult res = currentResultSets.get(i);
                    totalAllRows += res.getRowCount();
                    if (res.getExecutionDurationMs() > maxDuration) {
                        maxDuration = res.getExecutionDurationMs();
                    }

                    VBox paneBox = createSubGridPane(res, i + 1);
                    multiGridBox.getChildren().add(paneBox);
                }

                if (resultsTab != null) {
                    resultsTab.setText(String.format("Results (%d trong %d bảng)", totalAllRows, currentResultSets.size()));
                }

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
        String source = sourceTableName(res.getQuery());
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
        populateTableViewWithResult(subTable, res);
        var subState = attachRows(subTable, res);
        installGrid(subTable);
        bindCountBadge(rows, subState);
        // SSMS-like: each grid is as tall as its rows but never taller than the panel, so every
        // grid has its own scrollbar and the outer scroll moves between grids
        // T11 (Phase 7): no setFixedCellSize here. With a fixed cell size TableRowSkin drops the
        // cells of columns scrolled out of view, so the frozen "#" cell vanished on horizontal
        // scroll in stacked grids. The 24px row height comes from CSS (.stacked-grid) instead.
        double rowsHeight = 30 + Math.max(1, res.getRowCount()) * 24 + 18;
        subTable.setMinHeight(Math.min(rowsHeight, 120));
        subTable.setPrefHeight(rowsHeight);
        if (multiGridScroll != null) {
            subTable.maxHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.min(rowsHeight, Math.max(150, multiGridScroll.getHeight() * 0.75)),
                    multiGridScroll.heightProperty()));
        } else {
            subTable.setMaxHeight(Math.min(rowsHeight, 30 + STACKED_VISIBLE_ROWS * 24 + 18));
        }

        header.setOnMouseClicked(e -> {
            boolean open = !subTable.isVisible();
            subTable.setVisible(open);
            subTable.setManaged(open);
            chevron.setIconLiteral(open ? "fth-chevron-down" : "fth-chevron-right");
            box.getStyleClass().remove("collapsed");
            if (!open) box.getStyleClass().add("collapsed");
        });

        box.getChildren().addAll(header, subTable);
        return box;
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
            Tab tab = new Tab(String.format("Bảng %d (%,d)", i + 1, res.getRowCount()));
            FontIcon ic = new FontIcon("fth-grid");
            tab.setGraphic(ic);
            if (res.getQuery() != null) tab.setTooltip(new Tooltip(res.getQuery()));
            Runnable build = () -> {
                if (tab.getContent() != null) return;
                TableView<QueryResultRow> t = new TableView<>();
                t.getStyleClass().addAll("results-table", "tabbed-grid");
                t.getSelectionModel().setCellSelectionEnabled(true);
                t.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                populateTableViewWithResult(t, res);
                var st = attachRows(t, res);
                installGrid(t);
                int n = tab.getTabPane() != null ? tab.getTabPane().getTabs().indexOf(tab) + 1 : 0;
                Tooltip tip = new Tooltip();
                tip.setShowDelay(javafx.util.Duration.millis(250));
                tip.setShowDuration(javafx.util.Duration.seconds(30));
                st.setOnChanged(() -> {
                    boolean on = st.hasFilters();
                    tab.setText(on ? String.format("Bảng %d (%,d / %,d)", n, st.shownCount(), st.totalCount())
                            : String.format("Bảng %d (%,d)", n, st.totalCount()));
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

        if (result == null || result.getRowCount() == 0) {
            if (emptyStateBox != null) {
                emptyStateBox.setVisible(true);
                emptyStateBox.setManaged(true);
            }
            if (resultsStatsLabel != null) {
                resultsStatsLabel.setText("No query executed yet");
            }
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
            resultsTab.setText("Results (" + result.getRowCount() + ")");
        }

        updateFooterStats(result.getRowCount(), result.getExecutionDurationMs(), null);
    }

    private void populateTableViewWithResult(TableView<QueryResultRow> table, QueryResult result) {
        table.getColumns().clear();
        table.getProperties().put(GRID_RESULT_KEY, result);
        table.getProperties().remove(GRID_PINNED_KEY);

        app.models.IdeSettings curSettings = app.services.SettingsService.getInstance().getCurrentSettings();
        String gridFont = curSettings.getGrid() != null ? curSettings.getGrid().getFontFamily() : "Inter";
        int gridFontSize = curSettings.getGrid() != null ? curSettings.getGrid().getFontSize() : 12;
        table.setStyle(String.format(java.util.Locale.ROOT, "-fx-font-family: '%s'; -fx-font-size: %dpx;", gridFont, gridFontSize));

        // 1. Column 0: Row Number '#'
        TableColumn<QueryResultRow, Integer> rowNumCol = new TableColumn<>("#");
        // fixed while scrolling horizontally, ~50px by default, user can drag it wider (2026-11-05)
        rowNumCol.setPrefWidth(30);
        rowNumCol.setMinWidth(24);
        rowNumCol.setResizable(true);
        rowNumCol.setReorderable(false);
        rowNumCol.setSortable(false);
        rowNumCol.getStyleClass().add("row-number-column");
        rowNumCol.setVisible(curSettings.isShowRowNumbers());
        rowNumCol.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getRowNumber()));

        Label rowNumHeaderLabel = new Label("#");
        rowNumHeaderLabel.setStyle("-fx-cursor: hand; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");
        rowNumHeaderLabel.setOnMouseClicked(event -> {
            table.getSelectionModel().clearSelection();
            for (int r = 0; r < table.getItems().size(); r++) {
                for (TableColumn<QueryResultRow, ?> col : table.getColumns()) {
                    table.getSelectionModel().select(r, col);
                }
            }
            event.consume();
        });
        rowNumCol.setGraphic(rowNumHeaderLabel);

        // Clicking row number cell selects whole row
        rowNumCol.setCellFactory(column -> {
            TableCell<QueryResultRow, Integer> cell = new TableCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(String.valueOf(item));
                        if (!getStyleClass().contains("row-number-cell")) getStyleClass().add("row-number-cell");
                    }
                    app.ui.results.FrozenColumnSupport.freezeCell(this, table);
                }
            };

            cell.setOnMouseClicked(event -> {
                if (!cell.isEmpty()) {
                    int rIdx = cell.getIndex();
                    if (event.isControlDown() || event.isShortcutDown()) {
                        for (TableColumn<QueryResultRow, ?> c : table.getColumns()) {
                            table.getSelectionModel().select(rIdx, c);
                        }
                    } else {
                        table.getSelectionModel().clearSelection();
                        for (TableColumn<QueryResultRow, ?> c : table.getColumns()) {
                            table.getSelectionModel().select(rIdx, c);
                        }
                    }
                    event.consume();
                }
            });

            return cell;
        });

        table.getColumns().add(rowNumCol);
        app.ui.results.FrozenColumnSupport.install(table, rowNumCol);

        // 2. Dynamic Columns from query result
        for (int i = 0; i < result.getColumns().size(); i++) {
            final int colIndex = i;
            QueryResultColumn colMeta = result.getColumns().get(i);

            TableColumn<QueryResultRow, Object> col = new TableColumn<>(colMeta.getHeaderLabel());
            col.setPrefWidth(140);
            col.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getValue(colIndex)));

            // Header: name + data type (80 % size, italic, muted) and the filter button pinned to
            // the right edge of the column, so it follows when the column is resized (2026-11-05).
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
            javafx.scene.layout.Region headerGap = new javafx.scene.layout.Region();
            HBox.setHgrow(headerGap, javafx.scene.layout.Priority.ALWAYS);
            headerGap.setMinWidth(0);

            // Clicking the title selects the entire column
            titleLabel.setOnMouseClicked(event -> {
                if (!(event.isControlDown() || event.isShortcutDown())) table.getSelectionModel().clearSelection();
                for (int r = 0; r < table.getItems().size(); r++) {
                    table.getSelectionModel().select(r, col);
                }
                event.consume();
            });

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
                    col.getStyleClass().remove("column-header-filtered");
                    if (on) {
                        filterBtn.getStyleClass().add("active-filter");
                        col.getStyleClass().add("column-header-filtered");
                    }
                    updateStatsLabel();
                });
            });

            HBox headerBox = new HBox(4, titleLabel, typeLabel, headerGap, filterBtn);
            headerBox.setAlignment(Pos.CENTER_LEFT);
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
                        getStyleClass().removeAll("null-cell-value", "boolean-cell-false", "boolean-cell-true", "number-cell");

                        if (empty) {
                            setText(null);
                            setGraphic(null);
                        } else if (item == null) {
                            String nullText = app.services.SettingsService.getInstance().getCurrentSettings().getNullDisplayString();
                            setText(nullText != null ? nullText : "NULL");
                            setGraphic(null);
                            getStyleClass().add("null-cell-value");
                            setAlignment(Pos.CENTER_LEFT);
                        } else {
                            setText(item.toString());
                            setGraphic(null);

                            if (item instanceof Boolean b) {
                                getStyleClass().add(b ? "boolean-cell-true" : "boolean-cell-false");
                                setAlignment(Pos.CENTER_LEFT);
                            } else if (item instanceof Number) {
                                getStyleClass().add("number-cell");
                                setAlignment(Pos.CENTER_RIGHT);
                            } else {
                                String str = item.toString().trim();
                                if ("false".equalsIgnoreCase(str)) {
                                    getStyleClass().add("boolean-cell-false");
                                } else if ("true".equalsIgnoreCase(str)) {
                                    getStyleClass().add("boolean-cell-true");
                                }
                                setAlignment(Pos.CENTER_LEFT);
                            }
                        }
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

            table.getColumns().add(col);
        }

        table.setOnDragDetected(event -> table.startFullDrag());
    }

    /** The quick filter above the grid applies to every visible grid. */
    private void applyCombinedFilter() {
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
                    resultsStatsLabel.setTooltip(tip);
                }
                resultsStatsLabel.getTooltip().setText(mainFilterState.describe());
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
            planTypeBadge.setText(plan.getPlanType().toUpperCase());
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
