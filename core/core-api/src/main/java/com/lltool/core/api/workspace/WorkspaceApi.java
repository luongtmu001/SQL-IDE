package com.lltool.core.api.workspace;

import com.lltool.core.api.request.CreateWorkspaceRequest;
import com.lltool.core.api.request.RenameWorkspaceRequest;
import com.lltool.core.api.request.SaveWorkspaceStateRequest;
import com.lltool.core.api.request.SetActiveProjectInWorkspaceRequest;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.common.result.Result;

import java.util.List;

/**
 * Public Core Contract for Workspace operations.
 * Clean boundary for Desktop UI, multi-project workflows, and session persistence.
 */
public interface WorkspaceApi {

    Result<WorkspaceDto> createWorkspace(CreateWorkspaceRequest request);

    Result<WorkspaceDto> getWorkspace(String workspaceId);

    Result<List<WorkspaceDto>> getWorkspaces();

    Result<WorkspaceDto> saveWorkspaceState(SaveWorkspaceStateRequest request);

    Result<WorkspaceDto> renameWorkspace(RenameWorkspaceRequest request);

    Result<WorkspaceDto> setActiveProject(SetActiveProjectInWorkspaceRequest request);

    Result<Void> deleteWorkspace(String workspaceId);
}

