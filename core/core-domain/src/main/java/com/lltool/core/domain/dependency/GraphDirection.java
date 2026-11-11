package com.lltool.core.domain.dependency;

/**
 * Traversal direction for dependency exploration.
 */
public enum GraphDirection {
    DEPENDENTS("Được dùng bởi (Referenced By)"),
    DEPENDENCIES("Phụ thuộc vào (Depends On)"),
    BOTH("Cả hai chiều");

    private final String label;

    GraphDirection(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static GraphDirection fromString(String val) {
        if (val == null || val.isBlank()) return BOTH;
        String clean = val.trim().toUpperCase();
        for (GraphDirection d : values()) {
            if (d.name().equals(clean)) return d;
        }
        if (clean.contains("DEPENDENT") || clean.contains("REFERENCED")) return DEPENDENTS;
        if (clean.contains("DEPENDENC") || clean.contains("USES")) return DEPENDENCIES;
        return BOTH;
    }
}
