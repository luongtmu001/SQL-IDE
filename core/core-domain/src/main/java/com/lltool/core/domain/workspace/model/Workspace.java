package com.lltool.core.domain.workspace.model;

import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.workspace.valueobject.WorkspaceId;
import com.lltool.core.domain.workspace.valueobject.WorkspaceName;
import com.lltool.core.domain.workspace.valueobject.WorkspaceState;

import java.time.Instant;
import java.util.*;

/**
 * Workspace Aggregate Root.
 * Represents an active working context holding projects, active project, and persisted UI state.
 */
public class Workspace {

    private final WorkspaceId id;
    private WorkspaceName name;
    private final Set<ProjectId> projectIds = new LinkedHashSet<>();
    private ProjectId activeProjectId;
    private WorkspaceState state;
    private final Instant createdAt;
    private Instant updatedAt;

    public Workspace(WorkspaceId id, WorkspaceName name, Instant createdAt, Instant updatedAt) {
        this(id, name, WorkspaceState.empty(), createdAt, updatedAt);
    }

    public Workspace(WorkspaceId id, WorkspaceName name, WorkspaceState state, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "WorkspaceId cannot be null");
        this.name = Objects.requireNonNull(name, "WorkspaceName cannot be null");
        this.state = state != null ? state : WorkspaceState.empty();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public static Workspace create(WorkspaceId id, WorkspaceName name, Instant now) {
        return new Workspace(id, name, WorkspaceState.empty(), now, now);
    }

    public static Workspace create(WorkspaceId id, WorkspaceName name, WorkspaceState state, Instant now) {
        return new Workspace(id, name, state, now, now);
    }

    public void addProject(ProjectId projectId, Instant now) {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        projectIds.add(projectId);
        if (activeProjectId == null) {
            activeProjectId = projectId;
        }
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void removeProject(ProjectId projectId, Instant now) {
        projectIds.remove(projectId);
        if (Objects.equals(activeProjectId, projectId)) {
            activeProjectId = projectIds.isEmpty() ? null : projectIds.iterator().next();
        }
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void setActiveProject(ProjectId projectId, Instant now) {
        if (projectId != null && !projectIds.contains(projectId)) {
            throw new IllegalArgumentException("Cannot activate a project that does not belong to this workspace");
        }
        this.activeProjectId = projectId;
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void rename(WorkspaceName newName, Instant now) {
        Objects.requireNonNull(newName);
        if (!this.name.equals(newName)) {
            this.name = newName;
            this.updatedAt = Objects.requireNonNull(now);
        }
    }

    public void updateState(WorkspaceState newState, Instant now) {
        this.state = Objects.requireNonNull(newState, "newState cannot be null");
        this.updatedAt = Objects.requireNonNull(now, "now cannot be null");
    }

    // Getters
    public WorkspaceId getId() {
        return id;
    }

    public WorkspaceName getName() {
        return name;
    }

    public WorkspaceState getState() {
        return state != null ? state : WorkspaceState.empty();
    }

    public Set<ProjectId> getProjectIds() {
        return Collections.unmodifiableSet(projectIds);
    }

    public Optional<ProjectId> getActiveProjectId() {
        return Optional.ofNullable(activeProjectId);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Workspace workspace = (Workspace) o;
        return Objects.equals(id, workspace.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

