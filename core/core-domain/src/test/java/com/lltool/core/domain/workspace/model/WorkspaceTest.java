package com.lltool.core.domain.workspace.model;

import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.workspace.valueobject.WorkspaceId;
import com.lltool.core.domain.workspace.valueobject.WorkspaceName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {

    @Test
    @DisplayName("Workspace manages projects and active project correctly")
    void testWorkspaceOperations() {
        Instant now = Instant.now();
        Workspace ws = Workspace.create(WorkspaceId.generate(), WorkspaceName.of("Dev Workspace"), now);

        ProjectId p1 = ProjectId.generate();
        ProjectId p2 = ProjectId.generate();

        ws.addProject(p1, now);
        assertEquals(1, ws.getProjectIds().size());
        assertEquals(p1, ws.getActiveProjectId().orElse(null));

        ws.addProject(p2, now);
        assertEquals(2, ws.getProjectIds().size());
        assertEquals(p1, ws.getActiveProjectId().orElse(null));

        ws.setActiveProject(p2, now);
        assertEquals(p2, ws.getActiveProjectId().orElse(null));

        ws.removeProject(p2, now);
        assertEquals(1, ws.getProjectIds().size());
        assertEquals(p1, ws.getActiveProjectId().orElse(null));
    }

    @Test
    @DisplayName("Workspace manages and updates state correctly")
    void testWorkspaceStateUpdate() {
        Instant now = Instant.now();
        Workspace ws = Workspace.create(WorkspaceId.generate(), WorkspaceName.of("Analytics Workspace"), now);

        assertNotNull(ws.getState());
        assertEquals("Primer Dark", ws.getState().themePreference());
        assertTrue(ws.getState().openTabs().isEmpty());

        var tab1 = com.lltool.core.domain.workspace.valueobject.TabState.of("tab-1", "query.sql", "/data/query.sql", "conn-1", true);
        var layout = com.lltool.core.domain.workspace.valueobject.PanelLayoutState.of(true, 300.0, false, 200.0);
        var newState = com.lltool.core.domain.workspace.valueobject.WorkspaceState.of(
                "/data/query.sql",
                java.util.List.of(tab1),
                "conn-1",
                "Nord Dark",
                layout,
                java.util.Map.of("autoSave", "true")
        );

        Instant updatedTime = now.plusSeconds(10);
        ws.updateState(newState, updatedTime);

        assertEquals(newState, ws.getState());
        assertEquals("/data/query.sql", ws.getState().activeFilePath());
        assertEquals(1, ws.getState().openTabs().size());
        assertEquals("tab-1", ws.getState().openTabs().get(0).tabId());
        assertEquals("Nord Dark", ws.getState().themePreference());
        assertEquals(updatedTime, ws.getUpdatedAt());
    }
}

