package com.lltool.core.application.workspace.dto;

public record TabStateDto(
        String tabId,
        String title,
        String filePath,
        String connectionId,
        boolean active
) {}

