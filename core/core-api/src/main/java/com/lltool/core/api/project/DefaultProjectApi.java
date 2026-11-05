package com.lltool.core.api.project;

import com.lltool.core.api.request.CreateProjectRequest;
import com.lltool.core.api.request.RenameProjectRequest;
import com.lltool.core.application.project.command.CreateProjectCommand;
import com.lltool.core.application.project.command.DeleteProjectCommand;
import com.lltool.core.application.project.command.RenameProjectCommand;
import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.application.project.query.GetProjectDetailQuery;
import com.lltool.core.application.project.query.GetProjectListQuery;
import com.lltool.core.application.project.usecase.*;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;

import java.util.List;
import java.util.Objects;

/**
 * Standard implementation of ProjectApi delegating requests to Use Cases.
 */
public class DefaultProjectApi implements ProjectApi {

    private final CreateProjectUseCase createUseCase;
    private final RenameProjectUseCase renameUseCase;
    private final DeleteProjectUseCase deleteUseCase;
    private final GetProjectDetailUseCase detailUseCase;
    private final GetProjectListUseCase listUseCase;

    public DefaultProjectApi(
            CreateProjectUseCase createUseCase,
            RenameProjectUseCase renameUseCase,
            DeleteProjectUseCase deleteUseCase,
            GetProjectDetailUseCase detailUseCase,
            GetProjectListUseCase listUseCase) {
        this.createUseCase = Objects.requireNonNull(createUseCase);
        this.renameUseCase = Objects.requireNonNull(renameUseCase);
        this.deleteUseCase = Objects.requireNonNull(deleteUseCase);
        this.detailUseCase = Objects.requireNonNull(detailUseCase);
        this.listUseCase = Objects.requireNonNull(listUseCase);
    }

    @Override
    public Result<ProjectDto> createProject(CreateProjectRequest request) {
        if (request == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Request cannot be null"));
        }
        return createUseCase.execute(new CreateProjectCommand(request.name(), request.path()));
    }

    @Override
    public Result<ProjectDto> getProject(String projectId) {
        return detailUseCase.execute(new GetProjectDetailQuery(projectId));
    }

    @Override
    public Result<List<ProjectDto>> getProjects() {
        return listUseCase.execute(GetProjectListQuery.all());
    }

    @Override
    public Result<ProjectDto> renameProject(RenameProjectRequest request) {
        if (request == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Request cannot be null"));
        }
        return renameUseCase.execute(new RenameProjectCommand(request.projectId(), request.newName()));
    }

    @Override
    public Result<Void> deleteProject(String projectId) {
        return deleteUseCase.execute(new DeleteProjectCommand(projectId));
    }
}

