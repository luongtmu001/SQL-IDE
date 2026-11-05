package com.lltool.core.application.workspace.usecase;

import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.application.workspace.query.GetWorkspaceListQuery;
import com.lltool.core.common.result.Result;

import java.util.List;

public interface GetWorkspaceListUseCase {
    Result<List<WorkspaceDto>> execute(GetWorkspaceListQuery query);
}

