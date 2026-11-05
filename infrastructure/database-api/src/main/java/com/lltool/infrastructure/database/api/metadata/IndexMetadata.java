package com.lltool.infrastructure.database.api.metadata;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Metadata descriptor for an index (Phase 6.3).
 */
public record IndexMetadata(
        String name,
        String table,
        String schema,
        boolean unique,
        List<String> columnNames
) implements Serializable {

    public IndexMetadata {
        Objects.requireNonNull(name, "Index name cannot be null");
        columnNames = columnNames != null ? Collections.unmodifiableList(columnNames) : Collections.emptyList();
    }
}

