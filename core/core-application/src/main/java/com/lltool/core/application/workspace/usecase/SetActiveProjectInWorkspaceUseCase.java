package com.lltool.core.application.workspace.usecase;

import com.lltool.core.application.workspace.command.SetActiveProjectInWorkspaceCommand;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.common.result.Result;

public interface SetActiveProjectInWorkspaceUseCase {
    Result<WorkspaceDto> execute(SetActiveProjectInWorkspaceCommand command);
}

