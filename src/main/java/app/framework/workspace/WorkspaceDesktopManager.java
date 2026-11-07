package app.framework.workspace;

import app.application.AppContext;
import app.controllers.EditorController;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.lifecycle.DesktopLifecycleManager;
import app.framework.state.GlobalDesktopState;
import app.services.LayoutService;
import app.services.ThemeService;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.api.request.CreateWorkspaceRequest;
import com.lltool.core.api.request.SaveWorkspaceStateRequest;
import com.lltool.core.api.workspace.WorkspaceApi;
import com.lltool.core.application.workspace.dto.PanelLayoutDto;
import com.lltool.core.application.workspace.dto.TabStateDto;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.application.workspace.dto.WorkspaceStateDto;
import com.lltool.core.common.result.Result;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Desktop Framework Workspace Coordinator (Phase 6.1).
 * Coordinates workspace session persistence, tab restoration, layout state,
 * and handles missing files/projects gracefully.
 */
public class WorkspaceDesktopManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceDesktopManager.class);
    private static WorkspaceDesktopManager instance;

    private final WorkspaceApi workspaceApi;
    private final GlobalDesktopState desktopState;
    private WorkspaceDto currentWorkspace;

    // References to active UI controllers
    private EditorController editorController;

    private WorkspaceDesktopManager() {
        this.workspaceApi = DesktopBootstrap.getInstance().getWorkspaceApi();
        this.desktopState = GlobalDesktopState.getInstance();

        // Register automatic persistence on graceful application shutdown
        DesktopLifecycleManager.getInstance().addShutdownHook(this::saveCurrentWorkspaceState);
    }

    public static synchronized WorkspaceDesktopManager getInstance() {
        if (instance == null) {
            instance = new WorkspaceDesktopManager();
        }
        return instance;
    }

    public static synchronized void resetForTesting() {
        instance = null;
    }

    public void registerEditorController(EditorController editorController) {
        this.editorController = editorController;
        startTabAutoSave();
    }

    private final TabSnapshotStore tabStore = new TabSnapshotStore();
    private javafx.animation.Timeline autoSave;
    private String lastSnapshotJson;

    /** Builds the snapshot of every open SQL tab (FX thread). */
    private List<TabSnapshotStore.Snap> captureTabSnapshots() {
        List<TabSnapshotStore.Snap> out = new ArrayList<>();
        if (editorController == null) return out;
        SqlEditorTab active = editorController.getActiveTab();
        var session = sessionSettings();
        for (SqlEditorTab tab : editorController.getOpenTabs()) {
            // session.saveSqlContent: keep the text of untitled / unsaved tabs; session.maxTabSizeBytes: not bigger ones
            boolean keepText = session.isSaveSqlContent() && (tab.getFilePath() == null || tab.isDirty());
            String text = keepText ? tab.getCurrentContent() : null;
            if (text != null && tooBig(text, session.getMaxTabSizeBytes())) {
                log.info("Tab '{}' not kept in the snapshot: larger than session.maxTabSizeBytes", tab.getTabTitle());
                text = null;
            }
            out.add(new TabSnapshotStore.Snap(
                    tab.getTabTitle(),
                    tab.getFilePath() != null ? tab.getFilePath().toAbsolutePath().toString() : null,
                    text,
                    tab.isDirty(),
                    tab.getConnectionName(), tab.getDatabaseName(), tab.getSchemaName(),
                    tab.isPinned(), tab == active));
        }
        return out;
    }

    /** Writes the tab snapshot now (called on the FX thread); the file write itself runs off-thread unless {@code sync}. */
    public void saveTabSnapshots(boolean sync) {
        // never overwrite the user's saved tabs from a run without an editor (unit tests save the workspace
        // with no UI: that wrote an empty list over the real tab-snapshots.json, 2026-11-06)
        if (editorController == null || "mock".equalsIgnoreCase(System.getProperty("sqlide.backend"))) return;
        try {
            // periodic save: nothing changed since the last one -> do not even build the snapshot
            String signature = SqlEditorTab.editCount() + "|" + editorController.getOpenTabs().size() + "|"
                    + (editorController.getActiveTab() != null ? editorController.getActiveTab().getDocumentId() : "-");
            if (!sync && signature.equals(lastSignature)) return;
            lastSignature = signature;
            String json = tabStore.toJson(captureTabSnapshots());
            if (json.equals(lastSnapshotJson)) return;
            lastSnapshotJson = json;
            if (sync) tabStore.writeJson(json);
            else Thread.ofVirtual().start(() -> tabStore.writeJson(json));
        } catch (Throwable t) {
            log.warn("Tab snapshot failed: {}", t.getMessage());
        }
    }

    private String lastSignature;
    private int autoSaveSeconds = -1;

    /** session.autoSaveIntervalSeconds (LLTool default 3 s, at least 3); restarted when the setting changes. */
    private void startTabAutoSave() {
        restartAutoSave(sessionSettings().getAutoSaveIntervalSeconds());
        app.services.SettingsService.getInstance().addSettingsListener(s ->
                restartAutoSave(s.getSession() != null ? s.getSession().getAutoSaveIntervalSeconds() : 3));
    }

    private void restartAutoSave(int seconds) {
        int period = Math.max(3, seconds);
        if (autoSave != null && period == autoSaveSeconds) return;
        if (autoSave != null) autoSave.stop();
        autoSaveSeconds = period;
        autoSave = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(period), e -> saveTabSnapshots(false)));
        autoSave.setCycleCount(javafx.animation.Animation.INDEFINITE);
        autoSave.play();
    }

    private static app.models.IdeSettings.SessionSettings sessionSettings() {
        var s = app.services.SettingsService.getInstance().getCurrentSettings();
        return s != null && s.getSession() != null ? s.getSession() : new app.models.IdeSettings.SessionSettings();
    }

    /** UTF-8 size above the limit (0 or less = no limit); counts without building a byte array. */
    static boolean tooBig(String text, long maxBytes) {
        if (maxBytes <= 0 || text.length() <= maxBytes / 3) return false;
        if (text.length() > maxBytes) return true;
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) bytes++;
            else if (c < 0x800) bytes += 2;
            else if (Character.isHighSurrogate(c)) { bytes += 4; i++; }
            else bytes += 3;
            if (bytes > maxBytes) return true;
        }
        return false;
    }

    public WorkspaceDto getCurrentWorkspace() {
        return currentWorkspace;
    }

    /**
     * Initializes or restores the default/active workspace on application startup.
     */
    public synchronized WorkspaceDto initializeOrRestoreWorkspace() {
        Result<List<WorkspaceDto>> listRes = workspaceApi.getWorkspaces();
        List<WorkspaceDto> all = (listRes.isSuccess() && listRes.getData().isPresent())
                ? listRes.getData().get()
                : Collections.emptyList();

        if (all.isEmpty()) {
            log.info("No existing workspace found. Creating initial 'Default Workspace'...");
            Result<WorkspaceDto> createRes = workspaceApi.createWorkspace(new CreateWorkspaceRequest("Default Workspace"));
            if (createRes.isSuccess()) {
                currentWorkspace = createRes.getDataOrThrow();
            } else {
                log.error("Failed to create default workspace: {}", createRes.getError().map(e -> e.message()).orElse("Unknown error"));
            }
        } else {
            // Select the most recently updated workspace
            currentWorkspace = all.stream()
                    .max(Comparator.comparing(WorkspaceDto::updatedAt))
                    .orElse(all.get(0));
            log.info("Loaded active workspace: {} (id={})", currentWorkspace.name(), currentWorkspace.id());
        }

        if (currentWorkspace != null) {
            desktopState.setActiveWorkspaceId(currentWorkspace.id());
            desktopState.setActiveWorkspaceName(currentWorkspace.name());
        }

        return currentWorkspace;
    }

    /**
     * Captures current UI state (tabs, layout, theme, active connection) and persists it.
     */
    public synchronized boolean saveCurrentWorkspaceState() {
        if (currentWorkspace == null) {
            initializeOrRestoreWorkspace();
        }
        if (currentWorkspace == null) return false;

        try {
            log.info("Persisting workspace state for '{}'...", currentWorkspace.name());

            LayoutService layoutService = LayoutService.getInstance();
            ThemeService themeService = ThemeService.getInstance();

            // 1. Capture Open Tabs
            List<TabStateDto> tabDtos = new ArrayList<>();
            String activeFilePath = null;

            if (editorController != null) {
                List<SqlEditorTab> openTabs = editorController.getOpenTabs();
                SqlEditorTab activeTab = editorController.getActiveTab();

                for (SqlEditorTab tab : openTabs) {
                    boolean isActive = (tab == activeTab);
                    String filePath = tab.getFilePath() != null ? tab.getFilePath().toAbsolutePath().toString() : null;
                    if (isActive && filePath != null) {
                        activeFilePath = filePath;
                    }
                    tabDtos.add(new TabStateDto(
                            UUID.randomUUID().toString(),
                            tab.getTabTitle(),
                            filePath,
                            desktopState.getActiveConnection(),
                            isActive
                    ));
                }
            }

            // 1b. Unsaved text + per-tab connection (sidecar file)
            try {
                if (Platform.isFxApplicationThread()) {
                    saveTabSnapshots(true);
                } else {
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    Platform.runLater(() -> {
                        try { saveTabSnapshots(true); } finally { latch.countDown(); }
                    });
                    latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (Throwable t) {
                log.warn("Tab snapshot on save failed: {}", t.getMessage());
            }

            // 2. Capture Panel Layout
            PanelLayoutDto panelLayout = new PanelLayoutDto(
                    layoutService.isSidebarVisible(),
                    layoutService.getSidebarDividerPosition(),
                    layoutService.isResultsVisible(),
                    layoutService.getResultsDividerPosition()
            );

            // 3. Capture Theme Preference
            String theme = themeService.getCurrentDefinition().id(); // informational only: Settings own the theme

            // 4. Capture Connection
            String connectionId = desktopState.getActiveConnection();

            WorkspaceStateDto stateDto = new WorkspaceStateDto(
                    activeFilePath,
                    tabDtos,
                    connectionId,
                    theme,
                    panelLayout,
                    Map.of()
            );

            Result<WorkspaceDto> saveRes = workspaceApi.saveWorkspaceState(
                    new SaveWorkspaceStateRequest(currentWorkspace.id(), stateDto)
            );

            if (saveRes.isSuccess()) {
                currentWorkspace = saveRes.getDataOrThrow();
                log.info("Workspace state successfully persisted for '{}'.", currentWorkspace.name());
                return true;
            } else {
                log.error("Failed to save workspace state: {}", saveRes.getError().map(e -> e.message()).orElse("Unknown"));
                return false;
            }
        } catch (Throwable t) {
            log.error("Unexpected error saving workspace state: {}", t.getMessage(), t);
            return false;
        }
    }

    /**
     * Restores persisted workspace UI state into live components.
     * Safely handles deleted or missing files on disk.
     */
    public synchronized void applyWorkspaceState(WorkspaceDto workspace) {
        if (workspace == null || workspace.state() == null) return;
        this.currentWorkspace = workspace;

        desktopState.setActiveWorkspaceId(workspace.id());
        desktopState.setActiveWorkspaceName(workspace.name());

        WorkspaceStateDto state = workspace.state();

        // 1. Restore Layout
        if (state.panelLayout() != null) {
            LayoutService layoutService = LayoutService.getInstance();
            layoutService.setSidebarVisible(state.panelLayout().sidebarVisible());
            layoutService.setResultsVisible(state.panelLayout().resultsVisible());
        }

        // 2. Theme: not restored from the workspace any more. The theme of Settings (ide-settings.json) is applied
        //    at start-up; a second, older copy here overrode it (S3, 2026-11-06).

        // 3. Restore Connection Context
        if (state.selectedConnectionId() != null && !state.selectedConnectionId().isBlank()) {
            desktopState.setActiveConnection(state.selectedConnectionId());
        }

        // 4. Restore Open Tabs in Editor (prefer the richer snapshot: unsaved text, connection per tab, pinned)
        if (!sessionSettings().isRestoreOnStartup()) {
            log.info("session.restoreOnStartup is off: previous tabs are not reopened");
            return;
        }
        List<TabSnapshotStore.Snap> snaps = tabStore.read();
        if (editorController != null && !snaps.isEmpty()) {
            restoreFromSnapshots(snaps);
            return;
        }
        if (editorController != null && state.openTabs() != null && !state.openTabs().isEmpty()) {
            editorController.clearAllTabsSilently();

            for (TabStateDto tabDto : state.openTabs()) {
                if (tabDto.filePath() != null && !tabDto.filePath().isBlank()) {
                    Path file = Path.of(tabDto.filePath());
                    if (Files.exists(file)) {
                        try {
                            String sql = Files.readString(file);
                            editorController.openTabWithFile(tabDto.title(), sql, file, tabDto.active());
                        } catch (IOException e) {
                            log.warn("Could not read saved SQL file {}: {}. Skipping.", file, e.getMessage());
                        }
                    } else {
                        // Project or file no longer exists on disk - handle gracefully as required by Phase 6.1
                        log.warn("Workspace SQL file no longer exists on disk: {}. Skipping.", file);
                    }
                } else {
                    editorController.openTabWithFile(tabDto.title(), null, null, tabDto.active());
                }
            }
        }
    }

    private int skippedBig;

    private void restoreFromSnapshots(List<TabSnapshotStore.Snap> snaps) {
        editorController.clearAllTabsSilently();
        SqlEditorTab toSelect = null;
        int restored = 0;
        skippedBig = 0;
        for (TabSnapshotStore.Snap sn : snaps) {
            Path file = (sn.filePath() != null && !sn.filePath().isBlank()) ? Path.of(sn.filePath()) : null;
            String sql = sn.content();
            if (sql == null && file != null) {
                try {
                    long max = sessionSettings().getMaxTabSizeBytes();
                    if (max > 0 && Files.isRegularFile(file) && Files.size(file) > max) {
                        log.warn("Not reopened: {} is larger than session.maxTabSizeBytes ({} bytes)", file, max);
                        skippedBig++;
                        continue;
                    }
                    sql = Files.readString(file);
                } catch (IOException e) {
                    log.warn("Could not read saved SQL file {}: {}. Skipping.", file, e.getMessage());
                    continue;
                }
            }
            if (file != null && !Files.exists(file)) {
                // the file is gone: keep the tab only when it still carries unsaved text, as a tab without a file
                if (sn.content() == null) continue;
                file = null;
            }
            SqlEditorTab tab = editorController.openTabWithFile(sn.title(), sql, file,
                    sn.connection(), sn.database(), sn.schema(), false);
            if (sn.pinned()) tab.setPinned(true);
            if (sn.active()) toSelect = tab;
            restored++;
        }
        if (toSelect != null) editorController.selectTab(toSelect);
        log.info("Restored {} tab(s) from tab-snapshots.json", restored);
        if (skippedBig > 0) {
            app.services.NotificationService.getInstance().showWarning("Khôi phục tab",
                    skippedBig + " file lớn hơn giới hạn \"Dung lượng tối đa mỗi tab\" nên không được mở lại.");
        }
    }
}
