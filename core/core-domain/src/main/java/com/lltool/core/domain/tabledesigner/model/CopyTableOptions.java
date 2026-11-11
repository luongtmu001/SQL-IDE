package com.lltool.core.domain.tabledesigner.model;

import java.io.Serializable;

/**
 * Options configuring how a table design is duplicated (SCR-TD-001 / TD5).
 */
public record CopyTableOptions(
        boolean copyColumns,
        boolean copyPrimaryKey,
        boolean copyIndexes,
        boolean copyForeignKeys,
        boolean copyConstraints,
        boolean copyTriggers
) implements Serializable {

    /**
     * Default options matching Python LLTool behavior:
     * Columns, PK, Indexes, FKs, Unique & Check are copied by default;
     * Triggers are disabled by default (requiring explicit review).
     */
    public static CopyTableOptions defaults() {
        return new CopyTableOptions(true, true, true, true, true, false);
    }
}

