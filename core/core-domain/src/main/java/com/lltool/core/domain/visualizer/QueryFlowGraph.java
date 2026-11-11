package com.lltool.core.domain.visualizer;

import java.util.*;

/**
 * Entire SQL query flow graph representing data pipeline transformation stages.
 */
public class QueryFlowGraph {
    private final String sqlText;
    private final Map<String, QueryFlowNode> nodes = new LinkedHashMap<>();
    private final List<QueryFlowEdge> edges = new ArrayList<>();
    private final List<String> stageTitles = new ArrayList<>();
    private boolean unsupported = false;
    private String unsupportedReason = "";

    public QueryFlowGraph(String sqlText) {
        this.sqlText = sqlText != null ? sqlText : "";
    }

    public boolean isUnsupported() {
        return unsupported;
    }

    public void setUnsupported(boolean unsupported, String reason) {
        this.unsupported = unsupported;
        this.unsupportedReason = reason != null ? reason : "";
    }

    public String getUnsupportedReason() {
        return unsupportedReason;
    }

    public static QueryFlowGraph unsupported(String sqlText, String reason) {
        QueryFlowGraph g = new QueryFlowGraph(sqlText);
        g.setUnsupported(true, reason);
        return g;
    }

    public void addNode(QueryFlowNode node) {
        if (node != null) {
            nodes.put(node.getId(), node);
        }
    }

    public void addEdge(QueryFlowEdge edge) {
        if (edge != null && !edges.contains(edge)) {
            edges.add(edge);
        }
    }

    public void addStageTitle(String stageTitle) {
        if (stageTitle != null) {
            stageTitles.add(stageTitle);
        }
    }

    public String getSqlText() {
        return sqlText;
    }

    public Map<String, QueryFlowNode> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public List<QueryFlowEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public List<String> getStageTitles() {
        return Collections.unmodifiableList(stageTitles);
    }

    public QueryFlowNode getNode(String id) {
        return nodes.get(id);
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    /**
     * Finds the node that encloses the specified 1-based text coordinates (line, col).
     */
    public Optional<QueryFlowNode> findNodeAtPosition(int line, int col) {
        for (QueryFlowNode node : nodes.values()) {
            if (node.getSourceRange() != null && node.getSourceRange().contains(line, col)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    public List<QueryFlowNode> getNodesByStage(int stageIndex) {
        List<QueryFlowNode> result = new ArrayList<>();
        for (QueryFlowNode node : nodes.values()) {
            if (node.getStageIndex() == stageIndex) {
                result.add(node);
            }
        }
        return result;
    }
}
