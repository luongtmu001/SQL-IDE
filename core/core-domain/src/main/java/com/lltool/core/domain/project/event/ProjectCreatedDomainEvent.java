package com.lltool.core.domain.project.event;

import com.lltool.core.common.event.AppEvent;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.project.valueobject.ProjectName;
import com.lltool.core.domain.project.valueobject.ProjectPath;

import java.time.Instant;

public record ProjectCreatedDomainEvent(
        ProjectId projectId,
        ProjectName projectName,
        ProjectPath projectPath,
        Instant occurredAt
) implements AppEvent {}

