package com.lltool.infrastructure.database.api.metadata;

import java.io.Serializable;
import java.util.Objects;

/**
 * Metadata descriptor for a database table or view (Phase 6.3).
 */
public record TableMetadata(
        String name,
        String schema,
        String catalog,
        String type, // "TABLE" or "VIEW"
        long estimatedRows,
        String comment
) implements Serializable {

    public TableMetadata {
        Objects.requireNonNull(name, "Table name cannot be null");
        type = type != null ? type : "TABLE";
    }

    public static TableMetadata table(String name, String schema, String catalog) {
        return new TableMetadata(name, schema, catalog, "TABLE", 0, null);
    }

    public static TableMetadata view(String name, String schema, String catalog) {
        return new TableMetadata(name, schema, catalog, "VIEW", 0, null);
    }

    public boolean isView() {
        return "VIEW".equalsIgnoreCase(type);
    }
}

