package com.lltool.core.api.request;

import com.lltool.core.application.workspace.dto.WorkspaceStateDto;

public record SaveWorkspaceStateRequest(
        String workspaceId,
        WorkspaceStateDto state
) {}

