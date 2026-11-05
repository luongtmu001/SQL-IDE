package com.lltool.core.domain.project.event;

import com.lltool.core.common.event.AppEvent;
import com.lltool.core.domain.project.valueobject.ProjectId;

import java.time.Instant;

public record ProjectDeletedDomainEvent(
        ProjectId projectId,
        Instant occurredAt
) implements AppEvent {}

