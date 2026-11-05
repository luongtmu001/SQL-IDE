package com.lltool.core.application.workspace.service;

import com.lltool.core.application.workspace.command.*;
import com.lltool.core.application.workspace.dto.WorkspaceDto;
import com.lltool.core.application.workspace.mapper.WorkspaceMapper;
import com.lltool.core.application.workspace.query.GetWorkspaceDetailQuery;
import com.lltool.core.application.workspace.query.GetWorkspaceListQuery;
import com.lltool.core.application.workspace.usecase.*;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.event.EventPublisher;
import com.lltool.core.common.result.Result;
import com.lltool.core.common.time.TimeProvider;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.workspace.event.WorkspaceChangedDomainEvent;
import com.lltool.core.domain.workspace.model.Workspace;
import com.lltool.core.domain.workspace.repository.WorkspaceRepository;
import com.lltool.core.domain.workspace.valueobject.WorkspaceId;
import com.lltool.core.domain.workspace.valueobject.WorkspaceName;
import com.lltool.core.domain.workspace.valueobject.WorkspaceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service coordinating Workspace use cases, state persistence, and lifecycle.
 */
public class WorkspaceApplicationService implements
        CreateWorkspaceUseCase,
        SaveWorkspaceStateUseCase,
        RenameWorkspaceUseCase,
        DeleteWorkspaceUseCase,
        SetActiveProjectInWorkspaceUseCase,
        GetWorkspaceDetailUseCase,
        GetWorkspaceListUseCase {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceApplicationService.class);

    private final WorkspaceRepository repository;
    private final EventPublisher eventPublisher;
    private final TimeProvider timeProvider;

    public WorkspaceApplicationService(WorkspaceRepository repository, EventPublisher eventPublisher, TimeProvider timeProvider) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
    }

    @Override
    public Result<WorkspaceDto> execute(CreateWorkspaceCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<WorkspaceName> nameRes = WorkspaceName.create(command.name());
        if (nameRes.isFailure()) {
            return Result.failure(nameRes.getError().get());
        }

        WorkspaceName name = nameRes.getDataOrThrow();
        Optional<Workspace> existing = repository.findByName(name);
        if (existing.isPresent()) {
            return Result.failure(AppError.business(AppError.WORKSPACE_ALREADY_EXISTS,
                    "A workspace with name '" + name.value() + "' already exists."));
        }

        Instant now = timeProvider.now();
        WorkspaceId id = WorkspaceId.generate();
        Workspace workspace = Workspace.create(id, name, WorkspaceState.empty(), now);

        repository.save(workspace);
        log.info("Created workspace: {} (id={})", name.value(), id.value());

        eventPublisher.publish(new WorkspaceChangedDomainEvent(id, null, 0, now));

        return Result.success(WorkspaceMapper.toDto(workspace));
    }

    @Override
    public Result<WorkspaceDto> execute(SaveWorkspaceStateCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<WorkspaceId> idRes = WorkspaceId.create(command.workspaceId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        WorkspaceId id = idRes.getDataOrThrow();
        Optional<Workspace> foundOpt = repository.findById(id);
        if (foundOpt.isEmpty()) {
            return Result.failure(AppError.resourceNotFound(AppError.WORKSPACE_NOT_FOUND,
                    "Workspace not found with id: " + command.workspaceId()));
        }

        Workspace workspace = foundOpt.get();
        Instant now = timeProvider.now();

        WorkspaceState newState = WorkspaceMapper.toDomainState(command.state());
        workspace.updateState(newState, now);

        repository.save(workspace);
        log.debug("Saved state for workspace: {}", id.value());

        eventPublisher.publish(new WorkspaceChangedDomainEvent(
                id,
                workspace.getActiveProjectId().orElse(null),
                workspace.getProjectIds().size(),
                now
        ));

        return Result.success(WorkspaceMapper.toDto(workspace));
    }

    @Override
    public Result<WorkspaceDto> execute(RenameWorkspaceCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<WorkspaceId> idRes = WorkspaceId.create(command.workspaceId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        Result<WorkspaceName> newNameRes = WorkspaceName.create(command.newName());
        if (newNameRes.isFailure()) return Result.failure(newNameRes.getError().get());

        WorkspaceId id = idRes.getDataOrThrow();
        WorkspaceName newName = newNameRes.getDataOrThrow();

        Optional<Workspace> existing = repository.findByName(newName);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            return Result.failure(AppError.business(AppError.WORKSPACE_ALREADY_EXISTS,
                    "A workspace with name '" + newName.value() + "' already exists."));
        }

        Optional<Workspace> foundOpt = repository.findById(id);
        if (foundOpt.isEmpty()) {
            return Result.failure(AppError.resourceNotFound(AppError.WORKSPACE_NOT_FOUND,
                    "Workspace not found with id: " + command.workspaceId()));
        }

        Workspace workspace = foundOpt.get();
        Instant now = timeProvider.now();
        workspace.rename(newName, now);

        repository.save(workspace);
        log.info("Renamed workspace {} to {}", id.value(), newName.value());

        eventPublisher.publish(new WorkspaceChangedDomainEvent(
                id,
                workspace.getActiveProjectId().orElse(null),
                workspace.getProjectIds().size(),
                now
        ));

        return Result.success(WorkspaceMapper.toDto(workspace));
    }

    @Override
    public Result<Void> execute(DeleteWorkspaceCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<WorkspaceId> idRes = WorkspaceId.create(command.workspaceId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        WorkspaceId id = idRes.getDataOrThrow();
        if (!repository.existsById(id)) {
            return Result.failure(AppError.resourceNotFound(AppError.WORKSPACE_NOT_FOUND,
                    "Workspace not found with id: " + command.workspaceId()));
        }

        repository.delete(id);
        log.info("Deleted workspace: {}", id.value());

        return Result.success(null);
    }

    @Override
    public Result<WorkspaceDto> execute(SetActiveProjectInWorkspaceCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<WorkspaceId> idRes = WorkspaceId.create(command.workspaceId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        WorkspaceId id = idRes.getDataOrThrow();
        Optional<Workspace> foundOpt = repository.findById(id);
        if (foundOpt.isEmpty()) {
            return Result.failure(AppError.resourceNotFound(AppError.WORKSPACE_NOT_FOUND,
                    "Workspace not found with id: " + command.workspaceId()));
        }

        Workspace workspace = foundOpt.get();
        Instant now = timeProvider.now();

        if (command.projectId() != null && !command.projectId().isBlank()) {
            Result<ProjectId> pIdRes = ProjectId.create(command.projectId());
            if (pIdRes.isFailure()) return Result.failure(pIdRes.getError().get());
            ProjectId pId = pIdRes.getDataOrThrow();
            workspace.addProject(pId, now);
            workspace.setActiveProject(pId, now);
        } else {
            workspace.setActiveProject(null, now);
        }

        repository.save(workspace);

        eventPublisher.publish(new WorkspaceChangedDomainEvent(
                id,
                workspace.getActiveProjectId().orElse(null),
                workspace.getProjectIds().size(),
                now
        ));

        return Result.success(WorkspaceMapper.toDto(workspace));
    }

    @Override
    public Result<WorkspaceDto> execute(GetWorkspaceDetailQuery query) {
        if (query == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Query cannot be null"));
        }

        Result<WorkspaceId> idRes = WorkspaceId.create(query.workspaceId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        return repository.findById(idRes.getDataOrThrow())
                .map(WorkspaceMapper::toDto)
                .map(Result::success)
                .orElseGet(() -> Result.failure(AppError.resourceNotFound(AppError.WORKSPACE_NOT_FOUND,
                        "Workspace not found with id: " + query.workspaceId())));
    }

    @Override
    public Result<List<WorkspaceDto>> execute(GetWorkspaceListQuery query) {
        List<WorkspaceDto> dtos = repository.findAll().stream()
                .map(WorkspaceMapper::toDto)
                .toList();
        return Result.success(dtos);
    }
}
