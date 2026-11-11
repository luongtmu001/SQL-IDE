package com.lltool.core.domain.schemacompare.model;

/**
 * Kết quả sinh script migration DDL tổng hợp.
 */
public record MigrationScriptResult(
        String sql,
        int statementCount,
        boolean hasDestructive,
        int objectCount
) {
    public MigrationScriptResult {
        if (sql == null) sql = "";
    }
}

