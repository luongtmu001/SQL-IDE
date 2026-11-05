package com.lltool.core.application.workspace.command;

public record SetActiveProjectInWorkspaceCommand(
        String workspaceId,
        String projectId
) {}

