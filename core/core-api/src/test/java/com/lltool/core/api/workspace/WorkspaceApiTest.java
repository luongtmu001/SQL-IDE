package com.lltool.core.api.workspace;

import com.lltool.core.api.application.CorePlatform;
import com.lltool.core.api.request.CreateWorkspaceRequest;
import com.lltool.core.api.request.RenameWorkspaceRequest;
import com.lltool.core.api.request.SaveWorkspaceStateRequest;
import com.lltool.core.application.workspace.dto.PanelLayoutDto;
import com.lltool.core.application.workspace.dto.TabStateDto;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.application.workspace.dto.WorkspaceStateDto;
import com.lltool.core.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceApiTest {

    private CorePlatform platform;
    private WorkspaceApi workspaceApi;

    @BeforeEach
    void setUp() {
        platform = CorePlatform.createDefault();
        workspaceApi = platform.workspaceApi();
    }

    @Test
    @DisplayName("Create workspace via API returns success")
    void testCreateWorkspace() {
        Result<WorkspaceDto> res = workspaceApi.createWorkspace(new CreateWorkspaceRequest("E-Commerce Workspace"));
        assertTrue(res.isSuccess());
        assertEquals("E-Commerce Workspace", res.getDataOrThrow().name());
    }

    @Test
    @DisplayName("Save workspace state via API updates open tabs and panel dimensions")
    void testSaveWorkspaceState() {
        WorkspaceDto created = workspaceApi.createWorkspace(new CreateWorkspaceRequest("Production")).getDataOrThrow();

        TabStateDto tab = new TabStateDto("tab-users", "users.sql", "/data/users.sql", "conn-pg", true);
        PanelLayoutDto layout = new PanelLayoutDto(true, 300.0, true, 260.0);
        WorkspaceStateDto state = new WorkspaceStateDto(
                "/data/users.sql",
                List.of(tab),
                "conn-pg",
                "Dracula",
                layout,
                Map.of("dialect", "PostgreSQL 16")
        );

        Result<WorkspaceDto> saveRes = workspaceApi.saveWorkspaceState(new SaveWorkspaceStateRequest(created.id(), state));
        assertTrue(saveRes.isSuccess());

        WorkspaceDto retrieved = workspaceApi.getWorkspace(created.id()).getDataOrThrow();
        assertEquals("Dracula", retrieved.state().themePreference());
        assertEquals("/data/users.sql", retrieved.state().activeFilePath());
        assertEquals(1, retrieved.state().openTabs().size());
        assertEquals(260.0, retrieved.state().panelLayout().resultsHeight());
    }

    @Test
    @DisplayName("Rename and delete workspace via API works correctly")
    void testRenameAndDeleteWorkspace() {
        WorkspaceDto created = workspaceApi.createWorkspace(new CreateWorkspaceRequest("Old Name")).getDataOrThrow();

        Result<WorkspaceDto> renameRes = workspaceApi.renameWorkspace(new RenameWorkspaceRequest(created.id(), "New Name"));
        assertTrue(renameRes.isSuccess());
        assertEquals("New Name", renameRes.getDataOrThrow().name());

        Result<Void> delRes = workspaceApi.deleteWorkspace(created.id());
        assertTrue(delRes.isSuccess());

        Result<WorkspaceDto> getRes = workspaceApi.getWorkspace(created.id());
        assertTrue(getRes.isFailure());
    }
}

