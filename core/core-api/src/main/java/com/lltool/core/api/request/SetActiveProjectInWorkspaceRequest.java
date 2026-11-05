package com.lltool.core.api.request;

public record SetActiveProjectInWorkspaceRequest(
        String workspaceId,
        String projectId
) {}

