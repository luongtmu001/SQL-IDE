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

            // 2. Capture Panel Layout
            PanelLayoutDto panelLayout = new PanelLayoutDto(
                    layoutService.isSidebarVisible(),
                    layoutService.getSidebarDividerPosition(),
                    layoutService.isResultsVisible(),
                    layoutService.getResultsDividerPosition()
            );

            // 3. Capture Theme Preference
            String theme = themeService.getCurrentTheme().getDisplayName();

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

        // 2. Restore Theme
        if (state.themePreference() != null) {
            ThemeService.AppTheme theme = ThemeService.AppTheme.fromDisplayName(state.themePreference());
            Scene scene = AppContext.getInstance().getPrimaryStage() != null
                    ? AppContext.getInstance().getPrimaryStage().getScene()
                    : null;
            ThemeService.getInstance().setTheme(theme, scene);
        }

        // 3. Restore Connection Context
        if (state.selectedConnectionId() != null && !state.selectedConnectionId().isBlank()) {
            desktopState.setActiveConnection(state.selectedConnectionId());
        }

        // 4. Restore Open Tabs in Editor
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
}

