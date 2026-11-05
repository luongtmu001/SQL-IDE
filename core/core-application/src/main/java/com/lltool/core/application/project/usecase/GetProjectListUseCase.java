package com.lltool.core.application.project.usecase;

import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.application.project.query.GetProjectListQuery;
import com.lltool.core.common.result.Result;

import java.util.List;

public interface GetProjectListUseCase {

    Result<List<ProjectDto>> execute(GetProjectListQuery query);
}

