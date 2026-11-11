package com.lltool.core.domain.visualizer;

/**
 * Node types representing individual stages and operators in a SQL Query Flow.
 */
public enum QueryNodeType {
    SOURCE_TABLE("Source Table", "fth-grid", "FROM TABLE"),
    JOIN_TABLE("Join Table", "fth-grid", "JOIN TABLE"),
    CTE("CTE Source", "fth-layers", "WITH CTE"),
    SUBQUERY("Subquery", "fth-external-link", "SUBQUERY"),
    JOIN("Join Match", "fth-link-2", "JOIN OPERATOR"),
    FILTER("Where Filter", "fth-filter", "WHERE FILTER"),
    AGGREGATE("Aggregation", "fth-hash", "GROUP & AGGREGATE"),
    SORT("Sort & Limit", "fth-bar-chart-2", "ORDER BY / TOP"),
    PROJECTION("Select Projection", "fth-columns", "SELECT PROJECTION"),
    OUTPUT("Output Stream", "fth-check-circle", "FINAL OUTPUT"),
    UNKNOWN("Operator", "fth-help-circle", "OPERATOR");

    private final String displayName;
    private final String iconLiteral;
    private final String stageLabel;

    QueryNodeType(String displayName, String iconLiteral, String stageLabel) {
        this.displayName = displayName;
        this.iconLiteral = iconLiteral;
        this.stageLabel = stageLabel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconLiteral() {
        return iconLiteral;
    }

    public String getStageLabel() {
        return stageLabel;
    }
}
