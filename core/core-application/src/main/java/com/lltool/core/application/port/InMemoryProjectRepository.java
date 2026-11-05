package com.lltool.core.application.port;

import com.lltool.core.domain.project.model.Project;
import com.lltool.core.domain.project.repository.ProjectRepository;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.project.valueobject.ProjectName;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe In-Memory implementation of ProjectRepository.
 * Used for testing, initial platform bootstrapping, and mock execution.
 */
public class InMemoryProjectRepository implements ProjectRepository {

    private final Map<ProjectId, Project> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Project> findById(ProjectId id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<Project> findByName(ProjectName name) {
        if (name == null) return Optional.empty();
        return storage.values().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst();
    }

    @Override
    public List<Project> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void save(Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        storage.put(project.getId(), project);
    }

    @Override
    public void delete(ProjectId id) {
        if (id != null) {
            storage.remove(id);
        }
    }

    @Override
    public boolean existsById(ProjectId id) {
        return id != null && storage.containsKey(id);
    }

    @Override
    public boolean existsByName(ProjectName name) {
        if (name == null) return false;
        return storage.values().stream().anyMatch(p -> p.getName().equals(name));
    }

    public void clear() {
        storage.clear();
    }
}

