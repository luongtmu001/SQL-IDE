package com.lltool.infrastructure.database.api.metadata;

import java.io.Serializable;
import java.util.Objects;

/**
 * Metadata descriptor for a database trigger (Phase 6.3).
 */
public record TriggerMetadata(
        String name,
        String table,
        String schema,
        String event, // "INSERT", "UPDATE", "DELETE"
        String timing // "BEFORE", "AFTER", "INSTEAD OF"
) implements Serializable {

    public TriggerMetadata {
        Objects.requireNonNull(name, "Trigger name cannot be null");
    }
}

