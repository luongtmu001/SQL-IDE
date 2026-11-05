package com.lltool.core.application.port;

import com.lltool.core.domain.workspace.model.Workspace;
import com.lltool.core.domain.workspace.repository.WorkspaceRepository;
import com.lltool.core.domain.workspace.valueobject.WorkspaceId;
import com.lltool.core.domain.workspace.valueobject.WorkspaceName;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWorkspaceRepository implements WorkspaceRepository {

    private final Map<WorkspaceId, Workspace> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Workspace> findById(WorkspaceId id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<Workspace> findByName(WorkspaceName name) {
        if (name == null) return Optional.empty();
        return storage.values().stream()
                .filter(w -> w.getName().equals(name))
                .findFirst();
    }

    @Override
    public List<Workspace> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void save(Workspace workspace) {
        Objects.requireNonNull(workspace, "Workspace cannot be null");
        storage.put(workspace.getId(), workspace);
    }

    @Override
    public void delete(WorkspaceId id) {
        if (id != null) {
            storage.remove(id);
        }
    }

    @Override
    public boolean existsById(WorkspaceId id) {
        return id != null && storage.containsKey(id);
    }

    public void clear() {
        storage.clear();
    }
}

