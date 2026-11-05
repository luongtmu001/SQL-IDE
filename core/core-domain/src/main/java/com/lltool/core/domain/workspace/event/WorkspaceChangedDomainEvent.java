package com.lltool.core.domain.workspace.event;

import com.lltool.core.common.event.AppEvent;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.workspace.valueobject.WorkspaceId;

import java.time.Instant;

public record WorkspaceChangedDomainEvent(
        WorkspaceId workspaceId,
        ProjectId activeProjectId,
        int projectCount,
        Instant occurredAt
) implements AppEvent {}

