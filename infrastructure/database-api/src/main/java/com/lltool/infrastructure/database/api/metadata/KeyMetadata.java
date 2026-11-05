package com.lltool.infrastructure.database.api.metadata;

import java.util.List;

/**
 * Key metadata record (Primary Key, Foreign Key, Unique Key).
 */
public record KeyMetadata(
        String name,
        String type,
        String tableName,
        List<String> columns,
        String referencedTable,
        List<String> referencedColumns
) {
    public KeyMetadata(String name, String type, String tableName) {
        this(name, type, tableName, List.of(), null, List.of());
    }

    public KeyMetadata(String name, String type, String tableName, List<String> columns) {
        this(name, type, tableName, columns, null, List.of());
    }
}

