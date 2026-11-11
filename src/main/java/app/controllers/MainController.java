package app.controllers;

import app.application.AppContext;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.state.GlobalDesktopState;
import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import app.services.ThemeService;
import app.ui.dialogs.ConnectionManagerDialog;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MainController.class);


    @FXML private BorderPane mainWindowRoot;

    // Injected Title Bar Component
    @FXML private HBox titleBar;
    @FXML private app.ui.titlebar.TitleBarController titleBarController;

    // Menu Bar components
    @FXML private MenuBar mainMenuBar;
    @FXML private HBox searchBox;
    @FXML private HBox layoutActions;
    @FXML private Menu themesMenu;
    @FXML private CheckMenuItem menuViewExplorer;
    @FXML private CheckMenuItem menuViewResults;
    @FXML private CheckMenuItem menuViewAutoHideMenu;
    @FXML private MenuItem menuGotoBegin;
    @FXML private MenuItem menuGotoEnd;

    /** Connection name -> environment label (DEV/TEST/STAGING/PROD) for the title bar badge. */
    private final java.util.Map<String, String> connectionEnvironments = new java.util.HashMap<>();
    /** Connection name -> database type (POSTGRESQL / SQLSERVER / SQLITE) for the context bar icon. */
    private final java.util.Map<String, String> connectionTypes = new java.util.HashMap<>();
    private final java.util.Map<String, String> connectionIds = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean> connectionReadOnly = new java.util.HashMap<>();
    /** True once the persisted Explorer pixel width has been applied to the split (SCR-MAIN-001). */
    private boolean sidebarWidthApplied = false;
    /** Connection name -> default database of the profile (context bar fallback). */
    private final java.util.Map<String, String> connectionDefaultDatabases = new java.util.HashMap<>();
    private boolean autoHideMenuBar = false;

    // Tool Bar components
    @FXML private ComboBox<String> connectionComboBox;
    @FXML private ComboBox<String> schemaComboBox;
    @FXML private TextField searchField;
    @FXML private Button themeToggleButton;
    @FXML private FontIcon themeToggleIcon;
    @FXML private Button settingsButton;
    @FXML private Button executeButton;
    @FXML private FontIcon executeButtonIcon;
    @FXML private Button stopButton;
    @FXML private ComboBox<String> limitComboBox;
    @FXML private MenuItem menuBravoTool;
    @FXML private ComboBox<String> databaseComboBox;
    @FXML private Label toolbarEnvBadge;
    @FXML private FontIcon connectionTypeIcon;

    // SplitPanes and Panels
    @FXML private SplitPane mainHorizontalSplit;
    @FXML private SplitPane centerVerticalSplit;
    @FXML private VBox sidebar;
    @FXML private VBox editor;
    @FXML private VBox results;
    @FXML private HBox contextToolBar;
    @FXML private VBox editorSlot;
    @FXML private HBox statusBar;

    // Injected Child Controllers
    @FXML private SidebarController sidebarController;
    @FXML private EditorController editorController;
    @FXML private ResultsController resultsController;
    @FXML private StatusBarController statusBarController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        AppContext.getInstance().setMainController(this);
        setupTitleBarContent();
        setupToolbarData();
        setupTitleBarExtras();
        setupThemeMenu();
        setupThemeListener();
        setupEditorIntegration();
        setupSidebarIntegration();
        setupLayoutIntegration();
        setupCardClipping();
        setupCardGaps();
        applyPanelStyle(app.services.SettingsService.getInstance().getCurrentSettings());
        setupStatusBar();
        setupWorkspaceIntegration();
        setupToolbarSettings(app.services.SettingsService.getInstance().getCurrentSettings());
        applyUiTypography(app.services.SettingsService.getInstance().getCurrentSettings());
        applyAutoHideToolbar(app.services.SettingsService.getInstance().getCurrentSettings());
        Platform.runLater(this::bindWindowStateToRoot);
        // Pre-warm Settings Dialog during idle so the first open has zero lag (< 20ms)
        Platform.runLater(() -> {
            Stage stage = AppContext.getInstance().getPrimaryStage();
            if (stage == null && mainWindowRoot != null && mainWindowRoot.getScene() != null) {
                stage = (Stage) mainWindowRoot.getScene().getWindow();
            }
            app.ui.dialogs.SettingsDialog.preload(stage);
        });
    }

    // ------------------------------------------------------------------ appearance.autoHideNavbar (S3)
    // LLTool autohide-navbar.js: the toolbar row is hidden; it comes back when the mouse touches the top edge
    // (6px under the title bar) and hides again shortly after the mouse leaves it, unless one of its lists is open.

    private boolean autoHideToolbar;
    private javafx.event.EventHandler<javafx.scene.input.MouseEvent> autoHideFilter;
    private final javafx.animation.PauseTransition toolbarHideDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));

    private void applyAutoHideToolbar(app.models.IdeSettings settings) {
        // LLTool: appearance.autoHideNavbar first, the root key only when there is no appearance section
        boolean on = settings != null && (settings.getAppearance() != null
                ? settings.getAppearance().isAutoHideNavbar() : settings.isAutoHideNavbar());
        if (contextToolBar == null || mainWindowRoot == null || on == autoHideToolbar) return;
        autoHideToolbar = on;
        if (autoHideFilter == null) {
            autoHideFilter = this::onMouseForAutoHide;
            toolbarHideDelay.setOnFinished(e -> {
                if (autoHideToolbar && !toolbarPopupOpen()) showToolbar(false);
            });
        }
        if (on) {
            mainWindowRoot.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, autoHideFilter);
            showToolbar(false);
        } else {
            mainWindowRoot.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, autoHideFilter);
            toolbarHideDelay.stop();
            showToolbar(true);
        }
    }

    private void showToolbar(boolean show) {
        contextToolBar.setVisible(show);
        contextToolBar.setManaged(show);
    }

    private void onMouseForAutoHide(javafx.scene.input.MouseEvent e) {
        double top = titleBar != null ? titleBar.localToScene(titleBar.getBoundsInLocal()).getMaxY() : 0;
        if (!contextToolBar.isVisible()) {
            if (e.getSceneY() >= top && e.getSceneY() <= top + 6) {
                toolbarHideDelay.stop();
                showToolbar(true);
            }
            return;
        }
        double bottom = contextToolBar.localToScene(contextToolBar.getBoundsInLocal()).getMaxY();
        if (e.getSceneY() > bottom + 12) {
            if (toolbarHideDelay.getStatus() != javafx.animation.Animation.Status.RUNNING) toolbarHideDelay.playFromStart();
        } else {
            toolbarHideDelay.stop();
        }
    }

    private boolean toolbarPopupOpen() {
        for (ComboBox<?> c : new ComboBox<?>[]{connectionComboBox, databaseComboBox, schemaComboBox, limitComboBox}) {
            if (c != null && c.isShowing()) return true;
        }
        return false;
    }

    /** SCR-MAIN-001: rounded cards whose outline is never covered by the content. */
    private void setupCardClipping() {
        app.ui.components.RoundedCard.install(sidebar, 8);
        app.ui.components.RoundedCard.clip(editor, 8);
        app.ui.components.RoundedCard.install(results, 8);
        // Toolbar and status bar are cards too (user request 2026-11-05), separated by 6px gaps.
        if (contextToolBar != null) {
            VBox.setMargin(contextToolBar, new javafx.geometry.Insets(6, 6, 0, 6));
            app.ui.components.RoundedCard.install(contextToolBar, 8);
        }
        if (statusBar != null) {
            BorderPane.setMargin(statusBar, new javafx.geometry.Insets(0, 6, 6, 6));
            app.ui.components.RoundedCard.clip(statusBar, 6);
        }
    }

    /**
     * Forces the 6px gap on the split dividers with an inline style (highest CSS priority).
     * The stylesheet rule alone did not widen the editor/results divider (user report 2026-11-05).
     * Dividers are recreated when panels are hidden/shown, so re-apply on every item change.
     */
    /**
     * Editor/results gap: styling the SplitPane divider did not produce a visible gap
     * (and broke the resize cursor), so the gap is the bottom padding of the editor slot.
     * The divider itself stays the default thin, draggable sash.
     */
    private void setupCardGaps() {
        if (centerVerticalSplit != null) {
            centerVerticalSplit.getItems().addListener(
                    (javafx.collections.ListChangeListener<javafx.scene.Node>) c -> updateEditorSlotGap());
        }
        dividerGapAppliers.add(this::updateEditorSlotGap);
        updateEditorSlotGap();
    }

    /**
     * T7 (Phase 7): the 2px SplitPane divider was too thin to grab, so the visible gap under the
     * editor card is a real sash: a Region at the bottom of the editor slot that drags the divider.
     */
    private final javafx.scene.layout.Region editorSash = new javafx.scene.layout.Region();
    private double sashStartY;
    private double sashStartPos;

    private void installEditorSash() {
        if (editorSlot == null || editorSlot.getChildren().contains(editorSash)) return;
        editorSash.getStyleClass().add("editor-sash");
        editorSash.setCursor(javafx.scene.Cursor.V_RESIZE);
        editorSash.setOnMousePressed(e -> {
            if (centerVerticalSplit == null || centerVerticalSplit.getDividers().isEmpty()) return;
            sashStartY = e.getScreenY();
            sashStartPos = centerVerticalSplit.getDividerPositions()[0];
            editorSash.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("dragging"), true);
            e.consume();
        });
        editorSash.setOnMouseDragged(e -> {
            if (centerVerticalSplit == null || centerVerticalSplit.getDividers().isEmpty()) return;
            double h = centerVerticalSplit.getHeight();
            if (h <= 0) return;
            double pos = sashStartPos + (e.getScreenY() - sashStartY) / h;
            centerVerticalSplit.setDividerPosition(0, Math.max(0.08, Math.min(0.95, pos)));
            e.consume();
        });
        editorSash.setOnMouseReleased(e -> editorSash.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("dragging"), false));
        editorSlot.getChildren().add(editorSash);
    }

    private void updateEditorSlotGap() {
        if (editorSlot == null) return;
        installEditorSash();
        boolean resultsDocked = centerVerticalSplit != null && results != null
                && centerVerticalSplit.getItems().contains(results);
        double gap = resultsDocked ? 4 : 0; // flat keeps the gaps of the rounded style (user 2026-11-06)
        editorSlot.setPadding(javafx.geometry.Insets.EMPTY);
        editorSash.setMinHeight(gap);
        editorSash.setPrefHeight(gap);
        editorSash.setMaxHeight(gap);
        editorSash.setVisible(resultsDocked);
        editorSash.setManaged(resultsDocked);
    }

    private final java.util.List<Runnable> dividerGapAppliers = new java.util.ArrayList<>();
    /** appearance.panelStyle = "flat" (user setting, 2026-11-05). */
    private boolean flatPanels = false;

    /**
     * Rounded cards or flat (square) cards. Both keep the same 6px gaps and card outlines; flat only removes
     * the rounding (user decision 2026-11-06: "margin giữa các khối như giao diện bo góc").
     */
    private void applyPanelStyle(app.models.IdeSettings settings) {
        boolean flat = settings != null && settings.isFlatPanels();
        flatPanels = flat;
        if (mainWindowRoot != null) {
            mainWindowRoot.getStyleClass().remove("flat-panels");
            if (flat) mainWindowRoot.getStyleClass().add("flat-panels");
        }
        double cardRadius = flat ? 0 : 8;
        app.ui.components.RoundedCard.setRadius(sidebar, cardRadius);
        app.ui.components.RoundedCard.setRadius(editor, cardRadius);
        app.ui.components.RoundedCard.setRadius(results, cardRadius);
        app.ui.components.RoundedCard.setRadius(contextToolBar, cardRadius);
        app.ui.components.RoundedCard.setRadius(statusBar, flat ? 0 : 6);
        if (contextToolBar != null) {
            VBox.setMargin(contextToolBar, new javafx.geometry.Insets(6, 6, 0, 6));
        }
        if (statusBar != null) {
            BorderPane.setMargin(statusBar, new javafx.geometry.Insets(0, 6, 6, 6));
        }
        dividerGapAppliers.forEach(Runnable::run);    // R-UI-01: element shapes (inputs, selections, buttons, popups) follow the same setting
        app.services.ThemeService.getInstance().applyShapeToAllWindows();
    }

    // ------------------------------------------------------------------ status bar (SCR-MAIN-005)

    /** Status bar + tab headers follow the active tab's session (Phase 8 / D1). */
    private void refreshActiveTabSession() {
        if (statusBarController == null) return;
        var tab = editorController != null ? editorController.getCurrentEditorTab() : null;
        if (tab != null && (tab.getConnectionName() == null || tab.getConnectionName().isBlank())) {
            String activeConn = GlobalDesktopState.getInstance().getActiveConnection();
            if (activeConn != null && !activeConn.isBlank()) {
                tab.setConnectionName(activeConn);
                syncConnectionToToolbar(activeConn);
                autoConnectNewTab(tab);
            }
        }
        var session = tab != null ? app.framework.session.TabSessionManager.getInstance().get(tab.getDocumentId()) : null;
        boolean connected = session != null && session.state() == app.framework.session.TabSession.State.CONNECTED;
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.CONNECTIONS)) {
            String name = statusBarController.getConnectionName();
            String id = name != null ? connectionIds.get(name) : null;
            connected = id != null && app.services.ExplorerConnectionState.getInstance().get(id)
                    == app.services.ExplorerConnectionState.Status.CONNECTED;
        }
        statusBarController.setConnected(connected);
        // connected but PID unknown shows "—" so the PID entry is never silently missing
        statusBarController.setPid(connected
                ? (session != null && session.serverPid() != null ? String.valueOf(session.serverPid()) : "—")
                : null);
        if (editorController != null && editorController.getEditorBar() != null) {
            editorController.getEditorBar().setPid(connected
                    ? (session != null && session.serverPid() != null ? String.valueOf(session.serverPid()) : "—")
                    : null);
        }
        statusBarController.setSessionInfo(
                connected ? session.database() : null,
                connected && tab != null ? tab.getSchemaName() : null,
                connected && session.profile() != null ? session.profile().username() : null);
        if (editorController != null) {
            editorController.refreshTabDecorations();
            editorController.checkActiveTabIfSessionChanged();   // check syntax once the tab is connected
        }
    }

    private void setupStatusBar() {
        if (statusBarController == null) return;
        statusBarController.setConnectionMetaResolver(name -> new StatusBarController.ConnectionMeta(
                connectionIds.getOrDefault(name, name),
                app.framework.backend.BackendFlags.readOnlyAll() || Boolean.TRUE.equals(connectionReadOnly.get(name)),
                connectionEnvironments.get(name)));
        statusBarController.setOnManageConnections(this::handleManageConnections);
        statusBarController.setOnShowProblems(() -> {
            if (resultsController != null) {
                app.services.LayoutService.getInstance().setResultsVisible(true);
                resultsController.selectMessagesTab();
            }
        });
        statusBarController.setOnShowResults(() -> {
            if (resultsController != null) {
                app.services.LayoutService.getInstance().setResultsVisible(true);
                resultsController.selectResultsTab();
            }
        });
        statusBarController.setOnOpenSettings(this::handleOpenSettings);
        statusBarController.setOnToggleIndent(this::toggleIndentation);
        statusBarController.setOnZoomChanged(pct -> applyEditorZoom());
        // Ctrl+Wheel inside the editor: show the new percentage in the status bar (no echo back to the editor)
        if (editorController != null) {
            editorController.setOnZoomChanged(pct -> statusBarController.setZoom(pct));
            editorController.setOnEditorBarReady(statusBarController::setEditorBar);
            editorController.setOnProblemsChanged((errors, warnings) -> statusBarController.setProblems(errors, warnings));
        }

        // Phase 8 / D1: the status bar shows the ACTIVE TAB's own session (state, server PID).
        Runnable refreshConnected = this::refreshActiveTabSession;
        app.framework.session.TabSessionManager.getInstance().revisionProperty()
                .addListener((o, a, b) -> refreshConnected.run());
        if (editorController != null) {
            editorController.getPrimaryTabPane().getSelectionModel().selectedItemProperty()
                    .addListener((o, a, b) -> refreshConnected.run());
            // D3: each tab runs on its own; Run / Stop and the loading overlay follow the ACTIVE tab, and output of a
            // tab that finished in the background is shown when the tab is opened again
            var queries = app.framework.query.DesktopQueryExecutionService.getInstance();
            queries.setActiveTabSupplier(this::activeTabId);
            editorController.getPrimaryTabPane().getSelectionModel().selectedItemProperty()
                    .addListener((o, a, b) -> {
                        // D4.3 (LLTool restoreTabResults): each tab keeps its own results / messages / plan
                        long t0 = System.nanoTime();
                        if (resultsController != null) {
                            if (a instanceof app.ui.editor.SqlEditorTab oldTab) resultsController.saveTabState(oldTab.getDocumentId());
                            resultsController.restoreTabState(b instanceof app.ui.editor.SqlEditorTab newTab ? newTab.getDocumentId() : null);
                        }
                        long t1 = System.nanoTime();
                        refreshRunState();
                        flushTabOutput();
                        long t2 = System.nanoTime();
                        // connection hierarchy: an open connection reaches the tab when it is shown; its own
                        // session is checked (a dead one turns into "Mất kết nối")
                        if (b instanceof app.ui.editor.SqlEditorTab shown && !shown.isCustomContent()) {
                            Platform.runLater(() -> {
                                autoConnectNewTab(shown);
                                app.framework.session.TabSessionManager.getInstance().probeAsync(shown.getDocumentId());
                            });
                        }
                        // Non-SQL tabs (Table Designer, Data Editor, Compare, Dependency, Visualizer, View panels)
                        // must hide Results panel by default and disallow results view
                        if (b instanceof app.ui.editor.SqlEditorTab tab && !tab.isSqlTextTab()) {
                            app.services.LayoutService.getInstance().setResultsVisible(false);
                        }
                        // the rest of the switch (other listeners, CSS + layout of the panel) shows in the next pulse
                        Platform.runLater(() -> log.info("[tab-switch] results {} ms, run state {} ms, until next pulse {} ms",
                                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (System.nanoTime() - t0) / 1_000_000));
                    });
            editorController.getPrimaryTabPane().getTabs().addListener((javafx.collections.ListChangeListener<javafx.scene.control.Tab>) c -> {
                while (c.next()) {
                    for (javafx.scene.control.Tab t : c.getAddedSubList()) {
                        if (t instanceof app.ui.editor.SqlEditorTab st && !st.isCustomContent()) {
                            Platform.runLater(() -> autoConnectNewTab(st));
                        }
                    }
                    for (javafx.scene.control.Tab t : c.getRemoved()) {
                        if (t instanceof app.ui.editor.SqlEditorTab st && resultsController != null) {
                            // later: a pinned tab is removed and added back when it moves
                            Platform.runLater(() -> {
                                if (!editorController.getPrimaryTabPane().getTabs().contains(st)) {
                                    resultsController.dropTabState(st.getDocumentId());
                                    pendingTabOutput.remove(st.getDocumentId());
                                }
                            });
                        }
                    }
                }
            });
            queries.runningTabs().addListener((javafx.collections.SetChangeListener<String>) c -> refreshRunState());
            // the Explorer connected (or a tab did): the tab on screen follows at once, the others when shown
            app.services.ExplorerConnectionState.getInstance().revisionProperty().addListener((o, a, b) ->
                    autoConnectNewTab(editorController.getCurrentEditorTab()));
            // a dead session is noticed without a run: active tab only, every 30 s and when the window gets focus
            var heartbeat = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    javafx.util.Duration.seconds(30), e -> probeActiveTab()));
            heartbeat.setCycleCount(javafx.animation.Animation.INDEFINITE);
            heartbeat.play();
            hookWindowFocus(editorController.getPrimaryTabPane());
        }
        GlobalDesktopState.getInstance().activeConnectionProperty()
                .addListener((o, a, b) -> Platform.runLater(refreshConnected));

        statusBarController.setConnection(connectionComboBox != null ? connectionComboBox.getValue() : null);
        refreshConnected.run();
        applyStatusBarEditorSettings(app.services.SettingsService.getInstance().getCurrentSettings());

        // Keyboard: Ctrl+B toggles the Explorer (VS Code), Ctrl+= / Ctrl+- / Ctrl+0 zoom the editor.
        if (mainWindowRoot != null) {
            mainWindowRoot.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                if (!e.isShortcutDown() || e.isAltDown()) return;
                switch (e.getCode()) {
                    case B -> {
                        if (!e.isShiftDown()) { handleToggleSidebar(); e.consume(); }
                    }
                    case EQUALS, PLUS, ADD -> { statusBarController.stepZoom(1); e.consume(); }
                    case MINUS, SUBTRACT -> { statusBarController.stepZoom(-1); e.consume(); }
                    case DIGIT0, NUMPAD0 -> {
                        if (!e.isShiftDown()) { statusBarController.changeZoom(100); e.consume(); }
                    }
                    default -> { }
                }
            });
        }
    }

    /** Editor bar: TABS → SPACES: 4, SPACES → TABS (saved in Settings, applied to the editor at once). */
    private void toggleIndentation() {
        var service = app.services.SettingsService.getInstance();
        var settings = service.getCurrentSettings();
        if (settings == null || settings.getEditor() == null) return;
        var copy = settings.clone();
        boolean toSpaces = !copy.getEditor().isInsertSpaces();
        copy.getEditor().setInsertSpaces(toSpaces);
        if (toSpaces) copy.getEditor().setTabSize(4);
        service.saveSettings(copy);
    }

    private void applyStatusBarEditorSettings(app.models.IdeSettings s) {
        if (statusBarController == null || s == null) return;
        boolean spaces = s.getEditor() == null || s.getEditor().isInsertSpaces();
        statusBarController.setIndentation(spaces, s.getTabSize());
    }

    /** Status bar zoom scales the editor font size from Settings (UI only). */
    private void applyEditorZoom() {
        if (statusBarController == null || editorController == null) return;
        var host = editorController.getMonacoHost();
        if (host == null) return;
        int base = app.services.SettingsService.getInstance().getCurrentSettings().getEditorFontSize();
        host.setFontSize(base);
        host.setZoom(statusBarController.getZoom());
    }

    private void bindWindowStateToRoot() {
        Stage stage = AppContext.getInstance().getPrimaryStage();
        if (stage != null && mainWindowRoot != null) {
            stage.maximizedProperty().addListener((obs, oldVal, isMax) -> {
                mainWindowRoot.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("maximized"), isMax);
                if (isMax) {
                    if (!mainWindowRoot.getStyleClass().contains("window-maximized")) {
                        mainWindowRoot.getStyleClass().add("window-maximized");
                    }
                } else {
                    mainWindowRoot.getStyleClass().remove("window-maximized");
                }
            });
            boolean isMax = stage.isMaximized();
            mainWindowRoot.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("maximized"), isMax);
            if (isMax) {
                mainWindowRoot.getStyleClass().add("window-maximized");
            }
        }
    }

    private void setupSidebarIntegration() {
        // Double-click on a table/view is handled by SidebarController.handleNodeAction (SCR-MAIN-002):
        // it opens the SELECT in a new tab and only executes it when mock metadata is off (Gate D).
    }

    private void setupEditorIntegration() {
        if (editorController != null) {
            editorController.setOnExecuteRequestListener(this::handleExecute);
            editorController.setOnExecuteStatementRequestListener(this::handleExecuteStatement);
            editorController.setOnReloadRequested(this::handleRefreshObjects);
            // Phase 3: tabs without their own connection follow the toolbar; banner actions
            editorController.setActiveConnectionSupplier(() -> connectionComboBox != null ? connectionComboBox.getValue() : null);
            app.ui.editor.SqlEditorTab.setBannerActions(new app.ui.editor.SqlEditorTab.BannerActions() {
                @Override
                public void connect(app.ui.editor.SqlEditorTab tab) {
                    connectTab(tab);
                }

                @Override
                public void changeConnection(app.ui.editor.SqlEditorTab tab) {
                    if (connectionComboBox != null) {
                        connectionComboBox.requestFocus();
                        connectionComboBox.show();
                    }
                }
            });
            // Apply initial settings to editor
            editorController.applyEditorSettings(app.services.SettingsService.getInstance().getCurrentSettings());
        }
        app.services.SettingsService.getInstance().addSettingsListener(settings -> {
            applySettingsToUi(settings);
        });
        if (editorController != null && statusBarController != null) {
            editorController.setCursorReporter(statusBarController::setCursorPosition);

            editorController.getPrimaryTabPane().getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab instanceof app.ui.editor.SqlEditorTab sqlTab) {
                    statusBarController.setTabInfo(sqlTab.getTabTitle());
                    app.ui.titlebar.WindowTitleModel.getInstance().setDocumentTitle(sqlTab.getTabTitle());
                    String title = sqlTab.getTabTitle().toLowerCase();
                    String lang = "SQL";
                    if (title.endsWith(".json")) lang = "JSON";
                    else if (title.endsWith(".xml")) lang = "XML";
                    else if (title.endsWith(".txt")) lang = "Plain Text";
                    statusBarController.setLanguageMode(lang);
                    statusBarController.setEditorInfoVisible(true);
                    String text = editorController.getActiveText();
                    statusBarController.setLineSeparator(text != null && text.contains("\r\n") ? "CRLF" : "LF");

                    if (sqlTab.getConnectionName() != null && !sqlTab.getConnectionName().isBlank()) {
                        syncConnectionToToolbar(sqlTab.getConnectionName());
                    }
                    if (sqlTab.getDatabaseName() != null && !sqlTab.getDatabaseName().isBlank()) {
                        syncDatabaseToToolbar(sqlTab.getDatabaseName());
                    }
                    if (sqlTab.getSchemaName() != null && !sqlTab.getSchemaName().isBlank()) {
                        syncSchemaToToolbar(sqlTab.getSchemaName());
                    }
                } else {
                    app.ui.titlebar.WindowTitleModel.getInstance().setDocumentTitle("");
                    statusBarController.setTabInfo("");
                    statusBarController.setEditorInfoVisible(false);
                }
            });
        }
    }

    /**
     * Phase 8 / D1: opens the tab's own session (banner "Kết nối"). Asks for the password first when
     * the profile needs it (DLG-002), then connects in the background; errors go to a toast.
     */
    public void connectTab(app.ui.editor.SqlEditorTab tab) {
        if (tab == null) return;
        String name = tab.getConnectionName() != null ? tab.getConnectionName()
                : (connectionComboBox != null ? connectionComboBox.getValue() : null);
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.CONNECTIONS)) {
            String id = name != null ? connectionIds.get(name) : null;
            if (id != null && sidebarController != null) sidebarController.openConnectionById(id);
            return;
        }
        var profile = app.framework.session.TabSessionManager.profileByName(name);
        if (profile == null) {
            app.services.NotificationService.getInstance().showWarning("Kết nối", "Tab chưa chọn kết nối.");
            return;
        }
        if (tab.getConnectionName() == null) tab.setConnectionName(profile.name());
        var policy = app.services.PasswordPolicyService.getInstance();
        if (policy.needsPrompt(profile)) {
            String target = (profile.username() != null ? profile.username() + " @ " : "")
                    + (profile.host() != null ? profile.host() : "") + (profile.port() > 0 ? ":" + profile.port() : "");
            var answer = app.ui.dialogs.PasswordPromptDialog.show(ownerWindow(), profile.name(), target, policy.isExpired(profile.id()));
            if (answer.isEmpty()) return;
            String err = policy.applyPrompt(profile, answer.get().password(), answer.get().save());
            if (err != null) app.services.NotificationService.getInstance().showWarning("Mật khẩu", err);
        }
        String db = tab.getDatabaseName() != null && !tab.getDatabaseName().isBlank()
                ? tab.getDatabaseName() : profile.databaseName();
        if (db != null && !db.isBlank()) {
            tab.setDatabaseName(db);
            if (editorController != null && tab == editorController.getCurrentEditorTab()) {
                syncDatabaseToToolbar(db);
                GlobalDesktopState.getInstance().setActiveCatalog(db);
            }
        }
        String schema = tab.getSchemaName() != null && !tab.getSchemaName().isBlank()
                ? tab.getSchemaName() : app.services.DefaultSchemaService.get(profile.id());
        if (schema == null && profile.properties() != null) {
            schema = profile.properties().get("defaultSchema");
            if (schema == null) schema = profile.properties().get("schema");
        }
        if (schema != null && !schema.isBlank()) {
            tab.setSchemaName(schema);
            if (editorController != null && tab == editorController.getCurrentEditorTab()) {
                syncSchemaToToolbar(schema);
                GlobalDesktopState.getInstance().setActiveSchema(schema);
            }
        }
        app.framework.session.TabSessionManager.getInstance().connectAsync(tab.getDocumentId(), profile, db, res -> {
            if (res.isFailure()) {
                app.services.NotificationService.getInstance().showError("Không kết nối được " + profile.name(),
                        res.getError().map(e -> e.message()).orElse("Lỗi không xác định"));
            } else if (statusBarController != null) {
                statusBarController.setStatus("Tab đã kết nối " + profile.name() + (db != null ? " · " + db : ""), "online");
            }
        });
    }

    /**
     * A new query tab on a connection that is already open (Explorer connected, or another tab has a live
     * session on it) gets its own session at once, so it does not show "Chưa kết nối" (LLTool opens the tab's
     * session with the tab). Never asks for a password: a connection that needs one waits for "Kết nối" / F5.
     */
    private void autoConnectNewTab(app.ui.editor.SqlEditorTab tab) {
        if (tab == null || editorController == null || !editorController.getPrimaryTabPane().getTabs().contains(tab)) return;
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.CONNECTIONS)) return;
        var sessions = app.framework.session.TabSessionManager.getInstance();
        if (sessions.state(tab.getDocumentId()) != app.framework.session.TabSession.State.NONE) return;
        String name = tab.getConnectionName() != null && !tab.getConnectionName().isBlank() ? tab.getConnectionName()
                : (connectionComboBox != null && connectionComboBox.getValue() != null && !connectionComboBox.getValue().isBlank()
                    ? connectionComboBox.getValue()
                    : GlobalDesktopState.getInstance().getActiveConnection());
        var profile = app.framework.session.TabSessionManager.profileByName(name);
        if (profile == null) return;
        boolean open = app.services.ExplorerConnectionState.getInstance().get(profile.id())
                == app.services.ExplorerConnectionState.Status.CONNECTED;
        if (!open) {
            String activeConn = GlobalDesktopState.getInstance().getActiveConnection();
            if (activeConn != null && (activeConn.equalsIgnoreCase(profile.name()) || activeConn.equalsIgnoreCase(profile.id()))) {
                open = true;
            }
        }
        if (!open) {
            for (var other : editorController.getOpenTabs()) {
                var s = sessions.get(other.getDocumentId());
                if (other != tab && s != null && s.state() == app.framework.session.TabSession.State.CONNECTED
                        && s.profile() != null && profile.id().equals(s.profile().id())) {
                    open = true;
                    break;
                }
            }
        }
        if (!open || app.services.PasswordPolicyService.getInstance().needsPrompt(profile)) return;
        if (tab.getConnectionName() == null || tab.getConnectionName().isBlank()) tab.setConnectionName(profile.name());
        String db = tab.getDatabaseName() != null && !tab.getDatabaseName().isBlank()
                ? tab.getDatabaseName() : profile.databaseName();
        if (db != null && !db.isBlank()) {
            tab.setDatabaseName(db);
            if (editorController != null && tab == editorController.getCurrentEditorTab()) {
                syncDatabaseToToolbar(db);
                GlobalDesktopState.getInstance().setActiveCatalog(db);
            }
        }
        String schema = tab.getSchemaName() != null && !tab.getSchemaName().isBlank()
                ? tab.getSchemaName() : app.services.DefaultSchemaService.get(profile.id());
        if (schema == null) {
            schema = app.services.DefaultSchemaService.get(profile.name());
        }
        if (schema == null && profile.properties() != null) {
            schema = profile.properties().get("schema");
            if (schema == null) {
                schema = profile.properties().get("defaultSchema");
            }
        }
        if (schema != null && !schema.isBlank()) {
            tab.setSchemaName(schema);
            if (editorController != null && tab == editorController.getCurrentEditorTab()) {
                syncSchemaToToolbar(schema);
                GlobalDesktopState.getInstance().setActiveSchema(schema);
            }
        }
        sessions.connectAsync(tab.getDocumentId(), profile, db, res -> {
            if (res.isFailure()) {
                log.info("Auto-connect of new tab to {} failed: {}", profile.name(), res.getError().map(e -> e.message()).orElse("?"));
            }
            editorController.refreshTabDecorations();
        });
    }

    private void probeActiveTab() {
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.CONNECTIONS)) return;
        String id = activeTabId();
        if (id != null) app.framework.session.TabSessionManager.getInstance().probeAsync(id);
    }

    private void hookWindowFocus(javafx.scene.Node node) {
        java.util.function.Consumer<javafx.stage.Window> onWindow = w -> {
            if (w != null) w.focusedProperty().addListener((o, a, focused) -> {
                if (focused) probeActiveTab();
            });
        };
        java.util.function.Consumer<javafx.scene.Scene> onScene = sc -> {
            if (sc == null) return;
            if (sc.getWindow() != null) onWindow.accept(sc.getWindow());
            else sc.windowProperty().addListener((o, a, w) -> onWindow.accept(w));
        };
        if (node.getScene() != null) onScene.accept(node.getScene());
        else node.sceneProperty().addListener((o, a, sc) -> onScene.accept(sc));
    }

    /**
     * Before a run: a tab whose session is not open (never opened, closed by an Explorer disconnect, or lost)
     * asks the user to confirm server / database / schema first (user decision 2026-11-06); an open
     * (or opening) session runs at once. Then DLG-002 when the profile needs a password.
     */
    private boolean ensureSessionForRun(String tabId, String connectionName, String database, String schema) {
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.QUERY)) return true;
        var st = app.framework.session.TabSessionManager.getInstance().state(tabId);
        if (st == app.framework.session.TabSession.State.CONNECTED || st == app.framework.session.TabSession.State.CONNECTING) {
            return true;
        }
        var profile = app.framework.session.TabSessionManager.profileByName(connectionName);
        if (profile == null) return true; // the run reports "no connection"
        if (!confirmConnectForRun(profile, st == app.framework.session.TabSession.State.LOST, database, schema)) return false;
        return ensurePasswordForRun(tabId, connectionName);
    }

    private boolean confirmConnectForRun(com.lltool.core.application.connection.dto.ConnectionProfileDto profile,
                                         boolean lost, String database, String schema) {
        String server;
        if (profile.host() != null && !profile.host().isBlank()) {
            server = profile.host() + (profile.port() > 0 ? ":" + profile.port() : "");
        } else {
            server = profile.sqliteFilePath() != null ? profile.sqliteFilePath() : "";
        }
        if (profile.databaseType() != null) server += "  (" + profile.databaseType() + ")";
        String db = database != null && !database.isBlank() ? database : profile.databaseName();
        String sch = schema != null && !schema.isBlank() ? schema : app.services.DefaultSchemaService.get(profile.id());
        return app.ui.dialogs.ConnectForRunDialog.show(ownerWindow(), lost, profile.name(), server, db, sch);
    }

    /** The run found the session dead: confirm, reconnect and run the same statement again. */
    private void retryAfterLostConnection(String tabId, String tabTitle, String sql, String tabConn, String tabDb,
                                          String tabSchema, boolean prodConfirmed,
                                          app.framework.query.QueryOptions queryOptions) {
        if (!isActiveTab(tabId)) return;
        var profile = app.framework.session.TabSessionManager.profileByName(tabConn);
        if (profile == null || !confirmConnectForRun(profile, true, tabDb, tabSchema)) return;
        if (!ensurePasswordForRun(tabId, tabConn)) return;
        setExecutionRunningState(true);
        startRun(tabId, tabTitle, sql, tabConn, tabDb, tabSchema, prodConfirmed, queryOptions);
    }

    /** DLG-002 before a run opens the tab's session; false = the user cancelled. */
    private boolean ensurePasswordForRun(String tabId, String connectionName) {
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.QUERY)) return true;
        var session = app.framework.session.TabSessionManager.getInstance().get(tabId);
        if (session != null && session.state() == app.framework.session.TabSession.State.CONNECTED) return true;
        var profile = app.framework.session.TabSessionManager.profileByName(connectionName);
        var policy = app.services.PasswordPolicyService.getInstance();
        if (profile == null || !policy.needsPrompt(profile)) return true;
        String target = (profile.username() != null ? profile.username() + " @ " : "")
                + (profile.host() != null ? profile.host() : "") + (profile.port() > 0 ? ":" + profile.port() : "");
        var answer = app.ui.dialogs.PasswordPromptDialog.show(ownerWindow(), profile.name(), target, policy.isExpired(profile.id()));
        if (answer.isEmpty()) return false;
        String err = policy.applyPrompt(profile, answer.get().password(), answer.get().save());
        if (err != null) app.services.NotificationService.getInstance().showWarning("Mật khẩu", err);
        return true;
    }

    public void syncConnectionToToolbar(String connectionName) {
        if (connectionComboBox == null || connectionName == null || connectionName.isBlank()) return;
        if (!connectionComboBox.getItems().contains(connectionName)) {
            connectionComboBox.getItems().add(connectionName);
        }
        connectionComboBox.getSelectionModel().select(connectionName);
        GlobalDesktopState.getInstance().setActiveConnection(connectionName);
        if (statusBarController != null) {
            statusBarController.setConnection(connectionName);
        }
        var profile = app.framework.session.TabSessionManager.profileByName(connectionName);
        if (profile != null) {
            var activeTab = editorController != null ? editorController.getCurrentEditorTab() : null;
            String defaultDb = profile.databaseName();
            if (activeTab != null) {
                activeTab.setConnectionName(connectionName);
                String targetDb = activeTab.getDatabaseName() != null && !activeTab.getDatabaseName().isBlank()
                        ? activeTab.getDatabaseName() : defaultDb;
                if (targetDb != null && !targetDb.isBlank()) {
                    activeTab.setDatabaseName(targetDb);
                    syncDatabaseToToolbar(targetDb);
                    GlobalDesktopState.getInstance().setActiveCatalog(targetDb);
                } else {
                    if (databaseComboBox != null) {
                        databaseComboBox.getSelectionModel().clearSelection();
                        databaseComboBox.setValue(null);
                    }
                    GlobalDesktopState.getInstance().setActiveCatalog("");
                }
            } else if (defaultDb != null && !defaultDb.isBlank()) {
                syncDatabaseToToolbar(defaultDb);
                GlobalDesktopState.getInstance().setActiveCatalog(defaultDb);
            } else {
                if (databaseComboBox != null) {
                    databaseComboBox.getSelectionModel().clearSelection();
                    databaseComboBox.setValue(null);
                }
                GlobalDesktopState.getInstance().setActiveCatalog("");
            }
            String defaultSchema = app.services.DefaultSchemaService.get(profile.id());
            if (defaultSchema == null) {
                defaultSchema = app.services.DefaultSchemaService.get(profile.name());
            }
            if (defaultSchema == null && profile.properties() != null) {
                defaultSchema = profile.properties().get("schema");
                if (defaultSchema == null) {
                    defaultSchema = profile.properties().get("defaultSchema");
                }
            }
            if (defaultSchema != null && !defaultSchema.isBlank()) {
                syncSchemaToToolbar(defaultSchema);
                GlobalDesktopState.getInstance().setActiveSchema(defaultSchema);
                if (activeTab != null) {
                    activeTab.setSchemaName(defaultSchema);
                }
            }
        }
    }

    public void setConnectionReadOnly(String connectionName, boolean readOnly) {
        if (connectionName == null) return;
        connectionReadOnly.put(connectionName, readOnly);
        if (!readOnly) {
            app.framework.backend.BackendFlags.setReadOnlyAll(false);
        }
        app.framework.session.TabSessionManager.getInstance().setProfileReadOnly(connectionName, readOnly);
        if (editorController != null) {
            for (var t : editorController.getOpenTabs()) {
                if (connectionName.equalsIgnoreCase(t.getConnectionName())) {
                    var session = app.framework.session.TabSessionManager.getInstance().get(t.getDocumentId());
                    if (session != null) {
                        session.setReadOnly(readOnly);
                    }
                }
            }
            editorController.refreshTabDecorations();
        }
        if (statusBarController != null) {
            statusBarController.setConnection(connectionName);
        }
    }

    public void syncSchemaToToolbar(String schemaName) {
        if (schemaComboBox == null || schemaName == null || schemaName.isBlank()) return;
        if (!schemaComboBox.getItems().contains(schemaName)) {
            schemaComboBox.getItems().add(schemaName);
        }
        schemaComboBox.getSelectionModel().select(schemaName);
    }

    private void setupToolbarData() {
        if (connectionComboBox != null) {
            refreshConnectionDropdown();
            connectionComboBox.setOnAction(e -> {
                String selected = connectionComboBox.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    GlobalDesktopState.getInstance().setActiveConnection(selected);
                    var tab = editorController != null ? editorController.getCurrentEditorTab() : null;
                    if (tab != null && !selected.equals(tab.getConnectionName())) {
                        tab.setConnectionName(selected);
                        // Phase 8 / D1: the tab switched server: its old session is closed,
                        // the new one opens on "Kết nối" or on the next run
                        app.framework.session.TabSessionManager.getInstance().disconnect(tab.getDocumentId());
                    }
                    var profile = app.framework.session.TabSessionManager.profileByName(selected);
                    if (profile != null) {
                        String defaultDb = profile.databaseName();
                        if (defaultDb != null && !defaultDb.isBlank()) {
                            if (tab != null) tab.setDatabaseName(defaultDb);
                            syncDatabaseToToolbar(defaultDb);
                            GlobalDesktopState.getInstance().setActiveCatalog(defaultDb);
                        } else if (tab != null && tab.getDatabaseName() != null && !tab.getDatabaseName().isBlank()) {
                            syncDatabaseToToolbar(tab.getDatabaseName());
                            GlobalDesktopState.getInstance().setActiveCatalog(tab.getDatabaseName());
                        } else {
                            if (databaseComboBox != null) {
                                databaseComboBox.getSelectionModel().clearSelection();
                                databaseComboBox.setValue(null);
                            }
                            GlobalDesktopState.getInstance().setActiveCatalog("");
                        }
                        String defSchema = app.services.DefaultSchemaService.get(profile.id());
                        if (defSchema == null) {
                            defSchema = app.services.DefaultSchemaService.get(profile.name());
                        }
                        if (defSchema == null && profile.properties() != null) {
                            defSchema = profile.properties().get("schema");
                            if (defSchema == null) {
                                defSchema = profile.properties().get("defaultSchema");
                            }
                        }
                        if (defSchema != null && !defSchema.isBlank()) {
                            if (tab != null) tab.setSchemaName(defSchema);
                            syncSchemaToToolbar(defSchema);
                            GlobalDesktopState.getInstance().setActiveSchema(defSchema);
                        }
                    }
                    if (editorController != null) {
                        editorController.refreshTabDecorations();
                    }
                    if (statusBarController != null) {
                        statusBarController.setConnection(selected);
                    }
                }
            });

            // Reactively sync with GlobalDesktopState activeConnection
            GlobalDesktopState.getInstance().activeConnectionProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    syncConnectionToToolbar(newVal);
                }
            });
        }

        if (schemaComboBox != null) {
            // Phase 8 / D2: real schemas of the tab's connection + database, loaded when the list opens
            schemaComboBox.setOnShowing(e -> {
                var profile = app.framework.session.TabSessionManager.profileByName(currentTabConnection());
                if (profile == null) return;
                String db = databaseComboBox != null ? databaseComboBox.getValue() : null;
                app.framework.metadata.DesktopMetadataService.getInstance().fetchSchemas(profile,
                        db != null ? db : profile.databaseName(), list -> setItemsKeepValue(schemaComboBox, list));
            });
            schemaComboBox.setOnAction(e -> {
                String selected = schemaComboBox.getValue();
                var tab = editorController != null ? editorController.getCurrentEditorTab() : null;
                if (tab != null && selected != null) tab.setSchemaName(selected);
                if (selected != null && !selected.isBlank() && !selected.equals(GlobalDesktopState.getInstance().getActiveSchema())) {
                    GlobalDesktopState.getInstance().setActiveSchema(selected);
                }
            });
            GlobalDesktopState.getInstance().activeSchemaProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    syncSchemaToToolbar(newVal);
                    var tab = editorController != null ? editorController.getCurrentEditorTab() : null;
                    if (tab != null && !newVal.equals(tab.getSchemaName())) {
                        tab.setSchemaName(newVal);
                    }
                }
            });
        }
    }

    private String currentTabConnection() {
        var tab = editorController != null ? editorController.getCurrentEditorTab() : null;
        if (tab != null && tab.getConnectionName() != null) return tab.getConnectionName();
        return connectionComboBox != null ? connectionComboBox.getValue() : null;
    }

    private static void setItemsKeepValue(ComboBox<String> combo, java.util.List<String> values) {
        String current = combo.getValue();
        combo.getItems().setAll(values);
        if (current != null && values.contains(current)) combo.setValue(current);
    }

    public void refreshConnectionDropdown() {
        if (connectionComboBox == null) return;
        try {
            var res = DesktopBootstrap.getInstance().getConnectionApi().getConnections();
            if (res.isSuccess() && !res.getDataOrThrow().isEmpty()) {
                connectionEnvironments.clear();
                for (ConnectionProfileDto p : res.getDataOrThrow()) {
                    if (p.name() != null) {
                        connectionEnvironments.put(p.name(), p.environment());
                        connectionTypes.put(p.name(), p.databaseType());
                        connectionDefaultDatabases.put(p.name(), p.databaseName());
                        connectionIds.put(p.name(), p.id());
                        connectionReadOnly.put(p.name(), p.readOnly());
                    }
                }
                var names = res.getDataOrThrow().stream()
                        .map(ConnectionProfileDto::name)
                        .toList();
                connectionComboBox.setItems(FXCollections.observableArrayList(names));
                String active = GlobalDesktopState.getInstance().getActiveConnection();
                if (active != null && names.contains(active)) {
                    connectionComboBox.getSelectionModel().select(active);
                } else {
                    connectionComboBox.getSelectionModel().selectFirst();
                }
            } else {
                connectionComboBox.setItems(FXCollections.observableArrayList(
                    "PostgreSQL: dev-local @ localhost:5432",
                    "SQLite: embedded @ memory:test.db"
                ));
                connectionComboBox.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {
            connectionComboBox.setItems(FXCollections.observableArrayList(
                "PostgreSQL: dev-local @ localhost:5432"
            ));
            connectionComboBox.getSelectionModel().selectFirst();
        }
    }

    @FXML
    public void handleManageConnections() {
        Stage stage = AppContext.getInstance().getPrimaryStage();
        String connectId = ConnectionManagerDialog.show(stage);
        refreshConnectionDropdown();
        if (sidebarController != null) {
            sidebarController.loadData();
            if (connectId != null) sidebarController.openConnectionById(connectId);
        }
    }

    /** Modern VS Code style title bar: menus on the left, search in center, layout toggles on the right. */
    private void setupTitleBarContent() {
        if (titleBarController == null) return;
        if (mainMenuBar != null) {
            if (mainMenuBar.getParent() instanceof javafx.scene.layout.Pane parent) {
                parent.getChildren().remove(mainMenuBar);
            }
            titleBarController.setMenuBar(mainMenuBar);
        }
        if (searchBox != null) {
            titleBarController.setSearchBox(searchBox);
        }
        if (layoutActions != null) {
            titleBarController.setCustomActions(layoutActions);
        }
    }

    private void setupThemeMenu() {
        if (themesMenu != null) {
            themesMenu.getItems().clear();
            ToggleGroup group = new ToggleGroup();
            // T1/T4: dark groups then light ones (user decision: themes split by dark / light); inside a kind the
            // built-in themes, then each imported VS Code package ("Tối · GitHub Theme") - same groups as Settings
            var current = ThemeService.getInstance().getCurrentDefinition();
            for (var g : app.services.theme.ThemeRegistry.getInstance().grouped()) {
                javafx.scene.control.MenuItem header = new javafx.scene.control.MenuItem(g.title());
                header.setDisable(true);
                header.getStyleClass().add("theme-group-header");
                if (!themesMenu.getItems().isEmpty()) themesMenu.getItems().add(new SeparatorMenuItem());
                themesMenu.getItems().add(header);
                for (var def : g.themes()) {
                    RadioMenuItem item = new RadioMenuItem(def.label());
                    item.setToggleGroup(group);
                    item.setSelected(def.id().equals(current.id()));
                    item.setOnAction(e -> {
                        Stage stage = AppContext.getInstance().getPrimaryStage();
                        if (stage != null && stage.getScene() != null) {
                            ThemeService.getInstance().apply(def, stage.getScene());
                        }
                    });
                    themesMenu.getItems().add(item);
                }
            }
            // T3: themes of a VS Code extension (.vsix) become app themes
            javafx.scene.control.MenuItem importItem = new javafx.scene.control.MenuItem("Import theme VS Code (.vsix)…");
            importItem.setOnAction(e -> handleImportVsixTheme());
            themesMenu.getItems().addAll(new SeparatorMenuItem(), importItem);
        }
    }

    /**
     * T4: the list of themes changed (import / removal, from the menu or Settings): rebuild the menu; when the
     * theme in use was removed, fall back to Primer of the same kind (the listener below saves it in Settings).
     */
    private void onThemeListChanged() {
        Platform.runLater(() -> {
            var registry = app.services.theme.ThemeRegistry.getInstance();
            var current = ThemeService.getInstance().getCurrentDefinition();
            if (current != null && !current.isBuiltIn() && registry.findOrNull(current.id()) == null) {
                Stage stage = AppContext.getInstance().getPrimaryStage();
                if (stage != null && stage.getScene() != null) {
                    ThemeService.getInstance().apply(app.services.theme.ThemeDefinition.builtIn(current.isDark()
                            ? ThemeService.AppTheme.PRIMER_DARK : ThemeService.AppTheme.PRIMER_LIGHT), stage.getScene());
                    return; // the theme listener rebuilds the menu
                }
            }
            setupThemeMenu();
        });
    }

    private void setupThemeListener() {
        app.services.theme.ThemeRegistry.getInstance().addChangeListener(this::onThemeListChanged);
        // T1: follows the chosen theme (built in or imported), not only the AtlantaFX base under it
        ThemeService.getInstance().currentDefinitionProperty().addListener((obs, oldDef, def) -> {
            updateThemeIcons(def.isDark());
            if (editorController != null && editorController.getMonacoHost() != null) {
                editorController.getMonacoHost().applyAppTheme(); // the editor follows the theme
            }
            app.ui.titlebar.WindowTitleModel.getInstance().setThemeName(def.label());
            app.services.NotificationService.getInstance().showInfo("Theme Changed", "Active theme: " + def.label());
            setupThemeMenu();
            // a theme picked from the menu / title bar is the user's setting too; Settings keep the theme id
            var settings = app.services.SettingsService.getInstance().getCurrentSettings();
            if (settings != null && !def.id().equalsIgnoreCase(settings.getTheme())) {
                var copy = settings.clone();
                copy.setTheme(def.id());
                app.services.SettingsService.getInstance().saveSettings(copy);
            }
        });
        var def = ThemeService.getInstance().getCurrentDefinition();
        updateThemeIcons(def.isDark());
        app.ui.titlebar.WindowTitleModel.getInstance().setThemeName(def.label());
        if (editorController != null && editorController.getMonacoHost() != null) {
            editorController.getMonacoHost().applyAppTheme(); // first frame: was always the dark editor theme
        }
    }

    /** T3: picks a .vsix and imports its themes in the background; the menu follows the registry (T4). */
    public void handleImportVsixTheme() {
        app.ui.theme.ThemeImportActions.importVsix(ownerWindow(), null);
    }

    private void updateThemeIcons(boolean dark) {
        if (themeToggleIcon != null) {
            themeToggleIcon.setIconLiteral(dark ? "fth-sun" : "fth-moon");
        }
    }

    public HBox getTitleBarNode() {
        return titleBar;
    }

    /** See {@link EditorController#whenEditorReady}. */
    public void whenEditorReady(Runnable callback) {
        editorController.whenEditorReady(callback);
    }

    public app.ui.titlebar.TitleBarController getTitleBarController() {
        return titleBarController;
    }

    // Quick Actions
    @FXML
    public void handleNewTab() {
        if (editorController != null) {
            editorController.handleNewTab();
        }
    }

    @FXML
    public void handleFormatSql() {
        if (editorController != null) {
            editorController.handleFormatSql();
            if (statusBarController != null) {
                statusBarController.setStatus("SQL formatted successfully", "online");
            }
            app.services.NotificationService.getInstance().showSuccess("SQL Formatter", "SQL statements formatted successfully.");
        }
    }

    @FXML
    public void handleSplitRight() {
        if (editorController != null) {
            editorController.handleSplitRight();
        }
    }

    @FXML
    public void handleSplitDown() {
        if (editorController != null) {
            editorController.handleSplitDown();
        }
    }

    @FXML
    public void handleToggleTheme() {
        Stage stage = AppContext.getInstance().getPrimaryStage();
        if (stage == null && mainWindowRoot != null && mainWindowRoot.getScene() != null) {
            stage = (Stage) mainWindowRoot.getScene().getWindow();
        }
        if (stage != null && stage.getScene() != null) {
            // menu, editor and Settings follow through the theme listener (setupThemeListener)
            ThemeService.getInstance().toggleDarkLight(stage.getScene());
        }
    }

    // ------------------------------------------------------------------ title bar extras (SCR-SHELL-001)

    private void setupTitleBarExtras() {
        // Environment badge follows the active connection.
        GlobalDesktopState.getInstance().activeConnectionProperty()
            .addListener((obs, oldVal, newVal) -> updateEnvironmentBadge(newVal));
        updateEnvironmentBadge(connectionComboBox != null ? connectionComboBox.getValue() : null);
        setupDatabaseSelector();

        if (titleBarController != null) {
            titleBarController.setOnUpdateRequested(this::handleCheckUpdates);
        }

        // Alt+[ / Alt+] (bracket keys cannot be expressed reliably in FXML accelerator strings).
        if (menuGotoBegin != null) {
            menuGotoBegin.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.OPEN_BRACKET, javafx.scene.input.KeyCombination.ALT_DOWN));
        }
        if (menuGotoEnd != null) {
            menuGotoEnd.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.CLOSE_BRACKET, javafx.scene.input.KeyCombination.ALT_DOWN));
        }

        // Auto-hide menu bar: a lone Alt press toggles the menus (VS Code "menuBarVisibility: toggle").
        if (mainWindowRoot != null) {
            final boolean[] altAlone = {false};
            mainWindowRoot.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e ->
                altAlone[0] = e.getCode() == javafx.scene.input.KeyCode.ALT);
            mainWindowRoot.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, e -> {
                if (autoHideMenuBar && altAlone[0] && e.getCode() == javafx.scene.input.KeyCode.ALT
                        && titleBarController != null) {
                    titleBarController.setMenuBarHidden(!titleBarController.isMenuBarHidden());
                }
                altAlone[0] = false;
            });
        }
    }

    private void updateEnvironmentBadge(String connectionName) {
        String env = connectionName != null ? connectionEnvironments.get(connectionName) : null;
        // The environment badge is shown in the editor context bar only (title bar badge removed on request).
        if (titleBarController != null) {
            titleBarController.setEnvironment(null);
        }
        app.ui.titlebar.TitleBarController.applyEnvironmentBadge(toolbarEnvBadge, env);
        if (connectionTypeIcon != null) {
            String type = connectionName != null ? connectionTypes.get(connectionName) : null;
            String t = type == null ? "" : type.toUpperCase();
            connectionTypeIcon.setIconLiteral(t.contains("SQLITE") ? "fth-database" : "fth-server");
        }
        if (databaseComboBox != null && connectionName != null) {
            String tabDb = null;
            if (editorController != null && editorController.getCurrentEditorTab() != null) {
                tabDb = editorController.getCurrentEditorTab().getDatabaseName();
            }
            if (tabDb == null || tabDb.isBlank()) {
                tabDb = connectionDefaultDatabases.get(connectionName);
            }
            if (tabDb != null && !tabDb.isBlank()) {
                syncDatabaseToToolbar(tabDb);
            } else {
                databaseComboBox.getSelectionModel().clearSelection();
                databaseComboBox.setValue(null);
            }
        }
    }

    /** Database selector of the context bar (Phase 8 / D2: real list, per tab). */
    private void setupDatabaseSelector() {
        if (databaseComboBox == null) return;
        GlobalDesktopState state = GlobalDesktopState.getInstance();
        String current = state.getActiveCatalog();
        if (current != null && !current.isBlank()) {
            databaseComboBox.getItems().setAll(current);
            databaseComboBox.getSelectionModel().select(current);
        }
        // Phase 8 / D2: the list comes from the server; choosing a database changes the CURRENT TAB's database
        // (its session switches catalog on the next run / connect)
        databaseComboBox.setOnShowing(e -> {
            var profile = app.framework.session.TabSessionManager.profileByName(currentTabConnection());
            if (profile == null) return;
            app.framework.metadata.DesktopMetadataService.getInstance().fetchCatalogs(profile,
                    list -> setItemsKeepValue(databaseComboBox, list));
        });
        databaseComboBox.setOnAction(e -> {
            String selected = databaseComboBox.getValue();
            var tab = editorController != null ? editorController.getCurrentEditorTab() : null;
            if (tab != null && selected != null && !selected.equals(tab.getDatabaseName())) {
                tab.setDatabaseName(selected);
                tab.setSchemaName(null);
                if (schemaComboBox != null) schemaComboBox.getItems().clear();
                if (editorController != null) editorController.refreshTabDecorations();
            }
            if (selected != null && !selected.equals(state.getActiveCatalog())) {
                state.setActiveCatalog(selected);
            }
        });
        state.activeCatalogProperty().addListener((obs, o, n) -> {
            if (n != null && !n.isBlank()) {
                syncDatabaseToToolbar(n);
                var tab = editorController != null ? editorController.getCurrentEditorTab() : null;
                if (tab != null && !n.equals(tab.getDatabaseName())) {
                    tab.setDatabaseName(n);
                    if (editorController != null) editorController.refreshTabDecorations();
                }
            }
        });
    }

    public void syncDatabaseToToolbar(String databaseName) {
        if (databaseComboBox == null || databaseName == null || databaseName.isBlank()) return;
        if (!databaseComboBox.getItems().contains(databaseName)) {
            databaseComboBox.getItems().add(databaseName);
        }
        if (!databaseName.equals(databaseComboBox.getValue())) {
            databaseComboBox.getSelectionModel().select(databaseName);
        }
    }

    @FXML
    public void handleGotoBegin() {
        if (editorController != null && editorController.getMonacoHost() != null) {
            editorController.getMonacoHost().gotoBegin();
        }
    }

    @FXML
    public void handleGotoEnd() {
        if (editorController != null && editorController.getMonacoHost() != null) {
            editorController.getMonacoHost().gotoEnd();
        }
    }

    @FXML
    public void handleRefreshObjects() {
        refreshConnectionDropdown();
        if (sidebarController != null) {
            sidebarController.handleRefresh();
        }
        if (editorController != null) {
            editorController.reloadIntellisense();
        }
    }

    @FXML
    public void handleOpenCodeStyles() {
        // SCR-DLG-009 SQL code style manager (Phase 5)
        app.ui.dialogs.CodeStyleDialog.show(ownerWindow(),
                editorController != null ? editorController::getActiveText : null);
    }

    @FXML
    public void handleToggleAutoHideMenu() {
        autoHideMenuBar = menuViewAutoHideMenu != null && menuViewAutoHideMenu.isSelected();
        if (titleBarController != null) {
            titleBarController.setMenuBarHidden(autoHideMenuBar);
        }
    }

    @FXML
    public void handleCheckUpdates() {
        // DLG-013, mock only: no network call before Gate D.
        app.ui.dialogs.UpdateDialog.show(ownerWindow());
    }

    @FXML
    public void handleOpenSettings() {
        Stage stage = AppContext.getInstance().getPrimaryStage();
        if (stage == null && mainWindowRoot != null && mainWindowRoot.getScene() != null) {
            stage = (Stage) mainWindowRoot.getScene().getWindow();
        }
        app.ui.dialogs.SettingsDialog.show(stage);
    }

    @FXML
    public void handleOpenSnippetsManager() {
        app.ui.dialogs.SnippetsManagerDialog.show(ownerWindow());
    }

    @FXML
    public void handleOpenQueryHistory() {
        app.ui.dialogs.QueryHistoryDialog.show(ownerWindow(), sql -> {
            if (editorController != null) {
                String conn = connectionComboBox != null ? connectionComboBox.getValue() : null;
                String db = schemaComboBox != null ? schemaComboBox.getValue() : null;
                editorController.openNewTab("history_query.sql", sql, conn, db, null);
            }
        });
    }

    @FXML
    public void handleOpenObjectSearch() {
        if (editorController != null) {
            String connName = connectionComboBox != null ? connectionComboBox.getValue() : null;
            var profile = app.framework.connection.ProfileLookup.byName(connName);
            String db = schemaComboBox != null ? schemaComboBox.getValue() : null;
            editorController.openObjectContentSearchTab(profile, db, null);
        }
    }

    @FXML
    public void handleOpenSchemaCompare() {
        if (editorController != null) {
            String connName = connectionComboBox != null ? connectionComboBox.getValue() : null;
            var profile = app.framework.connection.ProfileLookup.byName(connName);
            String db = schemaComboBox != null ? schemaComboBox.getValue() : null;
            editorController.openSchemaCompareTab(profile, db, null, null, null, null);
        }
    }

    @FXML
    public void handleOpenDependencyGraph() {
        if (editorController != null) {
            String connName = connectionComboBox != null ? connectionComboBox.getValue() : null;
            var profile = app.framework.connection.ProfileLookup.byName(connName);
            String db = schemaComboBox != null ? schemaComboBox.getValue() : null;
            editorController.openDependencyGraphTab(profile, db, "dbo", "orders", null, com.lltool.core.domain.dependency.DependencyNodeType.TABLE);
        }
    }

    public void openDependencyGraph(com.lltool.core.application.connection.dto.ConnectionProfileDto profile,
                                    String catalog, String schema, String objectName, String columnName,
                                    com.lltool.core.domain.dependency.DependencyNodeType objectType) {
        if (editorController != null) {
            editorController.openDependencyGraphTab(profile, catalog, schema, objectName, columnName, objectType);
        }
    }

    @FXML
    public void handleOpenSqlVisualizer() {
        if (editorController != null) {
            String connName = connectionComboBox != null ? connectionComboBox.getValue() : null;
            var profile = app.framework.connection.ProfileLookup.byName(connName);
            String db = schemaComboBox != null ? schemaComboBox.getValue() : null;
            editorController.openSqlVisualizerTab(profile, db, "dbo", "Query1", null);
        }
    }

    public void openSqlVisualizer(com.lltool.core.application.connection.dto.ConnectionProfileDto profile,
                                  String catalog, String schema, String queryName, String initialSql) {
        if (editorController != null) {
            editorController.openSqlVisualizerTab(profile, catalog, schema, queryName, initialSql);
        }
    }

    public EditorController getEditorController() {
        return editorController;
    }

    public SidebarController getSidebarController() {
        return sidebarController;
    }

    @FXML
    public void handleOpenFile() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Mở file SQL");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("SQL Files (*.sql)", "*.sql"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );
        java.io.File file = chooser.showOpenDialog(ownerWindow());
        if (file != null && editorController != null) {
            try {
                String content = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                editorController.openTabWithFile(file.getName(), content, file.toPath(), true);
            } catch (Exception e) {
                app.services.NotificationService.getInstance().showError("Lỗi mở file", e.getMessage());
            }
        }
    }

    @FXML
    public void handleSaveFile() {
        saveTabFile(false);
    }

    @FXML
    public void handleSaveFileAs() {
        saveTabFile(true);
    }

    private void saveTabFile(boolean saveAs) {
        if (editorController == null) return;
        var activeTab = editorController.getActiveTab();
        if (activeTab == null) return;

        java.nio.file.Path path = activeTab.getFilePath();

        if (path == null || saveAs) {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle(saveAs ? "Lưu file SQL thành..." : "Lưu file SQL");
            chooser.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter("SQL Files (*.sql)", "*.sql"),
                    new javafx.stage.FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
            );
            if (path != null && path.getParent() != null) {
                chooser.setInitialDirectory(path.getParent().toFile());
                chooser.setInitialFileName(path.getFileName().toString());
            } else {
                String tabName = activeTab.getTabTitle();
                chooser.setInitialFileName(tabName != null && tabName.endsWith(".sql") ? tabName : (tabName != null ? tabName + ".sql" : "query.sql"));
            }
            java.io.File file = chooser.showSaveDialog(ownerWindow());
            if (file == null) return;
            path = file.toPath();
            activeTab.setFilePath(path);
            activeTab.setTabTitle(path.getFileName().toString());
        }

        try {
            String content = editorController.getActiveText();
            java.nio.file.Files.writeString(path, content != null ? content : "", java.nio.charset.StandardCharsets.UTF_8);
            activeTab.markSaved();
            String connName = activeTab.getConnectionName() != null ? activeTab.getConnectionName() : (connectionComboBox != null ? connectionComboBox.getValue() : null);
            String dbName = activeTab.getDatabaseName() != null ? activeTab.getDatabaseName() : (schemaComboBox != null ? schemaComboBox.getValue() : null);
            app.services.HistoryLoggingService.getInstance().recordSnapshot(
                    activeTab.getDocumentId(),
                    activeTab.getTabTitle(),
                    path.toString(),
                    content,
                    connName,
                    dbName
            );
            app.services.HistoryLoggingService.getInstance().updateDocument(
                    activeTab.getDocumentId(),
                    activeTab.getTabTitle(),
                    path.toString()
            );
            app.services.NotificationService.getInstance().showSuccess("Đã lưu", "Đã lưu " + activeTab.getTabTitle());
        } catch (Exception e) {
            app.services.NotificationService.getInstance().showError("Lỗi lưu file", e.getMessage());
        }
    }

    private void setupLayoutIntegration() {
        app.services.LayoutService layout = app.services.LayoutService.getInstance();

        layout.sidebarVisibleProperty().addListener((obs, oldVal, newVal) -> {
            applySidebarVisibility(newVal);
            if (statusBarController != null) {
                statusBarController.setStatus(newVal ? "Database Explorer shown" : "Database Explorer hidden", "online");
            }
        });

        layout.sidebarCollapsedProperty().addListener((obs, oldVal, newVal) -> {
            applySidebarCollapsed(newVal);
        });

        layout.resultsVisibleProperty().addListener((obs, oldVal, newVal) -> {
            applyResultsVisibility(newVal);
            if (statusBarController != null) {
                statusBarController.setStatus(newVal ? "Results Panel shown" : "Results Panel hidden", "online");
            }
        });

        layout.resultsMaximizedProperty().addListener((obs, oldVal, newVal) -> {
            applyResultsMaximized(newVal);
        });

        if (resultsController != null) {
            resultsController.setOnUndockHandler(this::handleUndockResults);
            if (editorController != null) {
                resultsController.setOnOpenInNewTabHandler((title, content) -> editorController.openNewTab(title, content));
            }
            resultsController.setOnFetchEstimatedPlanCallback(this::handleFetchEstimatedPlan);
            resultsController.setOnFetchActualPlanCallback(this::handleFetchActualPlan);
        }

        // SCR-MAIN-001: Explorer keeps its pixel width when the window is resized;
        // editor/results keep a minimum height.
        if (sidebar != null) {
            SplitPane.setResizableWithParent(sidebar, false);
            sidebar.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (sidebarWidthApplied && layout.isSidebarVisible() && !layout.isSidebarCollapsed()
                        && mainHorizontalSplit != null && mainHorizontalSplit.getItems().contains(sidebar)) {
                    layout.setSidebarWidthPx(newVal.doubleValue());
                }
            });
        }
        if (mainHorizontalSplit != null) {
            mainHorizontalSplit.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (sidebar != null && !layout.isSidebarCollapsed()) {
                    sidebar.setMaxWidth(maxSidebarWidth());
                }
            });
        }
        if (editor != null) editor.setMinHeight(app.services.LayoutService.MIN_PANEL_HEIGHT_PX);
        if (results != null) results.setMinHeight(app.services.LayoutService.MIN_PANEL_HEIGHT_PX);

        // Presets change the stored sizes: reflect them in the splits.
        layout.sidebarWidthPxProperty().addListener((obs, oldVal, newVal) -> {
            if (!layout.isSidebarCollapsed() && sidebar != null
                    && Math.abs(sidebar.getWidth() - newVal.doubleValue()) > 2) {
                sidebar.setPrefWidth(newVal.doubleValue());
                applySidebarWidthPx(newVal.doubleValue());
            }
        });
        layout.resultsDividerPositionProperty().addListener((obs, oldVal, newVal) -> {
            if (centerVerticalSplit != null && !centerVerticalSplit.getDividers().isEmpty()
                    && Math.abs(centerVerticalSplit.getDividerPositions()[0] - newVal.doubleValue()) > 0.005) {
                centerVerticalSplit.setDividerPosition(0, newVal.doubleValue());
            }
        });

        // Apply initial layout
        applySidebarVisibility(layout.isSidebarVisible());
        applySidebarCollapsed(layout.isSidebarCollapsed());
        applyResultsVisibility(layout.isResultsVisible());

        // Track user divider adjustments
        if (mainHorizontalSplit != null && !mainHorizontalSplit.getDividers().isEmpty()) {
            mainHorizontalSplit.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
                if (layout.isSidebarVisible() && !layout.isSidebarCollapsed()) {
                    layout.setSidebarDividerPosition(newVal.doubleValue());
                }
            });
        }

        if (centerVerticalSplit != null && !centerVerticalSplit.getDividers().isEmpty()) {
            centerVerticalSplit.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
                if (layout.isResultsVisible() && !layout.isResultsMaximized() && !layout.isResultsFloating()) {
                    layout.setResultsDividerPosition(newVal.doubleValue());
                }
            });
        }
    }

    private void applySidebarVisibility(boolean visible) {
        if (sidebar == null || mainHorizontalSplit == null) return;
        if (visible) {
            if (!mainHorizontalSplit.getItems().contains(sidebar)) {
                mainHorizontalSplit.getItems().add(0, sidebar);
                applySidebarCollapsed(app.services.LayoutService.getInstance().isSidebarCollapsed());
            }
        } else {
            if (mainHorizontalSplit.getDividerPositions().length > 0 && !app.services.LayoutService.getInstance().isSidebarCollapsed()) {
                app.services.LayoutService.getInstance().setSidebarDividerPosition(mainHorizontalSplit.getDividerPositions()[0]);
                app.services.LayoutService.getInstance().setSidebarWidthPx(sidebar.getWidth());
            }
            mainHorizontalSplit.getItems().remove(sidebar);
            SplitPane.setResizableWithParent(sidebar, false);
        }
        if (menuViewExplorer != null) {
            menuViewExplorer.setSelected(visible);
        }
    }

    /** Explorer may take at most half of the window (SCR-MAIN-001). */
    private double maxSidebarWidth() {
        double w = mainHorizontalSplit != null ? mainHorizontalSplit.getWidth() : 0;
        return w > 0 ? Math.max(app.services.LayoutService.MIN_SIDEBAR_WIDTH_PX, w * 0.5) : Double.MAX_VALUE;
    }

    /** Converts a pixel width into a divider position once the split has a real width. */
    private void applySidebarWidthPx(double px) {
        if (mainHorizontalSplit == null || sidebar == null) return;
        Runnable apply = () -> {
            double total = mainHorizontalSplit.getWidth();
            if (total <= 0 || !mainHorizontalSplit.getItems().contains(sidebar)
                    || mainHorizontalSplit.getDividers().isEmpty()) return;
            var insets = mainHorizontalSplit.getInsets();
            double usable = Math.max(1, total - insets.getLeft() - insets.getRight());
            mainHorizontalSplit.setDividerPosition(0, Math.min(0.9, px / usable));
            sidebarWidthApplied = true;
        };
        if (mainHorizontalSplit.getWidth() > 0) {
            Platform.runLater(apply);
        } else {
            mainHorizontalSplit.widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends Number> o, Number a, Number b) {
                    if (b.doubleValue() > 0) {
                        mainHorizontalSplit.widthProperty().removeListener(this);
                        Platform.runLater(apply);
                    }
                }
            });
        }
    }

    private void applySidebarCollapsed(boolean collapsed) {
        if (sidebarController != null) {
            sidebarController.setCollapsed(collapsed);
        }
        if (sidebar != null && mainHorizontalSplit != null && mainHorizontalSplit.getItems().contains(sidebar)) {
            double rail = app.services.LayoutService.RAIL_WIDTH_PX;
            if (collapsed) {
                sidebar.setMinWidth(rail);
                sidebar.setMaxWidth(rail);
                sidebar.setPrefWidth(rail);
                applySidebarWidthPx(rail);
            } else {
                double px = app.services.LayoutService.getInstance().getSidebarWidthPx();
                sidebar.setMinWidth(app.services.LayoutService.MIN_SIDEBAR_WIDTH_PX);
                sidebar.setMaxWidth(maxSidebarWidth());
                sidebar.setPrefWidth(px);
                applySidebarWidthPx(px);
            }
        }
    }

    private void applyResultsVisibility(boolean visible) {
        if (results == null || centerVerticalSplit == null) return;
        if (visible) {
            if (!centerVerticalSplit.getItems().contains(results) && !app.services.LayoutService.getInstance().isResultsFloating()) {
                centerVerticalSplit.getItems().add(results);
                double divPos = app.services.LayoutService.getInstance().isResultsMaximized() ?
                    app.services.LayoutService.MAXIMIZED_RESULTS_DIVIDER :
                    app.services.LayoutService.getInstance().getResultsDividerPosition();
                centerVerticalSplit.setDividerPosition(0, divPos);
                Platform.runLater(() -> {
                    if (!centerVerticalSplit.getDividers().isEmpty()) {
                        centerVerticalSplit.setDividerPosition(0, divPos);
                        centerVerticalSplit.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
                            if (app.services.LayoutService.getInstance().isResultsVisible()
                                    && !app.services.LayoutService.getInstance().isResultsMaximized()
                                    && !app.services.LayoutService.getInstance().isResultsFloating()) {
                                app.services.LayoutService.getInstance().setResultsDividerPosition(newVal.doubleValue());
                            }
                        });
                    }
                });
            }
        } else {
            if (centerVerticalSplit.getDividerPositions().length > 0 && !app.services.LayoutService.getInstance().isResultsMaximized()) {
                double currentPos = centerVerticalSplit.getDividerPositions()[0];
                if (currentPos > 0.05 && currentPos < 0.95) {
                    app.services.LayoutService.getInstance().setResultsDividerPosition(currentPos);
                }
            }
            centerVerticalSplit.getItems().remove(results);
        }
        if (menuViewResults != null) {
            menuViewResults.setSelected(visible);
        }
    }

    private void applyResultsMaximized(boolean maximized) {
        if (results == null || centerVerticalSplit == null || !app.services.LayoutService.getInstance().isResultsVisible()) {
            return;
        }
        if (!centerVerticalSplit.getItems().contains(results)) {
            centerVerticalSplit.getItems().add(results);
        }
        if (maximized) {
            centerVerticalSplit.setDividerPosition(0, app.services.LayoutService.MAXIMIZED_RESULTS_DIVIDER);
        } else {
            centerVerticalSplit.setDividerPosition(0, app.services.LayoutService.getInstance().getResultsDividerPosition());
        }
    }

    @FXML
    public void handleToggleSidebar() {
        app.services.LayoutService.getInstance().toggleSidebar();
    }

    @FXML
    public void handleToggleResults() {
        app.ui.editor.SqlEditorTab active = editorController != null ? editorController.getCurrentEditorTab() : null;
        if (active != null && !active.isSqlTextTab()) {
            if (statusBarController != null) {
                statusBarController.setStatus("Results Panel chỉ khả dụng cho tab soạn thảo SQL", "info");
            }
            return;
        }
        app.services.LayoutService.getInstance().toggleResults();
    }

    @FXML
    public void handleMaximizeResults() {
        app.services.LayoutService.getInstance().toggleMaximizeResults();
    }

    @FXML
    public void handleMaximizeEditor() {
        handlePresetEditorFocused();
    }

    @FXML
    public void handleUndockResults() {
        app.services.LayoutService layout = app.services.LayoutService.getInstance();
        if (layout.isResultsFloating()) {
            layout.reDockResults();
            return;
        }

        if (results != null && centerVerticalSplit != null) {
            if (centerVerticalSplit.getDividerPositions().length > 0) {
                layout.setResultsDividerPosition(centerVerticalSplit.getDividerPositions()[0]);
            }
            centerVerticalSplit.getItems().remove(results);

            layout.openFloatingResultsWindow(results, () -> {
                Platform.runLater(() -> {
                    if (centerVerticalSplit != null && results != null && !centerVerticalSplit.getItems().contains(results)) {
                        centerVerticalSplit.getItems().add(results);
                        centerVerticalSplit.setDividerPosition(0, layout.getResultsDividerPosition());
                    }
                });
            });
        }
    }

    @FXML
    public void handlePresetDefault() {
        app.services.LayoutService.getInstance().applyPreset(app.services.LayoutService.LayoutPreset.DEFAULT);
        if (statusBarController != null) {
            statusBarController.setStatus("Layout preset: Default", "online");
        }
        app.services.NotificationService.getInstance().showInfo("Layout", "Restored Default Layout.");
    }

    @FXML
    public void handlePresetEditorFocused() {
        app.services.LayoutService.getInstance().applyPreset(app.services.LayoutService.LayoutPreset.EDITOR_FOCUSED);
        if (statusBarController != null) {
            statusBarController.setStatus("Layout preset: Editor Focused (Distraction-Free)", "online");
        }
        app.services.NotificationService.getInstance().showInfo("Layout", "Distraction-Free Editor mode enabled.");
    }

    @FXML
    public void handlePresetExplorerFocused() {
        app.services.LayoutService.getInstance().applyPreset(app.services.LayoutService.LayoutPreset.DB_EXPLORER_FOCUSED);
        if (statusBarController != null) {
            statusBarController.setStatus("Layout preset: Explorer Focused", "online");
        }
        app.services.NotificationService.getInstance().showInfo("Layout", "Explorer Focused layout.");
    }

    @FXML
    public void handlePresetResultsMaximized() {
        app.services.LayoutService.getInstance().applyPreset(app.services.LayoutService.LayoutPreset.RESULTS_MAXIMIZED);
        if (statusBarController != null) {
            statusBarController.setStatus("Layout preset: Results Maximized", "online");
        }
        app.services.NotificationService.getInstance().showInfo("Layout", "Results Maximized layout.");
    }

    private void setupWorkspaceIntegration() {
        if (editorController != null) {
            app.framework.workspace.WorkspaceDesktopManager wsManager = app.framework.workspace.WorkspaceDesktopManager.getInstance();
            wsManager.registerEditorController(editorController);
            var activeWs = wsManager.initializeOrRestoreWorkspace();
            Platform.runLater(() -> wsManager.applyWorkspaceState(activeWs));
        }
    }

    @FXML
    public void handleSaveWorkspace() {
        boolean saved = app.framework.workspace.WorkspaceDesktopManager.getInstance().saveCurrentWorkspaceState();
        if (saved) {
            if (statusBarController != null) {
                statusBarController.setStatus("Workspace state saved successfully", "online");
            }
            app.services.NotificationService.getInstance().showSuccess("Workspace", "Workspace state saved.");
        } else {
            app.services.NotificationService.getInstance().showWarning("Workspace", "Failed to save workspace state.");
        }
    }

    @FXML
    public void handleResetLayout() {
        app.services.LayoutService.getInstance().resetToDefault();
        if (statusBarController != null) {
            statusBarController.setStatus("Layout reset to default", "online");
        }
        app.services.NotificationService.getInstance().showSuccess("Layout", "Layout reset to default configuration.");
    }

    public void openQueryConsole(String title, String sql) {
        openQueryConsole(title, sql, null, null, null);
    }

    public void openQueryConsole(String title, String sql, String connName, String dbName, String schemaName) {
        if (editorController != null) {
            editorController.openNewTab(title != null ? title : "console.sql", sql != null ? sql : "", connName, dbName, schemaName);
        }
    }

    /** "Permissions: name" tab of a SQL Server database user / PostgreSQL role (opened once per principal). */
    public void openPrincipalPermissions(com.lltool.core.application.connection.dto.ConnectionProfileDto profile, String database, String name) {
        if (editorController == null || profile == null || name == null) return;
        String key = "permissions|" + profile.id() + "|" + (database != null ? database : "") + "|" + name;
        editorController.openViewTab(key, "Permissions: " + name,
                () -> new app.ui.components.PrincipalPermissionsView(profile, database, name,
                        (title, sql) -> openQueryConsole(title + ".sql", sql, profile.name(), database, null)),
                profile.name(), database);
    }

    /** Feature 10 / SCR-TD-001: Opens the Table Designer tab. */
    public void openTableDesigner(com.lltool.core.application.connection.dto.ConnectionProfileDto profile,
                                  String catalog, String schema, String table, boolean isNewTable) {
        if (editorController != null) {
            editorController.openTableDesignerTab(profile, catalog, schema, table, isNewTable);
        }
    }

    /** Feature 10 / TD5: Opens the Table Designer tab with a duplicated TableDesign. */
    public void openTableDesignerForCopy(com.lltool.core.application.connection.dto.ConnectionProfileDto profile,
                                         String catalog, String schema, String table,
                                         com.lltool.core.domain.tabledesigner.model.TableDesign clonedDesign) {
        if (editorController != null) {
            editorController.openTableDesignerTabForCopy(profile, catalog, schema, table, clonedDesign);
        }
    }

    /** Feature 2 / Table Data Editor: Opens the Table Data Editor tab. */
    public void openTableDataEditor(com.lltool.core.application.connection.dto.ConnectionProfileDto profile,
                                    String catalog, String schema, String table) {
        if (editorController != null) {
            editorController.openTableDataEditorTab(profile, catalog, schema, table);
        }
    }

    public void executeQueryDirectly(String title, String sql) {
        executeQueryDirectly(title, sql, null, null, null);
    }

    public void executeQueryDirectly(String title, String sql, String connName, String dbName, String schemaName) {
        if (editorController != null) {
            editorController.openNewTab(title != null ? title : "query.sql", sql != null ? sql : "", connName, dbName, schemaName);
            Platform.runLater(this::handleExecute);
        }
    }

    @FXML
    public void handleCloseTab() {
        if (editorController != null) {
            editorController.closeCurrentTab();
        }
    }

    @FXML
    public void handleCloseAllTabs() {
        if (editorController != null) {
            editorController.closeAllTabs();
        }
    }

    @FXML
    public void handleExecute() {
        runSql(editorController != null ? editorController.getSelectedOrActiveText() : "SELECT 1;");
    }

    /** Phase 8 / D3: runs only the selection, or else the statement under the caret (not the whole script). */
    public void handleExecuteStatement() {
        runSql(editorController != null ? editorController.getSelectedOrStatementAtCursor() : "SELECT 1;");
    }

    private void runSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            if (statusBarController != null) {
                statusBarController.setStatus("No SQL statement to execute", "warning");
            }
            return;
        }

        // SCR-DLG-010: IDE debug extensions bs.* cannot run in normal mode
        var debugCalls = app.framework.query.DebugExtensionDetector.find(sql);
        if (!debugCalls.isEmpty()) {
            var choice = app.ui.dialogs.DebugExtensionDialog.show(ownerWindow(), debugCalls);
            if (choice != app.ui.dialogs.DebugExtensionDialog.Choice.COMMENT_AND_RUN) {
                if (statusBarController != null) statusBarController.setStatus("Đã hủy chạy: có hàm debug bs.*", "warning");
                return;
            }
            String full = editorController != null ? editorController.getActiveText() : null;
            if (full != null && editorController != null) {
                editorController.replaceActiveContent(app.framework.query.DebugExtensionDetector.commentOut(full));
            }
            sql = app.framework.query.DebugExtensionDetector.commentOut(sql);
        }

        // SCR-DLG-007a: Actions Guard
        var settings = app.services.SettingsService.getInstance().getCurrentSettings();
        if (settings.getSql() != null && settings.getSql().getSqlprotection() == 1) {
            var issues = app.framework.query.SqlProtectionService.getInstance().detectDangerousActions(sql);
            if (!issues.isEmpty()) {
                boolean confirmed = promptDangerousActionConfirmation(issues);
                if (!confirmed) {
                    if (statusBarController != null) {
                        statusBarController.setStatus("Đã hủy chạy (Actions Guard)", "warning");
                    }
                    return;
                }
            }
        }

        // Auto-show Results panel when query is executed if enabled in settings
        if (settings.isAutoShowResultsPanel()) {
            app.services.LayoutService.getInstance().setResultsVisible(true);
        }

        // Phase 8: no hard-coded fallback connection / schema; a tab without connection gets a clear error
        String activeConn = connectionComboBox != null ? connectionComboBox.getValue() : null;
        String activeSchema = schemaComboBox != null ? schemaComboBox.getValue() : null;

        app.ui.editor.SqlEditorTab activeTab = editorController != null ? editorController.getCurrentEditorTab() : null;
        String tabConn = activeTab != null && activeTab.getConnectionName() != null ? activeTab.getConnectionName() : activeConn;
        String tabDb = activeTab != null && activeTab.getDatabaseName() != null ? activeTab.getDatabaseName()
                : (databaseComboBox != null && databaseComboBox.getValue() != null ? databaseComboBox.getValue() : connectionDefaultDatabases.get(tabConn));
        String tabSchema = activeTab != null && activeTab.getSchemaName() != null ? activeTab.getSchemaName() : activeSchema;

        // Phase 8: read-only first (no point confirming PROD for a statement that is blocked anyway),
        // then SCR-DLG-007b for writes on PRODUCTION. The execution service checks again.
        String env = connectionEnvironments.get(tabConn);
        var prof = app.framework.connection.ProfileLookup.byName(tabConn);
        boolean profReadOnly = prof != null ? prof.readOnly() : Boolean.TRUE.equals(connectionReadOnly.get(tabConn));
        boolean readOnly = app.framework.backend.BackendFlags.readOnlyAll() || profReadOnly;
        var decision = app.framework.query.SqlStatementClassifier.check(sql, readOnly, env, false);
        boolean prodConfirmed = false;
        if (!decision.allowed() && "ERR_READ_ONLY".equals(decision.errorCode())) {
            boolean unlocked = app.ui.dialogs.ReadOnlyGuardDialog.show(ownerWindow(), tabConn);
            if (unlocked) {
                setConnectionReadOnly(tabConn, false);
                if (statusBarController != null) {
                    statusBarController.setStatus("Đã chuyển kết nối sang cho phép ghi", "success");
                }
                if (editorController != null) {
                    editorController.refreshTabDecorations();
                }
                final String sqlToRetry = sql;
                Platform.runLater(() -> runSql(sqlToRetry));
            } else {
                if (resultsController != null) {
                    resultsController.addMessage(app.models.ConsoleMessage.Level.WARNING, decision.message(), "-");
                    resultsController.selectMessagesTab();
                }
                if (statusBarController != null) statusBarController.setStatus("Đã chặn: kết nối chỉ đọc", "warning");
            }
            return;
        }
        if (decision.needsConfirmation()) {
            String changeType = changeStatementType(sql);
            if (changeType == null) changeType = String.join(", ", decision.classification().actions());
            if (!app.ui.dialogs.ProductionSafetyDialog.show(ownerWindow(), changeType, tabConn, tabDb)) {
                if (statusBarController != null) statusBarController.setStatus("Đã hủy chạy trên PRODUCTION", "warning");
                return;
            }
            prodConfirmed = true;
        }

        if (resultsController != null) resultsController.setExecutionContext(tabConn, tabDb, tabSchema);

        // UI state -> running
        setExecutionRunningState(true);

        String tabId = activeTab != null ? activeTab.getDocumentId() : null;
        if (!ensureSessionForRun(tabId, tabConn, tabDb, tabSchema)) {
            refreshRunState();
            if (statusBarController != null) statusBarController.setStatus("Đã hủy chạy: chưa kết nối", "warning");
            return;
        }
        // D3: row limit (toolbar "Giới hạn" is synced to settings.sql.maxRows) and timeout come from settings
        var sqlSettings = settings.getSql();
        var queryOptions = sqlSettings != null
                ? new app.framework.query.QueryOptions(sqlSettings.getMaxRows(), sqlSettings.getTimeoutSeconds(), sqlSettings.isStopOnError())
                : app.framework.query.QueryOptions.legacy();
        final String tabTitle = activeTab != null ? activeTab.getTabTitle() : null;
        startRun(tabId, tabTitle, sql, tabConn, tabDb, tabSchema, prodConfirmed, queryOptions);
    }

    private void startRun(String tabId, String tabTitle, String sql, String tabConn, String tabDb, String tabSchema,
                          boolean prodConfirmed, app.framework.query.QueryOptions queryOptions) {
        final app.ui.editor.SqlEditorTab activeEditorTab = editorController != null ? editorController.getCurrentEditorTab() : null;
        final String filePathStr = activeEditorTab != null && activeEditorTab.getFilePath() != null ? activeEditorTab.getFilePath().toString() : null;
        final boolean isSelection = editorController != null && editorController.hasSelection();
        final Long historyEntryId = app.services.HistoryLoggingService.getInstance().recordExecution(
                tabId, tabTitle, sql, tabConn, tabDb, filePathStr, null, isSelection
        );

        app.framework.query.DesktopQueryExecutionService.getInstance().execute(tabId, sql, tabConn, tabDb, tabSchema, prodConfirmed, queryOptions, new app.framework.query.DesktopQueryExecutionService.QueryCallback() {
            @Override
            public void onMessage(String message, app.framework.query.DesktopQueryExecutionService.QueryCallback.MessageKind kind) {
                deliverToTab(tabId, () -> {
                    if (resultsController == null) return;
                    switch (kind) {
                        case ERROR -> resultsController.addMessage(app.models.ConsoleMessage.Level.ERROR, message, "-");
                        case WARNING -> {
                            resultsController.appendSqlMessage(message);
                            resultsController.addMessage(app.models.ConsoleMessage.Level.WARNING, message, "-");
                        }
                        default -> resultsController.appendSqlMessage(message);
                    }
                });
            }

            @Override
            public void onStart(String querySql, String connName) {
                if (resultsController != null) {
                    resultsController.addMessage(app.models.ConsoleMessage.Level.INFO,
                            "Bắt đầu chạy · " + (connName != null ? connName : "kết nối hiện tại")
                                    + (tabDb != null ? " / " + tabDb : ""), "-");
                }
                if (statusBarController != null) {
                    statusBarController.setStatus("Executing query on " + (connName != null ? connName : "current connection") + "...", "busy");
                }
                if (resultsController != null) {
                    resultsController.resetStreaming();
                    resultsController.showLoading(true);
                }
            }

            @Override
            public void onResultStarted(int index, String setSql, List<app.models.QueryResultColumn> columns) {
                deliverToTab(tabId, () -> {
                    if (resultsController != null) {
                        resultsController.setColumnWidthScope(tabId);
                        resultsController.streamResultStarted(index, setSql, columns);
                    }
                });
            }

            @Override
            public void onRows(int index, List<app.models.QueryResultRow> rows) {
                deliverToTab(tabId, () -> {
                    if (resultsController != null) resultsController.streamRows(index, rows);
                });
            }

            @Override
            public void onSuccess(app.models.QueryResult result, List<app.models.QueryResult> allResults, int rowsAffected, String summaryMessage, long durationMs) {
                app.services.HistoryLoggingService.getInstance().updateExecutionResult(
                        historyEntryId, durationMs, app.models.HistoryEntry.STATUS_OK, null
                );
                refreshRunState();
                if (editorController != null) editorController.reloadIntellisenseAfterRun(tabId, sql);
                notifyBackgroundDone(tabId, tabTitle, summaryMessage, false);
                deliverToTab(tabId, () -> {
                    if (resultsController != null) {
                        resultsController.showLoading(false);
                        resultsController.setColumnWidthScope(tabId);
                        if (allResults != null && !allResults.isEmpty()) {
                            // D4: a streamed grid is only finalized (no redraw, scroll kept)
                            if (resultsController.isStreaming()) resultsController.finishStreaming(allResults);
                            else resultsController.setQueryResults(allResults);
                            resultsController.selectResultsTab();
                        } else if (result != null) {
                            resultsController.setQueryResult(result);
                            resultsController.selectResultsTab();
                        } else {
                            resultsController.selectMessagesTab();
                        }
                        resultsController.addMessage(app.models.ConsoleMessage.Level.SUCCESS, summaryMessage, durationMs + "ms");
                        resultsController.appendSqlMessage(summaryMessage);
                    }
                    if (statusBarController != null) {
                        statusBarController.setLastRun(summaryMessage + " · " + durationMs + " ms", "online");
                    }
                });
                if (isActiveTab(tabId)) {
                    app.services.NotificationService.getInstance().showSuccess("Query Executed", summaryMessage);
                }
            }

            @Override
            public void onError(String errorMessage, Throwable cause) {
                app.services.HistoryLoggingService.getInstance().updateExecutionResult(
                        historyEntryId, -1L, app.models.HistoryEntry.STATUS_ERROR, errorMessage
                );
                refreshRunState();
                // a script can fail after its first statements already changed objects
                if (editorController != null) editorController.reloadIntellisenseAfterRun(tabId, sql);
                notifyBackgroundDone(tabId, tabTitle, errorMessage, true);
                boolean isReadOnlyError = (cause instanceof java.sql.SQLException sqle && ("ERR_READ_ONLY".equals(sqle.getSQLState()) || "25006".equals(sqle.getSQLState())))
                        || (errorMessage != null && (
                            errorMessage.contains("ERR_READ_ONLY")
                            || errorMessage.toLowerCase().contains("chỉ đọc")
                            || errorMessage.toLowerCase().contains("read-only")
                            || errorMessage.toLowerCase().contains("readonly")
                            || errorMessage.toLowerCase().contains("read only")
                            || errorMessage.contains("25006")
                        ));
                if (isReadOnlyError && isActiveTab(tabId)) {
                    deliverToTab(tabId, () -> {
                        if (resultsController != null) {
                            resultsController.resetStreaming();
                            resultsController.showLoading(false);
                        }
                    });
                    Platform.runLater(() -> {
                        boolean unlocked = app.ui.dialogs.ReadOnlyGuardDialog.show(ownerWindow(), tabConn);
                        if (unlocked) {
                            setConnectionReadOnly(tabConn, false);
                            if (statusBarController != null) {
                                statusBarController.setStatus("Đã chuyển kết nối sang cho phép ghi", "success");
                            }
                            if (editorController != null) {
                                editorController.refreshTabDecorations();
                            }
                            runSql(sql);
                        } else {
                            deliverToTab(tabId, () -> {
                                if (resultsController != null) {
                                    resultsController.addMessage(app.models.ConsoleMessage.Level.WARNING, errorMessage, "-");
                                    resultsController.selectMessagesTab();
                                }
                                if (statusBarController != null) {
                                    statusBarController.setLastRun("Đã chặn: kết nối chỉ đọc", "warning");
                                }
                            });
                        }
                    });
                } else if (cause instanceof app.framework.session.ConnectionLostException) {
                    // the tab's session was dead: ask, reconnect and run again (no silent reconnect)
                    Platform.runLater(() -> retryAfterLostConnection(tabId, tabTitle, sql, tabConn, tabDb, tabSchema,
                            prodConfirmed, queryOptions));
                } else {
                    deliverToTab(tabId, () -> {
                        if (resultsController != null) {
                            resultsController.resetStreaming();
                            resultsController.showLoading(false);
                            resultsController.addMessage(app.models.ConsoleMessage.Level.ERROR, errorMessage, "-");
                            resultsController.selectMessagesTab();
                        }
                        if (statusBarController != null) {
                            statusBarController.setLastRun("Lỗi: " + errorMessage, "offline");
                        }
                    });
                    if (isActiveTab(tabId)) {
                        app.services.NotificationService.getInstance().showWarning("Query Failed", errorMessage);
                    }
                }
            }

            @Override
            public void onCancelled() {
                app.services.HistoryLoggingService.getInstance().updateExecutionResult(
                        historyEntryId, -1L, app.models.HistoryEntry.STATUS_CANCELLED, "Query execution cancelled by user."
                );
                refreshRunState();
                deliverToTab(tabId, () -> {
                    if (resultsController != null) {
                        resultsController.resetStreaming(); // rows already shown stay
                        resultsController.showLoading(false);
                        resultsController.addMessage(app.models.ConsoleMessage.Level.WARNING, "Query execution cancelled by user.", "-");
                        resultsController.selectMessagesTab();
                    }
                    if (statusBarController != null) {
                        statusBarController.setLastRun("Đã hủy truy vấn", "warning");
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ D3: one run per tab

    /** Output of tabs that finished while another tab was open; shown when the tab is opened again. */
    private final java.util.Map<String, java.util.List<Runnable>> pendingTabOutput = new java.util.HashMap<>();

    private String activeTabId() {
        app.ui.editor.SqlEditorTab t = editorController != null ? editorController.getCurrentEditorTab() : null;
        return t != null ? t.getDocumentId() : null;
    }

    private boolean isActiveTab(String tabId) {
        return tabId == null || java.util.Objects.equals(tabId, activeTabId());
    }

    private void deliverToTab(String tabId, Runnable output) {
        if (isActiveTab(tabId)) {
            output.run();
        } else {
            pendingTabOutput.computeIfAbsent(tabId, k -> new java.util.ArrayList<>()).add(output);
        }
    }

    private void flushTabOutput() {
        String id = activeTabId();
        if (id == null) return;
        java.util.List<Runnable> out = pendingTabOutput.remove(id);
        if (out != null) out.forEach(Runnable::run);
    }

    /** A tab that is not open finished: say so (its results wait until the tab is opened). */
    private void notifyBackgroundDone(String tabId, String title, String summary, boolean failed) {
        if (isActiveTab(tabId)) return;
        String name = title != null ? title : "khác";
        if (failed) {
            app.services.NotificationService.getInstance().showWarning("Tab \"" + name + "\" bị lỗi", summary);
        } else {
            app.services.NotificationService.getInstance().showSuccess("Tab \"" + name + "\" đã chạy xong", summary);
        }
    }

    /** Run / Stop buttons and the loading overlay show the ACTIVE tab's state. */
    private void refreshRunState() {
        boolean running = app.framework.query.DesktopQueryExecutionService.getInstance().isExecuting(activeTabId());
        setExecutionRunningState(running);
        // a grid that is being filled shows its own progress: no overlay over it
        if (resultsController != null) resultsController.showLoading(running && !resultsController.isStreaming());
    }

    private void setExecutionRunningState(boolean running) {
        if (executeButton != null) {
            executeButton.setDisable(running);
        }
        if (executeButtonIcon != null) {
            executeButtonIcon.setIconLiteral(running ? "fth-loader" : "fth-play");
        }
        if (stopButton != null) {
            stopButton.setDisable(!running);
        }
    }

    @FXML
    private void handleStop() {
        boolean cancelled = app.framework.query.DesktopQueryExecutionService.getInstance().cancel(activeTabId());
        if (cancelled) {
            if (statusBarController != null) {
                statusBarController.setStatus("Cancelling query execution...", "busy");
            }
        }
    }

    @FXML
    public void handleFetchEstimatedPlan() {
        requestExecutionPlan("estimated");
    }

    @FXML
    public void handleFetchActualPlan() {
        requestExecutionPlan("actual");
    }

    private void requestExecutionPlan(String planType) {
        // Auto-show Results panel when execution plan is requested (preserving divider position)
        app.services.LayoutService.getInstance().setResultsVisible(true);

        String sql = editorController != null ? editorController.getSelectedOrActiveText() : "SELECT 1;";
        if (sql == null || sql.trim().isEmpty()) {
            if (statusBarController != null) {
                statusBarController.setStatus("No SQL statement to explain", "warning");
            }
            return;
        }

        // D3: no hard-coded fallback ("PostgreSQL - dev-local" / "public"): the plan uses the tab's own connection
        String activeConn = connectionComboBox != null ? connectionComboBox.getValue() : null;
        String activeSchema = schemaComboBox != null ? schemaComboBox.getValue() : null;

        app.ui.editor.SqlEditorTab activeTab = editorController != null ? editorController.getCurrentEditorTab() : null;
        String tabConn = activeTab != null && activeTab.getConnectionName() != null ? activeTab.getConnectionName() : activeConn;
        String tabDb = activeTab != null && activeTab.getDatabaseName() != null ? activeTab.getDatabaseName()
                : (databaseComboBox != null && databaseComboBox.getValue() != null ? databaseComboBox.getValue() : connectionDefaultDatabases.get(tabConn));
        String tabSchema = activeTab != null && activeTab.getSchemaName() != null ? activeTab.getSchemaName() : activeSchema;
        if (tabConn == null || tabConn.isBlank()) {
            app.services.NotificationService.getInstance().showWarning("Execution Plan",
                    "Tab chưa chọn kết nối. Chọn kết nối trên thanh công cụ rồi thử lại.");
            return;
        }

        if (resultsController != null) {
            resultsController.selectExecutionPlanTab();
            resultsController.showPlanLoading(true);
        }

        if (statusBarController != null) {
            statusBarController.setStatus("Generating " + planType + " execution plan...", "busy");
        }

        app.framework.plan.DesktopExecutionPlanService.getInstance().getExecutionPlanAsync(
                activeTab != null ? activeTab.getDocumentId() : null, tabConn, tabDb, tabSchema, sql, planType, new app.framework.plan.DesktopExecutionPlanService.PlanCallback() {
                    @Override
                    public void onStart(String type, String dialect) {
                        if (resultsController != null) {
                            resultsController.showPlanLoading(true);
                        }
                    }

                    @Override
                    public void onSuccess(app.models.PhysicalExecutionPlan plan) {
                        if (resultsController != null) {
                            resultsController.showPlanLoading(false);
                            resultsController.setExecutionPlan(plan);
                            resultsController.selectExecutionPlanTab();
                        }
                        if (statusBarController != null) {
                            statusBarController.setStatus("Execution plan generated: " + plan.getNodeCount() + " operators", "online");
                        }
                        app.services.NotificationService.getInstance().showSuccess("Execution Plan",
                                "Generated " + planType + " plan (" + plan.getNodeCount() + " operators, Total Cost: " + String.format("%.2f", plan.getTotalCost()) + ")");
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (resultsController != null) {
                            resultsController.showPlanLoading(false);
                        }
                        if (statusBarController != null) {
                            statusBarController.setStatus("Execution plan error: " + error.getMessage(), "offline");
                        }
                        app.services.NotificationService.getInstance().showWarning("Execution Plan Error", error.getMessage());
                    }
                }
        );
    }

    @FXML
    private void handleClose() {
        if (titleBarController != null) {
            titleBarController.close();
        } else {
            Stage stage = AppContext.getInstance().getPrimaryStage();
            if (stage != null) {
                stage.close();
            }
        }
    }

    @FXML
    private void handleAbout() {
        app.ui.dialogs.AboutDialog.show(ownerWindow(), this::handleCheckUpdates);
    }


    private void setupToolbarSettings(app.models.IdeSettings settings) {
        if (limitComboBox != null) {
            limitComboBox.setItems(javafx.collections.FXCollections.observableArrayList("All", "100", "500", "1000", "5000"));
            int maxRows = settings.getSql() != null ? settings.getSql().getMaxRows() : 0;
            limitComboBox.setValue(maxRows == 0 ? "All" : String.valueOf(maxRows));
            limitComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    int rows = "All".equalsIgnoreCase(newVal) ? 0 : Integer.parseInt(newVal);
                    var current = app.services.SettingsService.getInstance().getCurrentSettings().clone();
                    current.getSql().setMaxRows(rows);
                    current.getQuery().setDefaultLimit(rows);
                    app.services.SettingsService.getInstance().saveSettings(current);
                }
            });
        }
        if (menuBravoTool != null) {
            boolean bravoEnabled = settings.getAddons() != null
                    && settings.getAddons().getBravo_tool() != null
                    && settings.getAddons().getBravo_tool().isEnabled();
            menuBravoTool.setVisible(bravoEnabled);
            menuBravoTool.setOnAction(e -> {
                app.services.NotificationService.getInstance().showInfo("BRAVO Tool", "Mở công cụ tiện ích mở rộng BRAVO Tool...");
            });
        }
    }

    private void applySettingsToUi(app.models.IdeSettings settings) {
        if (settings == null) return;
        if (editorController != null) {
            editorController.applyEditorSettings(settings);
        }
        applyUiTypography(settings);
        updateThemeIcons(ThemeService.getInstance().isDarkMode());
        applyStatusBarEditorSettings(settings);
        applyPanelStyle(settings);
        applyAutoHideToolbar(settings);
        if (statusBarController != null && statusBarController.getZoom() != 100) {
            Platform.runLater(this::applyEditorZoom);
        }
        if (menuBravoTool != null && settings.getAddons() != null && settings.getAddons().getBravo_tool() != null) {
            menuBravoTool.setVisible(settings.getAddons().getBravo_tool().isEnabled());
        }
        if (limitComboBox != null && settings.getSql() != null) {
            int maxRows = settings.getSql().getMaxRows();
            limitComboBox.setValue(maxRows == 0 ? "All" : String.valueOf(maxRows));
        }
    }

    private void applyUiTypography(app.models.IdeSettings settings) {
        if (settings == null) return;
        String fontFamily = settings.getUiFontFamily();
        if (fontFamily == null || fontFamily.isBlank()) fontFamily = "Inter";
        int fontSize = settings.getUiFontSize();
        if (fontSize < 10 || fontSize > 24) fontSize = 13;

        String style = String.format(java.util.Locale.ROOT, "-fx-font-family: '%s'; -fx-font-size: %dpx;", fontFamily, fontSize);
        if (mainWindowRoot != null) {
            mainWindowRoot.setStyle(style);
        }
    }

    private boolean promptDangerousActionConfirmation(java.util.List<app.framework.query.SqlProtectionService.DangerousActionIssue> issues) {
        var result = app.ui.dialogs.ActionsGuardDialog.show(ownerWindow(), issues);
        if (result.navigateLine() > 0 && editorController != null) {
            javafx.application.Platform.runLater(() -> editorController.gotoLine(result.navigateLine()));
        }
        return result.proceed();
    }

    private javafx.stage.Window ownerWindow() {
        return mainWindowRoot != null && mainWindowRoot.getScene() != null ? mainWindowRoot.getScene().getWindow() : null;
    }

    private static final java.util.regex.Pattern CHANGE_STMT = java.util.regex.Pattern.compile(
            "(?im)^\\s*(INSERT|UPDATE|DELETE|MERGE|TRUNCATE|DROP|ALTER|CREATE|GRANT|REVOKE|RENAME)\\b");

    /** "DML" / "DDL" when the script changes data or structure, otherwise null (comments ignored). */
    private static String changeStatementType(String sql) {
        if (sql == null) return null;
        String noComments = sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\n]*", " ");
        var m = CHANGE_STMT.matcher(noComments);
        boolean ddl = false, dml = false;
        while (m.find()) {
            String k = m.group(1).toUpperCase();
            if (k.equals("INSERT") || k.equals("UPDATE") || k.equals("DELETE") || k.equals("MERGE")) dml = true;
            else ddl = true;
        }
        return dml && ddl ? "DML / DDL" : dml ? "DML" : ddl ? "DDL" : null;
    }
}
