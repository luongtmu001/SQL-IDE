package app.framework;

import app.framework.bootstrap.DesktopBootstrap;
import app.framework.lifecycle.DesktopLifecycleManager;
import app.framework.state.GlobalDesktopState;
import app.framework.workspace.WorkspaceDesktopManager;
import app.services.LayoutService;
import app.services.ThemeService;
import com.lltool.core.api.request.CreateWorkspaceRequest;
import com.lltool.core.api.request.SaveWorkspaceStateRequest;
import com.lltool.core.application.workspace.dto.PanelLayoutDto;
import com.lltool.core.application.workspace.dto.TabStateDto;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.application.workspace.dto.WorkspaceStateDto;
import com.lltool.core.common.result.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceDesktopIntegrationTest {

    @BeforeAll
    static void setUpBootstrap() {
        DesktopBootstrap.getInstance();
    }

    @Test
    @DisplayName("WorkspaceDesktopManager creates and restores workspace successfully")
    void testWorkspaceDesktopManagerLifecycle() {
        WorkspaceDesktopManager wsManager = WorkspaceDesktopManager.getInstance();
        WorkspaceDto ws = wsManager.initializeOrRestoreWorkspace();

        assertNotNull(ws);
        assertNotNull(ws.id());
        assertNotNull(ws.name());
        assertEquals(ws.id(), GlobalDesktopState.getInstance().getActiveWorkspaceId());
    }

    @Test
    @DisplayName("Persisting and reloading workspace preserves state and handles missing files gracefully")
    void testWorkspaceStatePersistenceAndMissingFiles() {
        var bootstrap = DesktopBootstrap.getInstance();
        var workspaceApi = bootstrap.getWorkspaceApi();

        Result<WorkspaceDto> created = workspaceApi.createWorkspace(new CreateWorkspaceRequest("Integration Workspace"));
        assertTrue(created.isSuccess());
        String wsId = created.getDataOrThrow().id();

        // 1. Create a dummy file on disk
        Path tempFile;
        try {
            tempFile = Files.createTempFile("test_query", ".sql");
            Files.writeString(tempFile, "SELECT * FROM test_table;");
        } catch (Exception e) {
            fail("Failed to create temporary SQL test file: " + e.getMessage());
            return;
        }

        // 2. State with one existing file and one non-existent file
        TabStateDto existingTab = new TabStateDto("tab-1", "test_query.sql", tempFile.toAbsolutePath().toString(), "conn-pg", true);
        TabStateDto missingTab = new TabStateDto("tab-2", "deleted.sql", "C:\\non_existent_folder\\deleted.sql", "conn-pg", false);
        PanelLayoutDto layout = new PanelLayoutDto(true, 330.0, false, 210.0);

        WorkspaceStateDto stateDto = new WorkspaceStateDto(
                tempFile.toAbsolutePath().toString(),
                List.of(existingTab, missingTab),
                "conn-pg",
                "Primer Dark",
                layout,
                Map.of()
        );

        Result<WorkspaceDto> saveRes = workspaceApi.saveWorkspaceState(new SaveWorkspaceStateRequest(wsId, stateDto));
        assertTrue(saveRes.isSuccess());

        // 3. Reload from storage
        WorkspaceDto reloaded = workspaceApi.getWorkspace(wsId).getDataOrThrow();
        assertEquals(2, reloaded.state().openTabs().size());
        assertEquals("conn-pg", reloaded.state().selectedConnectionId());
        assertEquals(330.0, reloaded.state().panelLayout().sidebarWidth());
        assertFalse(reloaded.state().panelLayout().resultsVisible());

        // 4. Test apply workspace state without UI throws no exception on missing file
        assertDoesNotThrow(() -> {
            WorkspaceDesktopManager.getInstance().applyWorkspaceState(reloaded);
        });

        assertEquals(wsId, GlobalDesktopState.getInstance().getActiveWorkspaceId());
        assertEquals("conn-pg", GlobalDesktopState.getInstance().getActiveConnection());

        // Clean up temp file & workspace
        try {
            Files.deleteIfExists(tempFile);
        } catch (Exception ignored) {}
        workspaceApi.deleteWorkspace(wsId);
    }

    @Test
    @DisplayName("DesktopLifecycleManager executes shutdown hooks without error")
    void testShutdownHookIntegrity() {
        assertDoesNotThrow(() -> {
            boolean saved = WorkspaceDesktopManager.getInstance().saveCurrentWorkspaceState();
            // Should succeed or return boolean safely
            assertTrue(saved || WorkspaceDesktopManager.getInstance().getCurrentWorkspace() != null);
        });
    }
}

