package com.lltool.core.application.workspace.usecase;

import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.application.workspace.query.GetWorkspaceDetailQuery;
import com.lltool.core.common.result.Result;

public interface GetWorkspaceDetailUseCase {
    Result<WorkspaceDto> execute(GetWorkspaceDetailQuery query);
}

