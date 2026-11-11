package com.lltool.core.domain.schemacompare.model;

/**
 * Biểu diễn khác biệt giữa một cột trong bảng của Source và Target.
 */
public record ColumnDiff(
        String name,
        String sourceType,
        String targetType,
        Boolean sourceNullable,
        Boolean targetNullable,
        String action // "add", "drop", "modify"
) {
    public ColumnDiff {
        if (name == null) name = "";
        if (action == null) action = "modify";
    }

    public boolean isAdded() {
        return "add".equalsIgnoreCase(action);
    }

    public boolean isDropped() {
        return "drop".equalsIgnoreCase(action);
    }

    public boolean isModified() {
        return "modify".equalsIgnoreCase(action);
    }
}

