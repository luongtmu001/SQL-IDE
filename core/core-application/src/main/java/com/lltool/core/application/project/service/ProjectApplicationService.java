package com.lltool.core.application.project.service;

import com.lltool.core.application.project.command.CreateProjectCommand;
import com.lltool.core.application.project.command.DeleteProjectCommand;
import com.lltool.core.application.project.command.RenameProjectCommand;
import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.application.project.mapper.ProjectMapper;
import com.lltool.core.application.project.query.GetProjectDetailQuery;
import com.lltool.core.application.project.query.GetProjectListQuery;
import com.lltool.core.application.project.usecase.*;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.event.EventPublisher;
import com.lltool.core.common.result.Result;
import com.lltool.core.common.time.TimeProvider;
import com.lltool.core.domain.project.event.ProjectCreatedDomainEvent;
import com.lltool.core.domain.project.event.ProjectDeletedDomainEvent;
import com.lltool.core.domain.project.event.ProjectRenamedDomainEvent;
import com.lltool.core.domain.project.model.Project;
import com.lltool.core.domain.project.repository.ProjectRepository;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.project.valueobject.ProjectName;
import com.lltool.core.domain.project.valueobject.ProjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service coordinating Project use cases and enforcing application-level workflows.
 */
public class ProjectApplicationService implements
        CreateProjectUseCase,
        RenameProjectUseCase,
        DeleteProjectUseCase,
        GetProjectDetailUseCase,
        GetProjectListUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProjectApplicationService.class);

    private final ProjectRepository repository;
    private final EventPublisher eventPublisher;
    private final TimeProvider timeProvider;

    public ProjectApplicationService(ProjectRepository repository, EventPublisher eventPublisher, TimeProvider timeProvider) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
    }

    @Override
    public Result<ProjectDto> execute(CreateProjectCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<ProjectName> nameRes = ProjectName.create(command.name());
        if (nameRes.isFailure()) {
            return Result.failure(nameRes.getError().get());
        }

        Result<ProjectPath> pathRes = ProjectPath.create(command.path());
        if (pathRes.isFailure()) {
            return Result.failure(pathRes.getError().get());
        }

        ProjectName name = nameRes.getDataOrThrow();
        ProjectPath path = pathRes.getDataOrThrow();

        if (repository.existsByName(name)) {
            return Result.failure(AppError.business(AppError.PROJECT_ALREADY_EXISTS,
                    "A project with name '" + name.value() + "' already exists."));
        }

        Instant now = timeProvider.now();
        ProjectId id = ProjectId.generate();
        Project project = Project.create(id, name, path, now);

        repository.save(project);
        log.info("Created project: {} (id={})", name.value(), id.value());

        eventPublisher.publish(new ProjectCreatedDomainEvent(id, name, path, now));

        return Result.success(ProjectMapper.toDto(project));
    }

    @Override
    public Result<ProjectDto> execute(RenameProjectCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<ProjectId> idRes = ProjectId.create(command.projectId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        Result<ProjectName> newNameRes = ProjectName.create(command.newName());
        if (newNameRes.isFailure()) return Result.failure(newNameRes.getError().get());

        ProjectId id = idRes.getDataOrThrow();
        ProjectName newName = newNameRes.getDataOrThrow();

        Optional<Project> optProject = repository.findById(id);
        if (optProject.isEmpty()) {
            return Result.failure(AppError.resourceNotFound(AppError.PROJECT_NOT_FOUND,
                    "Project not found with id: " + id.value()));
        }

        Project project = optProject.get();
        if (!project.getName().equals(newName) && repository.existsByName(newName)) {
            return Result.failure(AppError.business(AppError.PROJECT_ALREADY_EXISTS,
                    "Another project with name '" + newName.value() + "' already exists."));
        }

        ProjectName oldName = project.getName();
        Instant now = timeProvider.now();
        project.rename(newName, now);

        repository.save(project);
        log.info("Renamed project {} to {}", oldName.value(), newName.value());

        eventPublisher.publish(new ProjectRenamedDomainEvent(id, oldName, newName, now));

        return Result.success(ProjectMapper.toDto(project));
    }

    @Override
    public Result<Void> execute(DeleteProjectCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<ProjectId> idRes = ProjectId.create(command.projectId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        ProjectId id = idRes.getDataOrThrow();
        if (!repository.existsById(id)) {
            return Result.failure(AppError.resourceNotFound(AppError.PROJECT_NOT_FOUND,
                    "Project not found with id: " + id.value()));
        }

        repository.delete(id);
        log.info("Deleted project id: {}", id.value());

        eventPublisher.publish(new ProjectDeletedDomainEvent(id, timeProvider.now()));

        return Result.success();
    }

    @Override
    public Result<ProjectDto> execute(GetProjectDetailQuery query) {
        if (query == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Query cannot be null"));
        }

        Result<ProjectId> idRes = ProjectId.create(query.projectId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        ProjectId id = idRes.getDataOrThrow();
        return repository.findById(id)
                .map(ProjectMapper::toDto)
                .map(Result::success)
                .orElseGet(() -> Result.failure(AppError.resourceNotFound(AppError.PROJECT_NOT_FOUND,
                        "Project not found with id: " + id.value())));
    }

    @Override
    public Result<List<ProjectDto>> execute(GetProjectListQuery query) {
        List<Project> projects = repository.findAll();
        boolean activeOnly = query != null && query.activeOnly();

        List<ProjectDto> dtos = projects.stream()
                .filter(p -> !activeOnly || p.isActive())
                .map(ProjectMapper::toDto)
                .toList();

        return Result.success(dtos);
    }
}

