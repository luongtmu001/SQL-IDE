package com.lltool.core.application.workspace.usecase;

import com.lltool.core.application.workspace.command.SaveWorkspaceStateCommand;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.common.result.Result;

public interface SaveWorkspaceStateUseCase {
    Result<WorkspaceDto> execute(SaveWorkspaceStateCommand command);
}

