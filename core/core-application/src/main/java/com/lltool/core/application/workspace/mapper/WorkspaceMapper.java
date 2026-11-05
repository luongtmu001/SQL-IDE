package com.lltool.core.application.workspace.mapper;

import com.lltool.core.application.workspace.dto.PanelLayoutDto;
import com.lltool.core.application.workspace.dto.TabStateDto;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.application.workspace.dto.WorkspaceStateDto;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.workspace.model.Workspace;
import com.lltool.core.domain.workspace.valueobject.PanelLayoutState;
import com.lltool.core.domain.workspace.valueobject.TabState;
import com.lltool.core.domain.workspace.valueobject.WorkspaceState;

import java.util.List;

public final class WorkspaceMapper {

    private WorkspaceMapper() {}

    public static WorkspaceDto toDto(Workspace domain) {
        if (domain == null) return null;

        List<String> projectIds = domain.getProjectIds().stream()
                .map(ProjectId::value)
                .toList();

        String activeProjectId = domain.getActiveProjectId()
                .map(ProjectId::value)
                .orElse(null);

        WorkspaceStateDto stateDto = toStateDto(domain.getState());

        return new WorkspaceDto(
                domain.getId().value(),
                domain.getName().value(),
                projectIds,
                activeProjectId,
                stateDto,
                domain.getCreatedAt().toString(),
                domain.getUpdatedAt().toString()
        );
    }

    public static WorkspaceStateDto toStateDto(WorkspaceState state) {
        if (state == null) return WorkspaceStateDto.empty();

        List<TabStateDto> tabDtos = state.openTabs().stream()
                .map(t -> new TabStateDto(t.tabId(), t.title(), t.filePath(), t.connectionId(), t.active()))
                .toList();

        PanelLayoutDto layoutDto = new PanelLayoutDto(
                state.panelLayout().sidebarVisible(),
                state.panelLayout().sidebarWidth(),
                state.panelLayout().resultsVisible(),
                state.panelLayout().resultsHeight()
        );

        return new WorkspaceStateDto(
                state.activeFilePath(),
                tabDtos,
                state.selectedConnectionId(),
                state.themePreference(),
                layoutDto,
                state.customProperties()
        );
    }

    public static WorkspaceState toDomainState(WorkspaceStateDto dto) {
        if (dto == null) return WorkspaceState.empty();

        List<TabState> tabs = dto.openTabs() != null ? dto.openTabs().stream()
                .map(t -> TabState.of(t.tabId(), t.title(), t.filePath(), t.connectionId(), t.active()))
                .toList() : List.of();

        PanelLayoutState layout = dto.panelLayout() != null
                ? PanelLayoutState.of(
                        dto.panelLayout().sidebarVisible(),
                        dto.panelLayout().sidebarWidth(),
                        dto.panelLayout().resultsVisible(),
                        dto.panelLayout().resultsHeight()
                  )
                : PanelLayoutState.defaultLayout();

        return WorkspaceState.of(
                dto.activeFilePath(),
                tabs,
                dto.selectedConnectionId(),
                dto.themePreference(),
                layout,
                dto.customProperties()
        );
    }
}

