package com.lltool.core.application.project.usecase;

import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.application.project.query.GetProjectDetailQuery;
import com.lltool.core.common.result.Result;

public interface GetProjectDetailUseCase {

    Result<ProjectDto> execute(GetProjectDetailQuery query);
}

