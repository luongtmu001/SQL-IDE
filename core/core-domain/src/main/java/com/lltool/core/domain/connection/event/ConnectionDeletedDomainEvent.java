package com.lltool.core.domain.connection.event;

import com.lltool.core.common.event.AppEvent;
import com.lltool.core.domain.connection.valueobject.ConnectionId;

import java.time.Instant;

public record ConnectionDeletedDomainEvent(
        ConnectionId connectionId,
        Instant occurredAt
) implements AppEvent {}

