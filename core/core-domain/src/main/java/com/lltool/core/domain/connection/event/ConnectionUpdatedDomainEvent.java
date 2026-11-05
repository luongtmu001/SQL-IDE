package com.lltool.core.domain.connection.event;

import com.lltool.core.common.event.AppEvent;
import com.lltool.core.domain.connection.valueobject.ConnectionDatabaseType;
import com.lltool.core.domain.connection.valueobject.ConnectionId;
import com.lltool.core.domain.connection.valueobject.ConnectionName;

import java.time.Instant;

public record ConnectionUpdatedDomainEvent(
        ConnectionId connectionId,
        ConnectionName connectionName,
        ConnectionDatabaseType databaseType,
        Instant occurredAt
) implements AppEvent {}

