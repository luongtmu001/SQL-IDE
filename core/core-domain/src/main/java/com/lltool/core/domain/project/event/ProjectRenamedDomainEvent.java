package com.lltool.core.domain.project.event;

import com.lltool.core.common.event.AppEvent;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.project.valueobject.ProjectName;

import java.time.Instant;

public record ProjectRenamedDomainEvent(
        ProjectId projectId,
        ProjectName oldName,
        ProjectName newName,
        Instant occurredAt
) implements AppEvent {}

