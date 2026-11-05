package com.lltool.core.domain.project.model;

import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.project.valueobject.ProjectName;
import com.lltool.core.domain.project.valueobject.ProjectPath;

import java.time.Instant;
import java.util.Objects;

/**
 * Core Project Aggregate Root.
 * Fully encapsulated business entity enforcing life-cycle invariants.
 */
public class Project {

    private final ProjectId id;
    private ProjectName name;
    private ProjectPath path;
    private final Instant createdAt;
    private Instant updatedAt;
    private boolean active;

    public Project(ProjectId id, ProjectName name, ProjectPath path, Instant createdAt, Instant updatedAt, boolean active) {
        this.id = Objects.requireNonNull(id, "ProjectId cannot be null");
        this.name = Objects.requireNonNull(name, "ProjectName cannot be null");
        this.path = Objects.requireNonNull(path, "ProjectPath cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.active = active;
    }

    public static Project create(ProjectId id, ProjectName name, ProjectPath path, Instant now) {
        return new Project(id, name, path, now, now, true);
    }

    public void rename(ProjectName newName, Instant now) {
        Objects.requireNonNull(newName, "newName cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        if (!this.name.equals(newName)) {
            this.name = newName;
            this.updatedAt = now;
        }
    }

    public void relocate(ProjectPath newPath, Instant now) {
        Objects.requireNonNull(newPath, "newPath cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        if (!this.path.equals(newPath)) {
            this.path = newPath;
            this.updatedAt = now;
        }
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = Objects.requireNonNull(now);
    }

    // Getters
    public ProjectId getId() {
        return id;
    }

    public ProjectName getName() {
        return name;
    }

    public ProjectPath getPath() {
        return path;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(id, project.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Project{id=" + id.value() + ", name=" + name.value() + ", active=" + active + '}';
    }
}

