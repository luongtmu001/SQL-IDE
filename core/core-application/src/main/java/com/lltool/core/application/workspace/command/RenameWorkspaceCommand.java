package com.lltool.core.application.workspace.command;

public record RenameWorkspaceCommand(
        String workspaceId,
        String newName
) {}

