package com.lltool.core.application.workspace.usecase;

import com.lltool.core.application.workspace.command.DeleteWorkspaceCommand;
import com.lltool.core.common.result.Result;

public interface DeleteWorkspaceUseCase {
    Result<Void> execute(DeleteWorkspaceCommand command);
}

