package com.lltool.core.domain.dependency;

/**
 * Types of database objects supported in dependency graphs.
 */
public enum DependencyNodeType {
    DATABASE("Database", "fth-database"),
    SCHEMA("Schema", "fth-folder"),
    TABLE("Table", "fth-grid"),
    COLUMN("Column", "fth-columns"),
    VIEW("View", "fth-eye"),
    MATERIALIZED_VIEW("Materialized View", "fth-layers"),
    FUNCTION("Function", "fth-zap"),
    PROCEDURE("Procedure", "fth-terminal"),
    TRIGGER("Trigger", "fth-bell"),
    INDEX("Index", "fth-bookmark"),
    FOREIGN_KEY("Foreign Key", "fth-link-2"),
    UNKNOWN("Unknown", "fth-help-circle");

    private final String displayName;
    private final String iconLiteral;

    DependencyNodeType(String displayName, String iconLiteral) {
        this.displayName = displayName;
        this.iconLiteral = iconLiteral;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconLiteral() {
        return iconLiteral;
    }

    public static DependencyNodeType fromString(String val) {
        if (val == null || val.isBlank()) return UNKNOWN;
        String clean = val.trim().toUpperCase();
        for (DependencyNodeType type : values()) {
            if (type.name().equals(clean)) return type;
        }
        if (clean.equals("MATVIEW") || clean.equals("MAT_VIEW") || clean.equals("MVIEW")) return MATERIALIZED_VIEW;
        if (clean.equals("FUNC") || clean.equals("FN")) return FUNCTION;
        if (clean.equals("PROC") || clean.equals("SP") || clean.equals("STORED_PROCEDURE")) return PROCEDURE;
        if (clean.equals("TRIG") || clean.equals("TRG")) return TRIGGER;
        if (clean.equals("FK")) return FOREIGN_KEY;
        if (clean.equals("COL") || clean.equals("FIELD")) return COLUMN;
        if (clean.equals("TBL") || clean.equals("RELATION")) return TABLE;
        if (clean.equals("IDX")) return INDEX;
        return UNKNOWN;
    }
}
