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
        Platform.runLater(this::bindWindowStateToRoot);
    }

    /** SCR-MAIN-001: rounded cards whose outline is never covered by the content. */
    private void setupCardClipping() {
        app.ui.components.RoundedCard.install(sidebar, 8);
        app.ui.components.RoundedCard.install(editor, 8);
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
        double gap = resultsDocked ? (flatPanels ? 3 : 4) : 0;
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

    /** Switches between rounded cards with gaps and a flat VS Code-like layout. */
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
            VBox.setMargin(contextToolBar, flat ? javafx.geometry.Insets.EMPTY : new javafx.geometry.Insets(6, 6, 0, 6));
        }
        if (statusBar != null) {
            BorderPane.setMargin(statusBar, flat ? javafx.geometry.Insets.EMPTY : new javafx.geometry.Insets(0, 6, 6, 6));
        }
        dividerGapAppliers.forEach(Runnable::run);    // R-UI-01: element shapes (inputs, selections, buttons, popups) follow the same setting
        app.services.ThemeService.getInstance().applyShapeToAllWindows();
    }

    // ------------------------------------------------------------------ status bar (SCR-MAIN-005)

    /** Status bar + tab headers follow the active tab's session (Phase 8 / D1). */
    private void refreshActiveTabSession() {
        if (statusBarController == null) return;
        var tab = editorController != null ? editorController.getCurrentEditorTab() : null;
        var session = tab != null ? app.framework.session.TabSessionManager.getInstance().get(tab.getDocumentId()) : null;
        boolean connected = session != null && session.state() == app.framework.session.TabSession.State.CONNECTED;
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.CONNECTIONS)) {
            String name = statusBarController.getConnectionName();
            String id = name != null ? connectionIds.get(name) : null;
            connected = id != null && app.services.ExplorerConnectionState.getInstance().get(id)
                    == app.services.ExplorerConnectionState.Status.CONNECTED;
        }
        statusBarController.setConnected(connected);
        statusBarController.setPid(connected && session.serverPid() != null ? String.valueOf(session.serverPid()) : null);
        if (editorController != null) editorController.refreshTabDecorations();
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
        statusBarController.setOnZoomChanged(pct -> applyEditorZoom());

        // Phase 8 / D1: the status bar shows the ACTIVE TAB's own session (state, server PID).
        Runnable refreshConnected = this::refreshActiveTabSession;
        app.framework.session.TabSessionManager.getInstance().revisionProperty()
                .addListener((o, a, b) -> refreshConnected.run());
        if (editorController != null) {
            editorController.getPrimaryTabPane().getSelectionModel().selectedItemProperty()
                    .addListener((o, a, b) -> refreshConnected.run());
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
        host.setFontSize(Math.max(6, Math.round(base * statusBarController.getZoom() / 100f)));
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
        String db = tab.getDatabaseName() != null ? tab.getDatabaseName() : profile.databaseName();
        app.framework.session.TabSessionManager.getInstance().connectAsync(tab.getDocumentId(), profile, db, res -> {
            if (res.isFailure()) {
                app.services.NotificationService.getInstance().showError("Không kết nối được " + profile.name(),
                        res.getError().map(e -> e.message()).orElse("Lỗi không xác định"));
            } else if (statusBarController != null) {
                statusBarController.setStatus("Tab đã kết nối " + profile.name() + (db != null ? " · " + db : ""), "online");
            }
        });
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
                    if (editorController != null) {
                        var tab = editorController.getCurrentEditorTab();
                        if (tab != null && !selected.equals(tab.getConnectionName())) {
                            tab.setConnectionName(selected);
                            // Phase 8 / D1: the tab switched server: its old session is closed,
                            // the new one opens on "Kết nối" or on the next run
                            app.framework.session.TabSessionManager.getInstance().disconnect(tab.getDocumentId());
                        }
                        editorController.refreshTabDecorations();
                    }
                    if (statusBarController != null) {
                        statusBarController.setConnection(selected);
                    }
                }
            });

            // Reactively sync with GlobalDesktopState activeConnection
            GlobalDesktopState.getInstance().activeConnectionProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank() && !newVal.equals(connectionComboBox.getValue())) {
                    if (!connectionComboBox.getItems().contains(newVal)) {
                        connectionComboBox.getItems().add(newVal);
                    }
                    connectionComboBox.getSelectionModel().select(newVal);
                    if (statusBarController != null) {
                        statusBarController.setConnection(newVal);
                    }
                }
            });
        }

        if (schemaComboBox != null) {
            schemaComboBox.setItems(FXCollections.observableArrayList(
                "public",
                "auth",
                "audit_logs",
                "information_schema"
            ));
            schemaComboBox.getSelectionModel().selectFirst();
        }
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

            for (ThemeService.AppTheme theme : ThemeService.AppTheme.values()) {
                RadioMenuItem item = new RadioMenuItem(theme.getDisplayName());
                item.setToggleGroup(group);
                if (theme == ThemeService.getInstance().getCurrentTheme()) {
                    item.setSelected(true);
                }
                item.setOnAction(e -> {
                    Stage stage = AppContext.getInstance().getPrimaryStage();
                    if (stage != null && stage.getScene() != null) {
                        ThemeService.getInstance().setTheme(theme, stage.getScene());
                    }
                });
                themesMenu.getItems().add(item);
            }
        }
    }

    private void setupThemeListener() {
        ThemeService.getInstance().currentThemeProperty().addListener((obs, oldTheme, newTheme) -> {
            updateThemeIcons(newTheme);
            if (editorController != null && editorController.getMonacoHost() != null) {
                editorController.getMonacoHost().setTheme(newTheme.isDark() ? "ide-dark" : "ide-light");
            }
            app.ui.titlebar.WindowTitleModel.getInstance().setThemeName(newTheme.getDisplayName());
            app.services.NotificationService.getInstance().showInfo("Theme Changed", "Active theme: " + newTheme.getDisplayName());
        });
        updateThemeIcons(ThemeService.getInstance().getCurrentTheme());
        app.ui.titlebar.WindowTitleModel.getInstance().setThemeName(ThemeService.getInstance().getCurrentTheme().getDisplayName());
    }

    private void updateThemeIcons(ThemeService.AppTheme theme) {
        if (themeToggleIcon != null) {
            themeToggleIcon.setIconLiteral(theme.isDark() ? "fth-sun" : "fth-moon");
        }
    }

    public HBox getTitleBarNode() {
        return titleBar;
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
            ThemeService.getInstance().toggleDarkLight(stage.getScene());
            setupThemeMenu();

            // Persist updated theme to SettingsService
            var s = app.services.SettingsService.getInstance().getCurrentSettings().clone();
            s.setTheme(ThemeService.getInstance().getCurrentTheme().getDisplayName());
            app.services.SettingsService.getInstance().saveSettings(s);
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
        // Context bar: show the profile's default database when the tab does not specify one.
        if (databaseComboBox != null && connectionName != null) {
            String defaultDb = connectionDefaultDatabases.get(connectionName);
            String tabDb = null;
            if (editorController != null && editorController.getCurrentEditorTab() != null) {
                tabDb = editorController.getCurrentEditorTab().getDatabaseName();
            }
            String db = (tabDb != null && !tabDb.isBlank()) ? tabDb : defaultDb;
            if (db != null && !db.isBlank()) {
                syncDatabaseToToolbar(db);
            } else {
                databaseComboBox.getSelectionModel().clearSelection();
            }
        }
    }

    /** Database selector of the context bar (mock list: no metadata query is issued from the toolbar). */
    private void setupDatabaseSelector() {
        if (databaseComboBox == null) return;
        GlobalDesktopState state = GlobalDesktopState.getInstance();
        String current = state.getActiveCatalog();
        if (current != null && !current.isBlank()) {
            databaseComboBox.getItems().setAll(current);
            databaseComboBox.getSelectionModel().select(current);
        }
        databaseComboBox.setOnAction(e -> {
            String selected = databaseComboBox.getValue();
            if (selected != null && !selected.equals(state.getActiveCatalog())) {
                state.setActiveCatalog(selected);
            }
        });
        state.activeCatalogProperty().addListener((obs, o, n) -> syncDatabaseToToolbar(n));
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
        String sql = editorController != null ? editorController.getSelectedOrActiveText() : "SELECT 1;";
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
        String tabDb = activeTab != null && activeTab.getDatabaseName() != null ? activeTab.getDatabaseName() : null;
        String tabSchema = activeTab != null && activeTab.getSchemaName() != null ? activeTab.getSchemaName() : activeSchema;

        // Phase 8: read-only first (no point confirming PROD for a statement that is blocked anyway),
        // then SCR-DLG-007b for writes on PRODUCTION. The execution service checks again.
        String env = connectionEnvironments.get(tabConn);
        boolean readOnly = app.framework.backend.BackendFlags.readOnlyAll() || Boolean.TRUE.equals(connectionReadOnly.get(tabConn));
        var decision = app.framework.query.SqlStatementClassifier.check(sql, readOnly, env, false);
        boolean prodConfirmed = false;
        if (!decision.allowed() && "ERR_READ_ONLY".equals(decision.errorCode())) {
            app.services.NotificationService.getInstance().showWarning("Chỉ đọc", decision.message());
            if (resultsController != null) {
                resultsController.addMessage(app.models.ConsoleMessage.Level.WARNING, decision.message(), "-");
                resultsController.selectMessagesTab();
            }
            if (statusBarController != null) statusBarController.setStatus("Đã chặn: kết nối chỉ đọc", "warning");
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
        if (!ensurePasswordForRun(tabId, tabConn)) {
            setExecutionRunningState(false);
            return;
        }
        app.framework.query.DesktopQueryExecutionService.getInstance().execute(tabId, sql, tabConn, tabDb, tabSchema, prodConfirmed, new app.framework.query.DesktopQueryExecutionService.QueryCallback() {
            @Override
            public void onStart(String querySql, String connName) {
                if (statusBarController != null) {
                    statusBarController.setStatus("Executing query on " + (connName != null ? connName : "current connection") + "...", "busy");
                }
                if (resultsController != null) {
                    resultsController.showLoading(true);
                }
            }

            @Override
            public void onSuccess(app.models.QueryResult result, List<app.models.QueryResult> allResults, int rowsAffected, String summaryMessage, long durationMs) {
                setExecutionRunningState(false);
                if (resultsController != null) {
                    resultsController.showLoading(false);
                    if (allResults != null && !allResults.isEmpty()) {
                        resultsController.setQueryResults(allResults);
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
                app.services.NotificationService.getInstance().showSuccess("Query Executed", summaryMessage);
            }

            @Override
            public void onError(String errorMessage, Throwable cause) {
                setExecutionRunningState(false);
                if (resultsController != null) {
                    resultsController.showLoading(false);
                    resultsController.addMessage(app.models.ConsoleMessage.Level.ERROR, errorMessage, "-");
                    resultsController.selectMessagesTab();
                }
                if (statusBarController != null) {
                    statusBarController.setLastRun("Lỗi: " + errorMessage, "offline");
                }
                app.services.NotificationService.getInstance().showWarning("Query Failed", errorMessage);
            }

            @Override
            public void onCancelled() {
                setExecutionRunningState(false);
                if (resultsController != null) {
                    resultsController.showLoading(false);
                    resultsController.addMessage(app.models.ConsoleMessage.Level.WARNING, "Query execution cancelled by user.", "-");
                    resultsController.selectMessagesTab();
                }
                if (statusBarController != null) {
                    statusBarController.setLastRun("Đã hủy truy vấn", "warning");
                }
            }
        });
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
        boolean cancelled = app.framework.query.DesktopQueryExecutionService.getInstance().cancel();
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

        String activeConn = connectionComboBox != null && connectionComboBox.getValue() != null ?
                connectionComboBox.getValue() : "PostgreSQL - dev-local";
        String activeSchema = schemaComboBox != null && schemaComboBox.getValue() != null ?
                schemaComboBox.getValue() : "public";

        app.ui.editor.SqlEditorTab activeTab = editorController != null ? editorController.getCurrentEditorTab() : null;
        String tabConn = activeTab != null && activeTab.getConnectionName() != null ? activeTab.getConnectionName() : activeConn;
        String tabDb = activeTab != null && activeTab.getDatabaseName() != null ? activeTab.getDatabaseName() : null;
        String tabSchema = activeTab != null && activeTab.getSchemaName() != null ? activeTab.getSchemaName() : activeSchema;

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
        updateThemeIcons(ThemeService.getInstance().getCurrentTheme());
        applyStatusBarEditorSettings(settings);
        applyPanelStyle(settings);
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
