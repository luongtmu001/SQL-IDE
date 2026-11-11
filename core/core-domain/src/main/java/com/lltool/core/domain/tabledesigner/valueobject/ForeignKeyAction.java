package com.lltool.core.domain.tabledesigner.valueobject;

/**
 * Referential integrity action for foreign keys on UPDATE or DELETE.
 */
public enum ForeignKeyAction {
    NO_ACTION("NO ACTION"),
    CASCADE("CASCADE"),
    SET_NULL("SET NULL"),
    SET_DEFAULT("SET DEFAULT"),
    RESTRICT("RESTRICT");

    private final String sqlKeyword;

    ForeignKeyAction(String sqlKeyword) {
        this.sqlKeyword = sqlKeyword;
    }

    public String sqlKeyword() {
        return sqlKeyword;
    }

    public static ForeignKeyAction fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NO_ACTION;
        }
        String clean = raw.trim().toUpperCase().replace("-", " ").replace("_", " ");
        for (ForeignKeyAction action : values()) {
            if (action.name().equals(clean) || action.sqlKeyword.equals(clean)) {
                return action;
            }
        }
        return NO_ACTION;
    }
}

