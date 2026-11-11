package com.lltool.core.domain.visualizer;

/**
 * Types of connections between SQL visual pipeline operators.
 */
public enum QueryEdgeType {
    DATA_FLOW("Data Flow"),
    JOIN_INPUT("Join Input"),
    FILTER_PASS("Filter Pass"),
    AGGREGATION_FLOW("Aggregation Flow"),
    OUTPUT_STREAM("Output Stream");

    private final String label;

    QueryEdgeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
