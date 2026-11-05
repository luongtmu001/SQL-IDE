package com.lltool.core.application.workspace.command;

import com.lltool.core.application.workspace.dto.WorkspaceStateDto;

public record SaveWorkspaceStateCommand(
        String workspaceId,
        WorkspaceStateDto state
) {}

