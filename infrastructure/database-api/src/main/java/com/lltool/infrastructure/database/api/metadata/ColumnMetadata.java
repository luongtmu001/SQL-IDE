package com.lltool.infrastructure.database.api.metadata;

import java.io.Serializable;
import java.util.Objects;

/**
 * Metadata descriptor for a column (Phase 6.3).
 */
public record ColumnMetadata(
        String name,
        String dataType,
        int columnSize,
        boolean nullable,
        boolean primaryKey,
        boolean foreignKey,
        String defaultValue,
        int ordinalPosition
) implements Serializable {

    public ColumnMetadata {
        Objects.requireNonNull(name, "Column name cannot be null");
        dataType = dataType != null ? dataType : "VARCHAR";
    }

    public static ColumnMetadata of(String name, String dataType, boolean isPk) {
        return new ColumnMetadata(name, dataType, 0, !isPk, isPk, false, null, 0);
    }
}

