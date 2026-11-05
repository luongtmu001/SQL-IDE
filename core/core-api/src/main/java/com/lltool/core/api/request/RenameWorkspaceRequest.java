package com.lltool.core.api.request;

public record RenameWorkspaceRequest(
        String workspaceId,
        String newName
) {}

