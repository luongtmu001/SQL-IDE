package com.lltool.core.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base contract for application and domain events.
 * Fully decoupled from JavaFX and any specific transport.
 */
public interface AppEvent {

    default String eventId() {
        return UUID.randomUUID().toString();
    }

    Instant occurredAt();

    default String eventType() {
        return getClass().getSimpleName();
    }
}

