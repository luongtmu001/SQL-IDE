package com.lltool.core.domain.dependency;

/**
 * Direct vs Indirect dependency classification.
 */
public enum DependencyType {
    DIRECT("Trực tiếp"),
    INDIRECT("Gián tiếp");

    private final String title;

    DependencyType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
