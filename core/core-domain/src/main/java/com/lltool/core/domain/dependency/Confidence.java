package com.lltool.core.domain.dependency;

/**
 * Degree of confidence in discovered dependency.
 */
public enum Confidence {
    CONFIRMED("Đã xác nhận (Catalog)"),
    LIKELY("Độ tin cậy cao (AST)"),
    POSSIBLE("Suy đoán (Text/Heuristic)");

    private final String description;

    Confidence(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
