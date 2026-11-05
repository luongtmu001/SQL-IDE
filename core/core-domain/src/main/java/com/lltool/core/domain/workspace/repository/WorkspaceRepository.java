package com.lltool.core.domain.workspace.repository;

import com.lltool.core.domain.workspace.model.Workspace;
import com.lltool.core.domain.workspace.valueobject.WorkspaceId;
import com.lltool.core.domain.workspace.valueobject.WorkspaceName;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {

    Optional<Workspace> findById(WorkspaceId id);

    Optional<Workspace> findByName(WorkspaceName name);

    List<Workspace> findAll();

    void save(Workspace workspace);

    void delete(WorkspaceId id);

    boolean existsById(WorkspaceId id);
}

