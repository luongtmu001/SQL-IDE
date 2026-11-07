package app.controllers;

import app.ui.editor.SqlCodeArea;
import app.ui.editor.SqlEditorTab;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;

public class EditorController implements Initializable {

    @FXML private VBox editorContainer;
    @FXML private HBox tabHeaderActions;
    @FXML private SplitPane editorSplitContainer;
    @FXML private VBox emptyEditorStateBox;
    @FXML private TabPane primaryTabPane;
    @FXML private TabPane secondaryTabPane;
    @FXML private Button closeSplitButton;

    private javafx.scene.layout.VBox primaryWrap;
    private javafx.scene.layout.VBox secondaryWrap;
    private int tabCounter = 1;
    private BiConsumer<Integer, Integer> cursorReporter;
    /** Phase 8 / D3: last caret position (1-based) reported by the editor; used to find the statement under the caret. */
    private int lastCursorLine = 1;
    private int lastCursorCol = 1;
    private java.util.function.Consumer<SqlEditorTab> tabSelectionListener;
    private Runnable onExecuteRequestListener;
    private Runnable onExecuteStatementRequestListener;
    private boolean isSplit = false;
    private app.ui.editor.MonacoEditorHost monacoHost;

    private static final String DEFAULT_SQL =
        "-- SQL Query Console\n" +
        "SELECT \n" +
        "    u.id,\n" +
        "    u.username,\n" +
        "    u.email,\n" +
        "    COUNT(o.id) AS total_orders,\n" +
        "    COALESCE(SUM(o.total_amount), 0.00) AS total_spent\n" +
        "FROM users u\n" +
        "LEFT JOIN orders o ON u.id = o.user_id\n" +
        "WHERE u.status = 'active'\n" +
        "GROUP BY u.id, u.username, u.email\n" +
        "ORDER BY total_spent DESC\n" +
        "LIMIT 50;\n";

    /** Save request from the tab menu / close dialog; default only marks the tab saved (no file I/O in the prototype). */
    private java.util.function.Consumer<SqlEditorTab> onSaveRequest = SqlEditorTab::markSaved;
    /** Connection used by tabs that have none of their own (toolbar selection). */
    private java.util.function.Supplier<String> activeConnectionSupplier = () -> null;

    public void setOnSaveRequest(java.util.function.Consumer<SqlEditorTab> handler) {
        if (handler != null) this.onSaveRequest = handler;
    }

    public void setActiveConnectionSupplier(java.util.function.Supplier<String> supplier) {
        if (supplier != null) this.activeConnectionSupplier = supplier;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTabPanes();
        app.services.ExplorerConnectionState.getInstance().revisionProperty()
                .addListener((o, a, b) -> refreshTabDecorations());
        app.services.ConnectionColorService.getInstance().revisionProperty()
                .addListener((o, a, b) -> refreshTabDecorations());
        openNewTab("console_1.sql", DEFAULT_SQL);
        restoreActiveTabFromPreferences();
        updateEmptyState();

        // Deferred idle warmup: wait 800ms after window layout stabilizes before initializing WebView & Monaco.
        // This eliminates the 3-4s startup freeze (fx-stall) and lets the window appear instantaneously.
        monacoWarmupDelay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(800));
        monacoWarmupDelay.setOnFinished(e -> ensureMonacoLoaded());
        monacoWarmupDelay.play();

