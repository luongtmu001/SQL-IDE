package com.lltool.core.application.project.usecase;

import com.lltool.core.application.project.command.CreateProjectCommand;
import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.common.result.Result;

public interface CreateProjectUseCase {

    Result<ProjectDto> execute(CreateProjectCommand command);
}

