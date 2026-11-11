package com.lltool.core.domain.dependency;

/**
 * Semantic relationships between graph objects.
 */
public enum RelationType {
    USES_COLUMN("Uses Column"),
    USES_TABLE("Uses Table"),
    REFERENCES_VIEW("References View"),
    CALLS_FUNCTION("Calls Function"),
    HAS_TRIGGER("Has Trigger"),
    HAS_INDEX("Has Index"),
    FOREIGN_KEY_TO("Foreign Key To"),
    DEPENDS_ON("Depends On"),
    UNKNOWN("Unknown");

    private final String label;

    RelationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static RelationType fromString(String val) {
        if (val == null || val.isBlank()) return DEPENDS_ON;
        String clean = val.trim().toUpperCase();
        for (RelationType rel : values()) {
            if (rel.name().equals(clean)) return rel;
        }
        return DEPENDS_ON;
    }
}
