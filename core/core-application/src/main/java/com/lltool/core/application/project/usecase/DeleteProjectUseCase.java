package com.lltool.core.application.project.usecase;

import com.lltool.core.application.project.command.DeleteProjectCommand;
import com.lltool.core.common.result.Result;

public interface DeleteProjectUseCase {

    Result<Void> execute(DeleteProjectCommand command);
}

