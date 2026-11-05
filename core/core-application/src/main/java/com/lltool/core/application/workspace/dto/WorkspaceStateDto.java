package com.lltool.core.application.workspace.dto;

import java.util.List;
import java.util.Map;

public record WorkspaceStateDto(
        String activeFilePath,
        List<TabStateDto> openTabs,
        String selectedConnectionId,
        String themePreference,
        PanelLayoutDto panelLayout,
        Map<String, String> customProperties
) {
    public static WorkspaceStateDto empty() {
        return new WorkspaceStateDto(
                null,
                List.of(),
                null,
                "Primer Dark",
                new PanelLayoutDto(true, 280.0, true, 250.0),
                Map.of()
        );
    }
}

