package com.lltool.infrastructure.storage.local;

import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.workspace.model.Workspace;
import com.lltool.core.domain.workspace.valueobject.*;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalWorkspaceRepositoryTest {

    @Test
    @DisplayName("LocalWorkspaceRepository persists workspace with state, projects, and tabs to JSON")
    void testSaveAndRetrieveWorkspace(@TempDir Path tempDir) {
        AppDirectoryManager dirManager = AppDirectoryManager.createIsolated(tempDir);
        LocalWorkspaceRepository repository = new LocalWorkspaceRepository(dirManager);

        WorkspaceId wsId = WorkspaceId.generate();
        Instant now = Instant.now();

        TabState tab1 = TabState.of("tab-1", "users.sql", "/tmp/users.sql", "conn-pg", true);
        PanelLayoutState layout = PanelLayoutState.of(true, 310.0, false, 180.0);
        WorkspaceState state = WorkspaceState.of(
                "/tmp/users.sql",
                List.of(tab1),
                "conn-pg",
                "Nord Dark",
                layout,
                Map.of("settingA", "valA")
        );

        Workspace ws = Workspace.create(wsId, WorkspaceName.of("Production WS"), state, now);
        ProjectId p1 = ProjectId.generate();
        ws.addProject(p1, now);
        ws.setActiveProject(p1, now);

        repository.save(ws);

        assertTrue(repository.existsById(wsId));
        assertTrue(repository.findByName(WorkspaceName.of("Production WS")).isPresent());

        Optional<Workspace> foundOpt = repository.findById(wsId);
        assertTrue(foundOpt.isPresent());
        Workspace found = foundOpt.get();

        assertEquals("Production WS", found.getName().value());
        assertEquals(1, found.getProjectIds().size());
        assertEquals(p1, found.getActiveProjectId().orElse(null));

        WorkspaceState foundState = found.getState();
        assertEquals("Nord Dark", foundState.themePreference());
        assertEquals("/tmp/users.sql", foundState.activeFilePath());
        assertEquals(1, foundState.openTabs().size());
        assertEquals("users.sql", foundState.openTabs().get(0).title());
        assertEquals(310.0, foundState.panelLayout().sidebarWidth());
        assertFalse(foundState.panelLayout().resultsVisible());

        // Test update state
        TabState tab2 = TabState.of("tab-2", "orders.sql", "/tmp/orders.sql", "conn-pg", true);
        WorkspaceState updatedState = WorkspaceState.of(
                "/tmp/orders.sql",
                List.of(tab1, tab2),
                "conn-pg",
                "Primer Dark",
                PanelLayoutState.defaultLayout(),
                Map.of()
        );
        found.updateState(updatedState, now.plusSeconds(30));
        repository.save(found);

        Workspace reloaded = repository.findById(wsId).orElseThrow();
        assertEquals(2, reloaded.getState().openTabs().size());
        assertEquals("Primer Dark", reloaded.getState().themePreference());

        // Test delete
        repository.delete(wsId);
        assertFalse(repository.existsById(wsId));
        assertTrue(repository.findById(wsId).isEmpty());
    }
}

