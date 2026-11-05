package com.lltool.core.application.workspace.service;

import com.lltool.core.application.port.InMemoryWorkspaceRepository;
import com.lltool.core.application.workspace.command.*;
import com.lltool.core.application.workspace.dto.PanelLayoutDto;
import com.lltool.core.application.workspace.dto.TabStateDto;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.application.workspace.dto.WorkspaceStateDto;
import com.lltool.core.application.workspace.query.GetWorkspaceDetailQuery;
import com.lltool.core.application.workspace.query.GetWorkspaceListQuery;
import com.lltool.core.common.event.EventBus;
import com.lltool.core.common.result.Result;
import com.lltool.core.common.time.TestTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceApplicationServiceTest {

    private InMemoryWorkspaceRepository repository;
    private EventBus eventBus;
    private TestTimeProvider timeProvider;
    private WorkspaceApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryWorkspaceRepository();
        eventBus = new EventBus();
        timeProvider = new TestTimeProvider(Instant.parse("2026-11-03T00:00:00Z"));
        service = new WorkspaceApplicationService(repository, eventBus, timeProvider);
    }

    @Test
    @DisplayName("Create workspace with valid name succeeds")
    void testCreateWorkspace() {
        Result<WorkspaceDto> result = service.execute(new CreateWorkspaceCommand("Analytics Workspace"));

        assertTrue(result.isSuccess());
        WorkspaceDto dto = result.getDataOrThrow();
        assertEquals("Analytics Workspace", dto.name());
        assertNotNull(dto.id());
        assertNotNull(dto.state());
        assertEquals("Primer Dark", dto.state().themePreference());
    }

    @Test
    @DisplayName("Create workspace with duplicate name fails with business error")
    void testCreateWorkspaceDuplicateFails() {
        service.execute(new CreateWorkspaceCommand("Analytics Workspace"));
        Result<WorkspaceDto> duplicateResult = service.execute(new CreateWorkspaceCommand("Analytics Workspace"));

        assertTrue(duplicateResult.isFailure());
        assertEquals("WORKSPACE_ALREADY_EXISTS", duplicateResult.getError().get().code());
    }

    @Test
    @DisplayName("Save workspace state persists tabs, panel layout, and theme")
    void testSaveWorkspaceState() {
        WorkspaceDto created = service.execute(new CreateWorkspaceCommand("Default WS")).getDataOrThrow();

        TabStateDto tab1 = new TabStateDto("tab-1", "users.sql", "/app/users.sql", "conn-pg", true);
        PanelLayoutDto layout = new PanelLayoutDto(true, 320.0, true, 220.0);
        WorkspaceStateDto newState = new WorkspaceStateDto(
                "/app/users.sql",
                List.of(tab1),
                "conn-pg",
                "Nord Light",
                layout,
                Map.of("key", "val")
        );

        timeProvider.advance(java.time.Duration.ofSeconds(60));
        Result<WorkspaceDto> saveResult = service.execute(new SaveWorkspaceStateCommand(created.id(), newState));

        assertTrue(saveResult.isSuccess());
        WorkspaceDto updated = saveResult.getDataOrThrow();
        assertEquals("Nord Light", updated.state().themePreference());
        assertEquals("/app/users.sql", updated.state().activeFilePath());
        assertEquals(1, updated.state().openTabs().size());
        assertEquals(320.0, updated.state().panelLayout().sidebarWidth());
    }

    @Test
    @DisplayName("Rename and delete workspace workflows succeed")
    void testRenameAndDeleteWorkspace() {
        WorkspaceDto created = service.execute(new CreateWorkspaceCommand("Old Name")).getDataOrThrow();

        Result<WorkspaceDto> renameResult = service.execute(new RenameWorkspaceCommand(created.id(), "New Name"));
        assertTrue(renameResult.isSuccess());
        assertEquals("New Name", renameResult.getDataOrThrow().name());

        Result<Void> deleteResult = service.execute(new DeleteWorkspaceCommand(created.id()));
        assertTrue(deleteResult.isSuccess());

        Result<WorkspaceDto> getResult = service.execute(new GetWorkspaceDetailQuery(created.id()));
        assertTrue(getResult.isFailure());
        assertEquals("WORKSPACE_NOT_FOUND", getResult.getError().get().code());
    }

    @Test
    @DisplayName("List workspaces returns all stored workspaces")
    void testListWorkspaces() {
        service.execute(new CreateWorkspaceCommand("WS 1"));
        service.execute(new CreateWorkspaceCommand("WS 2"));

        Result<List<WorkspaceDto>> listResult = service.execute(GetWorkspaceListQuery.all());
        assertTrue(listResult.isSuccess());
        assertEquals(2, listResult.getDataOrThrow().size());
    }
}
