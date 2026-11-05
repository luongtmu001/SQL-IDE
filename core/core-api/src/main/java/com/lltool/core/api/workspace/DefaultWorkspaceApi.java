package com.lltool.core.api.workspace;

import com.lltool.core.api.request.CreateWorkspaceRequest;
import com.lltool.core.api.request.RenameWorkspaceRequest;
import com.lltool.core.api.request.SaveWorkspaceStateRequest;
import com.lltool.core.api.request.SetActiveProjectInWorkspaceRequest;
import com.lltool.core.application.workspace.command.*;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.application.workspace.query.GetWorkspaceDetailQuery;
import com.lltool.core.application.workspace.query.GetWorkspaceListQuery;
import com.lltool.core.application.workspace.usecase.*;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;

import java.util.List;
import java.util.Objects;

/**
 * Standard implementation of WorkspaceApi delegating requests to Use Cases.
 */
public class DefaultWorkspaceApi implements WorkspaceApi {

    private final CreateWorkspaceUseCase createUseCase;
    private final SaveWorkspaceStateUseCase saveStateUseCase;
    private final RenameWorkspaceUseCase renameUseCase;
    private final DeleteWorkspaceUseCase deleteUseCase;
    private final SetActiveProjectInWorkspaceUseCase setActiveProjectUseCase;
    private final GetWorkspaceDetailUseCase detailUseCase;
    private final GetWorkspaceListUseCase listUseCase;

    public DefaultWorkspaceApi(
            CreateWorkspaceUseCase createUseCase,
            SaveWorkspaceStateUseCase saveStateUseCase,
            RenameWorkspaceUseCase renameUseCase,
            DeleteWorkspaceUseCase deleteUseCase,
            SetActiveProjectInWorkspaceUseCase setActiveProjectUseCase,
            GetWorkspaceDetailUseCase detailUseCase,
            GetWorkspaceListUseCase listUseCase) {
        this.createUseCase = Objects.requireNonNull(createUseCase);
        this.saveStateUseCase = Objects.requireNonNull(saveStateUseCase);
        this.renameUseCase = Objects.requireNonNull(renameUseCase);
        this.deleteUseCase = Objects.requireNonNull(deleteUseCase);
        this.setActiveProjectUseCase = Objects.requireNonNull(setActiveProjectUseCase);
        this.detailUseCase = Objects.requireNonNull(detailUseCase);
        this.listUseCase = Objects.requireNonNull(listUseCase);
    }

    @Override
    public Result<WorkspaceDto> createWorkspace(CreateWorkspaceRequest request) {
        if (request == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Request cannot be null"));
        }
        return createUseCase.execute(new CreateWorkspaceCommand(request.name()));
    }

    @Override
    public Result<WorkspaceDto> getWorkspace(String workspaceId) {
        return detailUseCase.execute(new GetWorkspaceDetailQuery(workspaceId));
    }

    @Override
    public Result<List<WorkspaceDto>> getWorkspaces() {
        return listUseCase.execute(GetWorkspaceListQuery.all());
    }

    @Override
    public Result<WorkspaceDto> saveWorkspaceState(SaveWorkspaceStateRequest request) {
        if (request == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Request cannot be null"));
        }
        return saveStateUseCase.execute(new SaveWorkspaceStateCommand(request.workspaceId(), request.state()));
    }

    @Override
    public Result<WorkspaceDto> renameWorkspace(RenameWorkspaceRequest request) {
        if (request == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Request cannot be null"));
        }
        return renameUseCase.execute(new RenameWorkspaceCommand(request.workspaceId(), request.newName()));
    }

    @Override
    public Result<WorkspaceDto> setActiveProject(SetActiveProjectInWorkspaceRequest request) {
        if (request == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Request cannot be null"));
        }
        return setActiveProjectUseCase.execute(new SetActiveProjectInWorkspaceCommand(request.workspaceId(), request.projectId()));
    }

    @Override
    public Result<Void> deleteWorkspace(String workspaceId) {
        return deleteUseCase.execute(new DeleteWorkspaceCommand(workspaceId));
    }
}

