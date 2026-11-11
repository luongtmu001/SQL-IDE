package com.lltool.core.application.tabledesigner.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * An individual DDL migration statement produced by TableDesignDiffer.
 */
public record MigrationStatement(
        String sql,
        String description,
        StatementGroup group,
        boolean destructive,
        String objectName
) implements Serializable {

    public MigrationStatement {
        sql = sql != null ? sql.trim() : "";
        description = description != null ? description.trim() : "";
        group = group != null ? group : StatementGroup.COLUMN_ALTER;
    }

    public static MigrationStatement of(String sql, String description, StatementGroup group, boolean destructive, String objectName) {
        return new MigrationStatement(sql, description, group, destructive, objectName);
    }
}

