package com.lltool.core.domain.workspace.valueobject;

/**
 * Value Object representing the visibility and dimensions of dockable panels in the Workspace.
 */
public record PanelLayoutState(
        boolean sidebarVisible,
        double sidebarWidth,
        boolean resultsVisible,
        double resultsHeight
) {
    public static PanelLayoutState defaultLayout() {
        return new PanelLayoutState(true, 280.0, true, 250.0);
    }

    public static PanelLayoutState of(boolean sidebarVisible, double sidebarWidth, boolean resultsVisible, double resultsHeight) {
        return new PanelLayoutState(sidebarVisible, sidebarWidth, resultsVisible, resultsHeight);
    }
}

