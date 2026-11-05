package com.lltool.core.domain.workspace.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Value Object capturing persistent UI state for a Workspace.
 * Conforms to Clean Architecture: holds zero plaintext credentials or database handles.
 */
public record WorkspaceState(
        String activeFilePath,
        List<TabState> openTabs,
        String selectedConnectionId,
        String themePreference,
        PanelLayoutState panelLayout,
        Map<String, String> customProperties
) {
    public WorkspaceState {
        openTabs = openTabs != null ? List.copyOf(openTabs) : List.of();
        panelLayout = panelLayout != null ? panelLayout : PanelLayoutState.defaultLayout();
        customProperties = customProperties != null ? Map.copyOf(customProperties) : Map.of();
    }

    public static WorkspaceState empty() {
        return new WorkspaceState(null, List.of(), null, "Primer Dark", PanelLayoutState.defaultLayout(), Map.of());
    }

    public static WorkspaceState of(
            String activeFilePath,
            List<TabState> openTabs,
            String selectedConnectionId,
            String themePreference,
            PanelLayoutState panelLayout,
            Map<String, String> customProperties
    ) {
        return new WorkspaceState(
                activeFilePath,
                openTabs,
                selectedConnectionId,
                themePreference != null ? themePreference : "Primer Dark",
                panelLayout,
                customProperties
        );
    }
}

