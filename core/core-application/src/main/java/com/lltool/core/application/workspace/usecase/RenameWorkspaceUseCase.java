package com.lltool.core.application.workspace.usecase;

import com.lltool.core.application.workspace.command.RenameWorkspaceCommand;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.common.result.Result;

public interface RenameWorkspaceUseCase {
    Result<WorkspaceDto> execute(RenameWorkspaceCommand command);
}