        // Also trigger load immediately on user interaction
        primaryTabPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> ensureMonacoLoaded());
        primaryTabPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> ensureMonacoLoaded());
        secondaryTabPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> ensureMonacoLoaded());
        secondaryTabPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> ensureMonacoLoaded());
    }

    private boolean monacoInitializing = false;
    private javafx.animation.PauseTransition monacoWarmupDelay;

    public synchronized void ensureMonacoLoaded() {
        if (monacoHost != null || monacoInitializing) return;
        monacoInitializing = true;
        if (monacoWarmupDelay != null) {
            monacoWarmupDelay.stop();
        }
        setupMonacoHost();
    }

    private void setupMonacoHost() {
        var profiler = app.diagnostics.startup.StartupProfiler.getInstance();
        try (var task = profiler.beginTask("MonacoHost_Init")) {
            monacoHost = new app.ui.editor.MonacoEditorHost();
            monacoHost.setOnContentChanged((modelId, content) -> {
                for (SqlEditorTab tab : getOpenTabs()) {
                    if (tab.getDocumentId().equals(modelId)) {
                        tab.setCurrentContent(content);
                        scheduleSyntaxCheck(modelId, content);
                        break;
                    }
                }
            });
            // the bar under the editor is fed straight from the editor's events (not only through the status bar)
            final app.ui.editor.EditorBar bar = monacoHost.getEditorBar();
            bar.setOnZoomRequested(pct -> {
                monacoHost.setZoom(pct);
                if (onZoomChangedListener != null) onZoomChangedListener.accept(pct);   // status bar keeps the state
            });
            monacoHost.setOnCursorPositionChanged((line, col) -> {
                lastCursorLine = line;
                lastCursorCol = col;
                bar.setCursor(line, col);
                if (cursorReporter != null) {
                    cursorReporter.accept(line, col);
                }
            });
            monacoHost.setOnExecuteRequested(() -> {
                if (onExecuteRequestListener != null) {
                    onExecuteRequestListener.run();
                }
            });
            monacoHost.setOnExecuteStatementRequested(() -> {
                if (onExecuteStatementRequestListener != null) {
                    onExecuteStatementRequestListener.run();
                }
            });
            primaryTabPane.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
                if (b instanceof SqlEditorTab st) {
                    reportProblems(st.getDocumentId());
                    Platform.runLater(this::checkActiveTabIfSessionChanged);
                } else if (onProblemsChangedListener != null) {
                    onProblemsChangedListener.accept(-1, 0);
                }
            });
            monacoHost.applyAppTheme();   // first frame: the editor shows the theme, not Monaco's default
            if (onEditorBarReady != null) onEditorBarReady.accept(monacoHost.getEditorBar());
            monacoHost.setOnZoomChanged(pct -> {
                bar.setZoom(pct);
                if (onZoomChangedListener != null) {
                    onZoomChangedListener.accept(pct);
                }
            });
            // Settings may have been applied before the Monaco host existed
            monacoHost.updateEditorSettings(app.services.SettingsService.getInstance().getCurrentSettings());
            pushIntellisense();
            monacoHost.setOnOpenScript(this::openNewTab);
            monacoHost.setOnReady(() -> {
                Tab selected = primaryTabPane.getSelectionModel().getSelectedItem();
                if (selected instanceof SqlEditorTab activeTab) {
                    // Sync latest content into Monaco model
                    monacoHost.updateContent(activeTab.getDocumentId(), activeTab.getCurrentContent());
                    activeTab.setEditorNode(monacoHost);
                    monacoHost.switchModel(activeTab.getDocumentId());
                }
            });

            // Pre-register any tabs that were opened during immediate launch
            for (SqlEditorTab tab : getOpenTabs()) {
                monacoHost.createModel(tab.getDocumentId(), tab.getCurrentContent(), tab.getLanguage());
            }
        } catch (Throwable t) {
            monacoHost = null;
            monacoInitializing = false;
        }
    }

    private void restoreActiveTabFromPreferences() {
        String savedActiveTab = app.services.LayoutService.getInstance().getActiveEditorTabTitle();
        if (savedActiveTab != null && !savedActiveTab.isBlank()) {
            for (Tab t : primaryTabPane.getTabs()) {
                if (t instanceof SqlEditorTab st && st.getTabTitle().equals(savedActiveTab)) {
                    primaryTabPane.getSelectionModel().select(st);
                    break;
                }
            }
        }
    }

    public void updateEmptyState() {
        long tabCount = primaryTabPane.getTabs().stream()
            .filter(t -> t instanceof SqlEditorTab).count();
        if (isSplit && secondaryTabPane != null) {
            tabCount += secondaryTabPane.getTabs().stream()
                .filter(t -> t instanceof SqlEditorTab).count();
        }
        boolean empty = (tabCount == 0);

        if (emptyEditorStateBox != null) {
            emptyEditorStateBox.setVisible(empty);
            emptyEditorStateBox.setManaged(empty);
        }
        if (editorSplitContainer != null) {
            editorSplitContainer.setVisible(!empty);
            editorSplitContainer.setManaged(!empty);
        }
        if (tabHeaderActions != null) {
            tabHeaderActions.setVisible(!empty);
            tabHeaderActions.setManaged(!empty);
        }
    }

    private void setupTabPanes() {
        primaryTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        secondaryTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // Hide secondary split initially
        if (editorSplitContainer != null && editorSplitContainer.getItems().contains(secondaryTabPane)) {
            editorSplitContainer.getItems().remove(secondaryTabPane);
        }

        // Phase 3 follow-up: own tab strip above each TabPane (header hidden) so tabs can wrap,
        // fade when they overflow, and never run under the actions group.
        if (tabHeaderActions != null && tabHeaderActions.getParent() instanceof javafx.scene.layout.Pane p) {
            p.getChildren().remove(tabHeaderActions);
        }
        primaryWrap = app.ui.editor.EditorTabStrip.wrap(primaryTabPane, this::handleNewTab, tabHeaderActions);
        secondaryWrap = app.ui.editor.EditorTabStrip.wrap(secondaryTabPane, this::handleNewTab, null);
        int pIdx = editorSplitContainer.getItems().indexOf(primaryTabPane);
        editorSplitContainer.getItems().remove(primaryTabPane);
        editorSplitContainer.getItems().add(Math.max(0, pIdx), primaryWrap);

        primaryTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab instanceof SqlEditorTab sqlTab && sqlTab.isCustomContent()) {
                // view tab (Permissions…): no editor model, no cursor; the status bar still follows its connection
                if (tabSelectionListener != null) tabSelectionListener.accept(sqlTab);
                return;
            }
            if (newTab instanceof SqlEditorTab sqlTab) {
                if (monacoHost != null && monacoHost.isEditorReady()) {
                    if (oldTab instanceof SqlEditorTab oldSqlTab) {
                        oldSqlTab.setEditorNode(null);
                    }
                    sqlTab.setEditorNode(monacoHost);
                    monacoHost.switchModel(sqlTab.getDocumentId());
                } else {
                    sqlTab.setEditorNode(sqlTab.getCodeArea());
                }
                bindCursorReporting(sqlTab.getCodeArea());
                app.services.LayoutService.getInstance().setActiveEditorTabTitle(sqlTab.getTabTitle());
                if (tabSelectionListener != null) {
                    tabSelectionListener.accept(sqlTab);
                }
            }
        });


        secondaryTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab instanceof SqlEditorTab sqlTab) {
                bindCursorReporting(sqlTab.getCodeArea());
                app.services.LayoutService.getInstance().setActiveEditorTabTitle(sqlTab.getTabTitle());
                if (tabSelectionListener != null) {
                    tabSelectionListener.accept(sqlTab);
                }
            }
        });
    }

    private void bindCursorReporting(SqlCodeArea codeArea) {
        if (codeArea != null) {
            codeArea.setCursorChangeListener((line, col) -> {
                lastCursorLine = line;
                lastCursorCol = col;
                if (cursorReporter != null) {
                    cursorReporter.accept(line, col);
                }
            });
            if (cursorReporter != null) {
                cursorReporter.accept(codeArea.getCurrentLine(), codeArea.getCurrentColumn());
            }
        }
    }

    public SqlEditorTab openNewTab(String title, String sqlContent) {
        return openTabWithFile(title, sqlContent, null, null, null, null, true);
    }

    public SqlEditorTab openNewTab(String title, String sqlContent, String connectionName, String databaseName, String schemaName) {
        return openTabWithFile(title, sqlContent, null, connectionName, databaseName, schemaName, true);
    }

    public SqlEditorTab openTabWithFile(String title, String sqlContent, java.nio.file.Path filePath, boolean select) {
        return openTabWithFile(title, sqlContent, filePath, null, null, null, select);
    }

    public SqlEditorTab openTabWithFile(String title, String sqlContent, java.nio.file.Path filePath,
                                        String connectionName, String databaseName, String schemaName, boolean select) {
        String finalTitle = title != null ? title : (filePath != null ? filePath.getFileName().toString() : ("console_" + (tabCounter++) + ".sql"));
        String finalSql = sqlContent != null ? sqlContent : "-- Write your SQL query here\n";

        SqlEditorTab newTab = new SqlEditorTab(finalTitle, finalSql, filePath, connectionName, databaseName, schemaName, this::handleTabAction);

        newTab.setOnClosed(e -> {
            updateEmptyState();
        });

        primaryTabPane.getTabs().add(newTab);
        newTab.pinnedProperty().addListener((o, was, pinned) -> movePinned(newTab, pinned));

        if (monacoHost != null) {
            monacoHost.createModel(newTab.getDocumentId(), finalSql, newTab.getLanguage());
        }

        updateEmptyState();
        refreshTabDecorations();

        // Default behavior: when creating or opening a tab, do not show results panel
        app.services.LayoutService.getInstance().setResultsVisible(false);

        if (select) {
            primaryTabPane.getSelectionModel().select(newTab);
            bindCursorReporting(newTab.getCodeArea());
            if (tabSelectionListener != null) {
                tabSelectionListener.accept(newTab);
            }
        }

        return newTab;
    }

    public void closeCurrentTab() {
        SqlEditorTab active = getActiveTab();
        if (active != null) {
            closeTab(active);
        }
    }

    public void setTabSelectionListener(java.util.function.Consumer<SqlEditorTab> listener) {
        this.tabSelectionListener = listener;
    }

    public List<SqlEditorTab> getOpenTabs() {
        return primaryTabPane.getTabs().stream()
                .filter(t -> t instanceof SqlEditorTab st && !st.isCustomContent())
                .map(t -> (SqlEditorTab) t)
                .toList();
    }

    /**
     * Opens (or selects, when {@code key} is already open) a view tab such as "Permissions: name" in the editor area.
     * The tab is not a SQL document: no editor model, not saved with the workspace, nothing to execute.
     */
    public SqlEditorTab openViewTab(String key, String title, java.util.function.Supplier<javafx.scene.Node> viewFactory,
                                    String connectionName, String databaseName) {
        for (Tab t : primaryTabPane.getTabs()) {
            if (t instanceof SqlEditorTab st && key.equals(st.getCustomKey())) {
                primaryTabPane.getSelectionModel().select(st);
                return st;
            }
        }
        SqlEditorTab tab = new SqlEditorTab(title, "", null, connectionName, databaseName, null, this::handleTabAction);
        tab.setCustomContent(key, viewFactory.get());
        tab.setOnClosed(e -> updateEmptyState());
        primaryTabPane.getTabs().add(tab);
        tab.pinnedProperty().addListener((o, was, pinned) -> movePinned(tab, pinned));
        updateEmptyState();
        refreshTabDecorations();
        primaryTabPane.getSelectionModel().select(tab);
        return tab;
    }

    public void selectTab(SqlEditorTab tab) {
        if (tab != null) primaryTabPane.getSelectionModel().select(tab);
    }

    public SqlEditorTab getActiveTab() {
        Tab selected = primaryTabPane.getSelectionModel().getSelectedItem();
        return (selected instanceof SqlEditorTab) ? (SqlEditorTab) selected : null;
    }

    public void clearAllTabsSilently() {
        List<Tab> toRemove = primaryTabPane.getTabs().stream()
                .filter(t -> t instanceof SqlEditorTab)
                .toList();
        primaryTabPane.getTabs().removeAll(toRemove);
        updateEmptyState();
    }

    // ------------------------------------------------------------------ Phase 3: connection dot + banner

    /** Connection colour dot on every tab and the "Chưa kết nối" banner (mock state from the Explorer). */
    public void refreshTabDecorations() {
        java.util.Map<String, com.lltool.core.application.connection.dto.ConnectionProfileDto> byName = new java.util.HashMap<>();
        for (var p : app.framework.connection.ProfileLookup.all()) if (p.name() != null) byName.put(p.name(), p);
        for (SqlEditorTab tab : getOpenTabs()) {
            String name = tab.getConnectionName() != null && !tab.getConnectionName().isBlank()
                    ? tab.getConnectionName() : activeConnectionSupplier.get();
            var profile = name != null ? byName.get(name) : null;
            if (profile == null) {
                tab.setConnectionIcon(null);
                tab.setConnectionColor(null);
                tab.updateConnectionBanner(null, null, null);
                continue;
            }
            boolean connected;
            String dot;
            app.ui.editor.SqlEditorTab.BannerState bannerState = null;
            String bannerError = null;
            if (app.framework.backend.BackendFlags.real(app.framework.backend.BackendFlags.Area.CONNECTIONS)) {
                // Phase 8 / D1: every tab shows ITS OWN session (user decision 2026-11-05)
                var session = app.framework.session.TabSessionManager.getInstance().get(tab.getDocumentId());
                var st = session != null && session.profile() != null && profile.id().equals(session.profile().id())
                        ? session.state() : app.framework.session.TabSession.State.NONE;
                connected = st == app.framework.session.TabSession.State.CONNECTED;
                dot = switch (st) {
                    case CONNECTED -> app.services.ConnectionColorService.getInstance().colorFor(profile.id()).hex();
                    case CONNECTING -> "#d29922";
                    case ERROR, LOST -> "#f85149";
                    default -> "#8b8b8b";
                };
                bannerState = switch (st) {
                    case CONNECTED -> app.ui.editor.SqlEditorTab.BannerState.HIDDEN;
                    case CONNECTING -> app.ui.editor.SqlEditorTab.BannerState.CONNECTING;
                    case LOST -> app.ui.editor.SqlEditorTab.BannerState.LOST;
                    default -> app.ui.editor.SqlEditorTab.BannerState.DISCONNECTED;
                };
                bannerError = st == app.framework.session.TabSession.State.LOST && session != null ? session.error() : null;
                tab.setSessionTooltip(sessionTooltip(profile, session, st));
            } else {
                var status = app.services.ExplorerConnectionState.getInstance().get(profile.id());
                connected = status == app.services.ExplorerConnectionState.Status.CONNECTED;
                dot = connected ? app.services.ConnectionColorService.getInstance().colorFor(profile.id()).hex() : "#8b8b8b";
            }
            tab.setConnectionIcon(dbLogo(profile.databaseType()));
            // status dot: connection colour when connected, grey otherwise
            tab.setConnectionColor(dot);
            if (connected) tab.resetBannerDismissed();
            String db = tab.getDatabaseName() != null ? tab.getDatabaseName() : profile.databaseName();
            if (bannerState != null) tab.updateConnectionBanner(profile.name(), db, bannerState, bannerError);
            else tab.updateConnectionBanner(profile.name(), db, connected);
        }
    }

    private static String sessionTooltip(com.lltool.core.application.connection.dto.ConnectionProfileDto profile,
                                         app.framework.session.TabSession session,
                                         app.framework.session.TabSession.State st) {
        StringBuilder sb = new StringBuilder(profile.name());
        switch (st) {
            case CONNECTED -> {
                sb.append("\nĐã kết nối").append(session.database() != null ? " · " + session.database() : "");
                if (session.serverPid() != null) sb.append(" · PID ").append(session.serverPid());
                if (session.readOnly()) sb.append("\nChỉ đọc");
            }
            case CONNECTING -> sb.append("\nĐang kết nối…");
            case LOST -> sb.append("\nMất kết nối").append(session.error() != null ? ": " + session.error() : "");
            case ERROR -> sb.append("\nLỗi: ").append(session.error());
            default -> sb.append("\nChưa kết nối (phiên riêng của tab)");
        }
        return sb.toString();
    }

    /** Jumps to a 1-based line of the active tab (Actions Guard double-click). */
    public void gotoLine(int line) {
        if (monacoHost != null && monacoHost.isEditorReady()) {
            monacoHost.gotoLine(line);
            return;
        }
        SqlEditorTab tab = getCurrentEditorTab();
        if (tab == null) return;
        var area = tab.getCodeArea();
        int p = Math.max(0, Math.min(line - 1, area.getParagraphs().size() - 1));
        area.moveTo(p, 0);
        area.selectLine();
        area.requestFollowCaret();
        area.requestFocus();
    }

    /** Replaces the whole text of the active tab (e.g. DLG-010 "Comment hết bs.*"). Undoable in Monaco via Ctrl+Z is not kept. */
    public void replaceActiveContent(String text) {
        SqlEditorTab tab = getCurrentEditorTab();
        if (tab == null || text == null) return;
        tab.setCurrentContent(text);
        if (monacoHost != null) monacoHost.updateContent(tab.getDocumentId(), text);
    }

    private static final java.util.Map<String, javafx.scene.image.Image> LOGOS = new java.util.HashMap<>();

    private static javafx.scene.image.Image dbLogo(String dbType) {
        String t = dbType == null ? "" : dbType.toUpperCase();
        String file = t.contains("SQLITE") ? "sqlite.png"
                : (t.contains("SQLSERVER") || t.contains("SQL SERVER") || t.contains("MSSQL")) ? "sqlserver.png" : "postgresql.png";
        return LOGOS.computeIfAbsent(file, f -> {
            var url = EditorController.class.getResource("/static/icons/" + f);
            return url != null ? new javafx.scene.image.Image(url.toExternalForm(), 28, 28, true, true) : null;
        });
    }

    private javafx.stage.Window owner() {
        return editorContainer != null && editorContainer.getScene() != null ? editorContainer.getScene().getWindow() : null;
    }

    @FXML
    public void handleNewTab() {
        openNewTab(null, null);
    }

    public void handleTabAction(SqlEditorTab.SqlEditorTabAction action) {
        SqlEditorTab tab = action.getTab();
        switch (action.getType()) {
            case CLOSE -> closeTab(tab);
            case CLOSE_OTHERS -> closeOtherTabs(tab);
            case CLOSE_ALL -> closeAllTabs();
            case CLOSE_UNMODIFIED -> closeUnmodifiedTabs();
            case SPLIT_RIGHT -> splitRight(tab);
            case SPLIT_DOWN -> splitDown(tab);
            case SPLIT_LEFT -> enableSplit(tab, Orientation.HORIZONTAL, true);
            case SPLIT_UP -> enableSplit(tab, Orientation.VERTICAL, true);
            case CLOSE_SPLIT -> closeSplit();
            case SAVE -> onSaveRequest.accept(tab);
            case CLOSE_RIGHT -> closeTabsToRight(tab);
            case DUPLICATE -> duplicateTab(tab);
        }
    }

    public void closeTabsToRight(SqlEditorTab target) {
        TabPane pane = target.getTabPane();
        if (pane == null) return;
        int idx = pane.getTabs().indexOf(target);
        List<SqlEditorTab> toClose = new ArrayList<>();
        for (int i = idx + 1; i < pane.getTabs().size(); i++) {
            if (pane.getTabs().get(i) instanceof SqlEditorTab st && !st.isPinned()) toClose.add(st);
        }
        toClose.forEach(this::closeTab);
    }

    /**
     * Pinned tabs move to the front in the order they were pinned (user request 2026-11-05);
     * an unpinned tab goes right after the remaining pinned tabs.
     */
    private void movePinned(SqlEditorTab tab, boolean pinned) {
        TabPane pane = tab.getTabPane();
        if (pane == null) return;
        boolean wasSelected = pane.getSelectionModel().getSelectedItem() == tab;
        pane.getTabs().remove(tab);
        int pinnedCount = 0;
        for (Tab t : pane.getTabs()) {
            if (t instanceof SqlEditorTab st && st.isPinned()) pinnedCount++;
        }
        pane.getTabs().add(Math.min(pinnedCount, pane.getTabs().size()), tab);
        if (wasSelected) pane.getSelectionModel().select(tab);
    }

    public void duplicateTab(SqlEditorTab source) {
        openTabWithFile(source.getTabTitle() + " (bản sao)", source.getCurrentContent(), null,
                source.getConnectionName(), source.getDatabaseName(), source.getSchemaName(), true);
    }

    public void closeTab(SqlEditorTab tab) {
        if (tab.isPinned()) return;

        if (tab.isDirty()) {
            switch (app.ui.dialogs.CloseTabDialog.show(owner(), tab.getTabTitle())) {
                case SAVE_AND_CLOSE -> {
                    onSaveRequest.accept(tab);
                    removeTab(tab);
                }
                case CLOSE_WITHOUT_SAVING -> removeTab(tab);
                default -> { }
            }
        } else {
            removeTab(tab);
        }
    }

    private void removeTab(SqlEditorTab tab) {
        if (monacoHost != null) {
            monacoHost.disposeModel(tab.getDocumentId());
            app.framework.syntax.SyntaxCheckService.getInstance().forget(tab.getDocumentId());
            problemCounts.remove(tab.getDocumentId());
            lastCheckKey.remove(tab.getDocumentId());
        }
        // Phase 8 / D1: the tab's own database session closes with the tab
        app.framework.query.DesktopQueryExecutionService.getInstance().cancel(tab.getDocumentId()); // D3: stop its run first
        app.framework.session.TabSessionManager.getInstance().release(tab.getDocumentId());
        if (primaryTabPane.getTabs().contains(tab)) {
            primaryTabPane.getTabs().remove(tab);
            updateEmptyState();
        } else if (secondaryTabPane.getTabs().contains(tab)) {
            secondaryTabPane.getTabs().remove(tab);
            if (secondaryTabPane.getTabs().isEmpty() && isSplit) {
                closeSplit();
            }
            updateEmptyState();
        }
    }

    public void closeOtherTabs(SqlEditorTab target) {
        TabPane parentPane = target.getTabPane();
        if (parentPane == null) return;

        List<Tab> toClose = new ArrayList<>();
        for (Tab t : parentPane.getTabs()) {
            if (t != target && t instanceof SqlEditorTab st && !st.isPinned()) {
                toClose.add(t);
            }
        }
        for (Tab t : toClose) {
            if (t instanceof SqlEditorTab st) {
                closeTab(st);
            }
        }
    }

    public void closeAllTabs() {
        List<Tab> allTabs = new ArrayList<>(primaryTabPane.getTabs());
        allTabs.addAll(secondaryTabPane.getTabs());
        for (Tab t : allTabs) {
            if (t instanceof SqlEditorTab st && !st.isPinned()) {
                closeTab(st);
            }
        }
    }

    public void closeUnmodifiedTabs() {
        List<Tab> tabs = new ArrayList<>(primaryTabPane.getTabs());
        tabs.addAll(secondaryTabPane.getTabs());
        for (Tab t : tabs) {
            if (t instanceof SqlEditorTab st && !st.isDirty() && !st.isPinned()) {
                removeTab(st);
            }
        }
    }

    @FXML
    public void handleSplitRight() {
        Tab active = primaryTabPane.getSelectionModel().getSelectedItem();
        if (active instanceof SqlEditorTab st) {
            splitRight(st);
        }
    }

    @FXML
    public void handleSplitDown() {
        Tab active = primaryTabPane.getSelectionModel().getSelectedItem();
        if (active instanceof SqlEditorTab st) {
            splitDown(st);
        }
    }

    public void splitRight(SqlEditorTab tab) {
        enableSplit(tab, Orientation.HORIZONTAL, false);
    }

    public void splitDown(SqlEditorTab tab) {
        enableSplit(tab, Orientation.VERTICAL, false);
    }

    /** {@code before}: the new pane goes left / above (LLTool "Sang trái", "Lên trên"). */
    private void enableSplit(SqlEditorTab tab, Orientation orientation, boolean before) {
        editorSplitContainer.getItems().remove(secondaryWrap);
        if (before) editorSplitContainer.getItems().add(0, secondaryWrap);
        else editorSplitContainer.getItems().add(secondaryWrap);
        editorSplitContainer.setDividerPosition(0, 0.5);
        editorSplitContainer.setOrientation(orientation);

        // Create a duplicate/split view tab
        SqlEditorTab splitTab = new SqlEditorTab(
            tab.getTabTitle() + " (chia)",
            tab.getCodeArea().getText(),
            tab.getFilePath(),
            tab.getConnectionName(),
            tab.getDatabaseName(),
            tab.getSchemaName(),
            this::handleTabAction
        );

        secondaryTabPane.getTabs().add(splitTab);
        secondaryTabPane.getSelectionModel().select(splitTab);

        isSplit = true;
        if (closeSplitButton != null) {
            closeSplitButton.setVisible(true);
            closeSplitButton.setManaged(true);
        }
    }

    @FXML
    public void closeSplit() {
        if (editorSplitContainer.getItems().contains(secondaryWrap)) {
            editorSplitContainer.getItems().remove(secondaryWrap);
            secondaryTabPane.getTabs().clear();
        }
        isSplit = false;
        if (closeSplitButton != null) {
            closeSplitButton.setVisible(false);
            closeSplitButton.setManaged(false);
        }
    }

    @FXML
    public void handleFormatSql() {
        if (getCurrentEditorTab() == null || getCurrentEditorTab().isCustomContent()) return;
        if (!app.ui.dialogs.FormatConfirmDialog.confirm(owner())) return; // SCR-DLG-008
        if (monacoHost != null && monacoHost.isEditorReady()) {
            monacoHost.formatDocument();
        } else {
            SqlEditorTab current = getCurrentEditorTab();
            if (current != null) {
                String text = current.getCurrentContent();
                String formatted = basicSqlFormat(text);
                current.setCurrentContent(formatted);
            }
        }
    }

    public String getActiveText() {
        SqlEditorTab active = getCurrentEditorTab();
        if (active == null || active.isCustomContent()) return "";
        if (monacoHost != null && monacoHost.isEditorReady()) {
            String content = monacoHost.getContent(active.getDocumentId());
            if (content != null && !content.isEmpty()) {
                return content;
            }
        }
        return active.getCurrentContent();
    }

    /** Selection if any, else the statement under the caret, else the whole text. */
    public String getSelectedOrStatementAtCursor() {
        SqlEditorTab active = getCurrentEditorTab();
        if (active == null) return "";
        String selection = null;
        if (monacoHost != null && monacoHost.isEditorReady()) {
            selection = monacoHost.getSelectedText();
        } else {
            SqlCodeArea area = getActiveCodeArea();
            if (area != null) selection = area.getSelectedText();
        }
        if (selection != null && !selection.isBlank()) {
            return selection;
        }
        String all = getActiveText();
        return app.framework.query.SqlStatementSplitter.atCursor(all, lastCursorLine, lastCursorCol)
                .map(app.framework.query.SqlStatementSplitter.Part::text)
                .orElse(all);
    }

    public String getSelectedOrActiveText() {
        SqlEditorTab active = getCurrentEditorTab();
        if (active == null || active.isCustomContent()) return "";
        if (monacoHost != null && monacoHost.isEditorReady()) {
            String selected = monacoHost.getSelectedText();
            if (selected != null && !selected.isBlank()) {
                return selected;
            }
            String content = monacoHost.getContent(active.getDocumentId());
            if (content != null && !content.isEmpty()) {
                return content;
            }
        }
        SqlCodeArea area = getActiveCodeArea();
        if (area != null) {
            String selected = area.getSelectedText();
            if (selected != null && !selected.isBlank()) {
                return selected;
            }
        }
        return active.getCurrentContent();
    }

    /** Phase 8 / D3: Ctrl+E = run the statement under the caret. */
    public void setOnExecuteStatementRequestListener(Runnable listener) {
        this.onExecuteStatementRequestListener = listener;
        if (monacoHost != null) {
            monacoHost.setOnExecuteStatementRequested(listener);
        }
    }

    public void setOnExecuteRequestListener(Runnable listener) {
        this.onExecuteRequestListener = listener;
        if (monacoHost != null) {
            monacoHost.setOnExecuteRequested(listener);
        }
    }

    public app.ui.editor.MonacoEditorHost getMonacoHost() {
        return monacoHost;
    }

    private String basicSqlFormat(String sql) {
        if (sql == null || sql.isBlank()) return sql;
        // Basic PoC formatter: capitalize standard keywords
        String[] keywords = {"select", "from", "where", "join", "inner join", "left join", "right join",
            "group by", "order by", "having", "limit", "offset", "insert into", "values", "update", "set", "delete from"};

        String result = sql;
        for (String kw : keywords) {
            result = result.replaceAll("(?i)\\b" + kw + "\\b", kw.toUpperCase());
        }
        return result;
    }

    public SqlEditorTab getCurrentEditorTab() {
        Tab selected = primaryTabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof SqlEditorTab st) {
            return st;
        }
        selected = secondaryTabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof SqlEditorTab st) {
            return st;
        }
        return null;
    }

    public SqlCodeArea getActiveCodeArea() {
        SqlEditorTab current = getCurrentEditorTab();
        return current != null ? current.getCodeArea() : null;
    }

    public void setCursorReporter(BiConsumer<Integer, Integer> cursorReporter) {
        this.cursorReporter = cursorReporter;
    }

    public TabPane getPrimaryTabPane() {
        return primaryTabPane;
    }

    public boolean isSplit() {
        return isSplit;
    }

    public VBox getEmptyEditorStateBox() {
        return emptyEditorStateBox;
    }

    private java.util.function.IntConsumer onZoomChangedListener;
    private java.util.function.Consumer<app.ui.editor.EditorBar> onEditorBarReady;

    /** The bar under the editor: told once (now if the editor already exists). */
    /** The bar under the editor, or null while the editor is not created. */
    public app.ui.editor.EditorBar getEditorBar() {
        return monacoHost != null ? monacoHost.getEditorBar() : null;
    }

    public void setOnEditorBarReady(java.util.function.Consumer<app.ui.editor.EditorBar> listener) {
        this.onEditorBarReady = listener;
        if (listener != null && monacoHost != null) listener.accept(monacoHost.getEditorBar());
    }

    // ---- syntax check (Phase 8 / D5) ----
    private java.util.function.BiConsumer<Integer, Integer> onProblemsChangedListener;
    private final java.util.Map<String, int[]> problemCounts = new java.util.HashMap<>();
    private final java.util.Map<String, String> lastCheckKey = new java.util.HashMap<>();

    /** Errors / warnings of the active tab (status bar). */
    public void setOnProblemsChanged(java.util.function.BiConsumer<Integer, Integer> listener) {
        this.onProblemsChangedListener = listener;
    }

    private void scheduleSyntaxCheck(String tabId, String content) {
        var s = app.services.SettingsService.getInstance().getCurrentSettings();
        boolean enabled = s != null && s.getEditor() != null && s.getEditor().isSyntaxCheck();
        app.framework.syntax.SyntaxCheckService.getInstance().schedule(tabId, content, enabled, outcome -> {
            int errors = outcome.checked() ? outcome.diagnostics().size() : -1;   // -1: not checked
            problemCounts.put(tabId, new int[]{errors, 0});
            if (monacoHost != null) monacoHost.setMarkers(tabId, outcome.checked() ? outcome.diagnostics() : java.util.List.of());
            Tab sel = primaryTabPane.getSelectionModel().getSelectedItem();
            if (sel instanceof SqlEditorTab st && st.getDocumentId().equals(tabId)) reportProblems(tabId);
        });
    }

    private void reportProblems(String tabId) {
        int[] c = problemCounts.get(tabId);
        if (onProblemsChangedListener != null) onProblemsChangedListener.accept(c == null ? -1 : c[0], c == null ? 0 : c[1]);
    }

    /**
     * Re-checks the active tab when its session changed (connected / switched database / reconnected) and
     * refreshes the status bar counts. Cheap: compares a key, no I/O.
     */
    public void checkActiveTabIfSessionChanged() {
        Tab sel = primaryTabPane.getSelectionModel().getSelectedItem();
        if (!(sel instanceof SqlEditorTab st) || st.isCustomContent()) return;
        String id = st.getDocumentId();
        var session = app.framework.session.TabSessionManager.getInstance().get(id);
        String key = session == null ? "none"
                : session.state() + "|" + (session.profile() == null ? "" : session.profile().id()) + "|" + session.database();
        if (!key.equals(lastCheckKey.put(id, key))) {
            scheduleSyntaxCheck(id, st.getCurrentContent());
        }
        reportProblems(id);
        pushIntellisense();
    }

    private String lastIntellisenseKey;

    /** Sends the session of the active tab + IntelliSense Settings to the editor (only when something changed). */
    private void pushIntellisense() {
        if (monacoHost == null) return;
        var s = app.services.SettingsService.getInstance().getCurrentSettings();
        var e = s == null ? null : s.getEditor();
        boolean enabled = e == null || e.isIntellisense();
        boolean mru = e == null || e.isIntellisenseMru();
        boolean detail = e == null || e.isIntellisenseDetail();
        String detailMode = e == null ? "always" : e.getIntellisenseDetailMode();
        String kc = e == null || e.getKeywordCase() == null ? "upper" : e.getKeywordCase();
        String dialect = "OTHER";
        String connectionId = null, database = null, schema = null;
        Tab sel = primaryTabPane.getSelectionModel().getSelectedItem();
        if (sel instanceof SqlEditorTab st) {
            var session = app.framework.session.TabSessionManager.getInstance().get(st.getDocumentId());
            if (session != null && session.profile() != null) {
                dialect = app.framework.query.ScriptRunner.Dialect.of(session.profile().databaseType()).name();
                // Without a live session: keywords and snippets only, no reads from a server the tab isn't on
                if (session.state() == app.framework.session.TabSession.State.CONNECTED) {
                    connectionId = session.profile().id();
                    database = session.database();
                    schema = app.services.DefaultSchemaService.get(connectionId);
                }
            }
        }
        String key = enabled + "|" + mru + "|" + detail + "|" + detailMode + "|" + kc + "|" + dialect
                + "|" + connectionId + "|" + database + "|" + schema;
        if (key.equals(lastIntellisenseKey)) return;
        lastIntellisenseKey = key;
        monacoHost.setIntellisense(new app.ui.editor.MonacoEditorHost.IntellisenseContext(enabled, dialect, kc, mru, detail, detailMode,
                connectionId, database, schema));
        app.framework.intellisense.IntellisensePerf.log("editor context changed, connected=" + (connectionId != null));
        // read once per connection, in the background, before the first suggestion needs it
        if (enabled && connectionId != null && sel instanceof SqlEditorTab st) {
            var session = app.framework.session.TabSessionManager.getInstance().get(st.getDocumentId());
            if (session != null && session.profile() != null) {
                app.framework.intellisense.MetadataSnapshotStore.getInstance().preload(session.profile(), database);
            }
        }
    }

    /** "Refresh Objects & IntelliSense": the suggestions of the active tab's connection are read from the server again. */
    public void reloadIntellisense() {
        Tab sel = primaryTabPane.getSelectionModel().getSelectedItem();
        if (!(sel instanceof SqlEditorTab st)) return;
        var session = app.framework.session.TabSessionManager.getInstance().get(st.getDocumentId());
        if (session == null || session.profile() == null || session.state() != app.framework.session.TabSession.State.CONNECTED) return;
        var profile = session.profile();
        String database = session.database();
        Thread.ofVirtual().name("intellisense-reload").start(() ->
                app.framework.intellisense.MetadataSnapshotStore.getInstance().reload(profile, database, true));
    }

    /** After a run: objects created, changed or dropped from this tab appear in the suggestions without a refresh. */
    public void reloadIntellisenseAfterRun(String tabId, String sql) {
        var session = app.framework.session.TabSessionManager.getInstance().get(tabId);
        if (session == null || session.profile() == null) return;
        var profile = session.profile();
        String database = session.database();
        Thread.ofVirtual().name("intellisense-ddl-check").start(() -> {
            if (app.framework.intellisense.MetadataSnapshotStore.changesObjects(sql)) {
                app.framework.intellisense.MetadataSnapshotStore.getInstance().reload(profile, database, false);
            }
        });
    }

    /** Called when the user zooms with Ctrl+Wheel inside the editor (percent of the Settings font size). */
    public void setOnZoomChanged(java.util.function.IntConsumer listener) {
        this.onZoomChangedListener = listener;
    }

    public void applyEditorSettings(app.models.IdeSettings s) {
        if (s == null) return;
        if (monacoHost != null) {
            monacoHost.updateEditorSettings(s);
            pushIntellisense();
        }
    }
}
