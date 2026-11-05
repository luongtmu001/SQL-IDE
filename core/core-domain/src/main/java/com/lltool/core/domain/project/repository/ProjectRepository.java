package com.lltool.core.domain.project.repository;

import com.lltool.core.domain.project.model.Project;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.project.valueobject.ProjectName;

import java.util.List;
import java.util.Optional;

/**
 * Domain Repository Interface for Project Aggregate.
 * Implementation belongs to Infrastructure or In-Memory test adapters.
 */
public interface ProjectRepository {

    Optional<Project> findById(ProjectId id);

    Optional<Project> findByName(ProjectName name);

    List<Project> findAll();

    void save(Project project);

    void delete(ProjectId id);

    boolean existsById(ProjectId id);

    boolean existsByName(ProjectName name);
}

