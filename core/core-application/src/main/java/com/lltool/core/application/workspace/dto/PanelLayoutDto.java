package com.lltool.core.application.workspace.dto;

public record PanelLayoutDto(
        boolean sidebarVisible,
        double sidebarWidth,
        boolean resultsVisible,
        double resultsHeight
) {}

