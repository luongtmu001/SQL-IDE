package com.lltool.core.application.workspace.dto;

import java.util.List;

public record WorkspaceDto(
        String id,
        String name,
        List<String> projectIds,
        String activeProjectId,
        WorkspaceStateDto state,
        String createdAt,
        String updatedAt
) {}

