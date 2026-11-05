package com.lltool.core.application.workspace.usecase;

import com.lltool.core.application.workspace.command.CreateWorkspaceCommand;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.common.result.Result;

public interface CreateWorkspaceUseCase {
    Result<WorkspaceDto> execute(CreateWorkspaceCommand command);
}

