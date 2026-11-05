package com.lltool.infrastructure.database.api.metadata;

/**
 * Constraint metadata record (Check, Default, etc.).
 */
public record ConstraintMetadata(
        String name,
        String type,
        String tableName,
        String definition
) {
    public ConstraintMetadata(String name, String type, String tableName) {
        this(name, type, tableName, null);
    }
}

