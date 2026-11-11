package com.lltool.core.domain.visualizer;

import java.util.Objects;

/**
 * Directed link between two pipeline stages or operators.
 */
public class QueryFlowEdge {
    private final String id;
    private final String fromNodeId;
    private final String toNodeId;
    private final QueryEdgeType edgeType;
    private final String label;

    public QueryFlowEdge(String fromNodeId, String toNodeId, QueryEdgeType edgeType, String label) {
        this.fromNodeId = Objects.requireNonNull(fromNodeId, "fromNodeId must not be null");
        this.toNodeId = Objects.requireNonNull(toNodeId, "toNodeId must not be null");
        this.id = fromNodeId + "->" + toNodeId;
        this.edgeType = edgeType != null ? edgeType : QueryEdgeType.DATA_FLOW;
        this.label = label != null ? label : "";
    }

    public static QueryFlowEdge of(String fromNodeId, String toNodeId, String label) {
        return new QueryFlowEdge(fromNodeId, toNodeId, QueryEdgeType.DATA_FLOW, label);
    }

    public String getId() {
        return id;
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public QueryEdgeType getEdgeType() {
        return edgeType;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryFlowEdge that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "QueryFlowEdge{" +
                "fromNodeId='" + fromNodeId + '\'' +
                ", toNodeId='" + toNodeId + '\'' +
                ", label='" + label + '\'' +
                '}';
    }
}
