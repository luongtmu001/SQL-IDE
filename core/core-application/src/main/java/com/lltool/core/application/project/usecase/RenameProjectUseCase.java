package com.lltool.core.application.project.usecase;

import com.lltool.core.application.project.command.RenameProjectCommand;
import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.common.result.Result;

public interface RenameProjectUseCase {

    Result<ProjectDto> execute(RenameProjectCommand command);
}

