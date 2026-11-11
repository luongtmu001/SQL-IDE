package com.lltool.core.domain.dependency;

import java.util.*;

/**
 * Entire dependency graph with node map, edge set, and graph properties.
 */
public class DependencyGraphModel {
    private final String rootNodeId;
    private final GraphDirection direction;
    private final int depth;
    private final Map<String, DependencyNode> nodes = new LinkedHashMap<>();
    private final List<DependencyEdge> edges = new ArrayList<>();
    private final List<SuggestedStep> suggestedSteps = new ArrayList<>();

    public DependencyGraphModel(String rootNodeId, GraphDirection direction, int depth) {
        this.rootNodeId = Objects.requireNonNull(rootNodeId, "rootNodeId must not be null");
        this.direction = direction != null ? direction : GraphDirection.BOTH;
        this.depth = depth > 0 ? depth : 2;
    }

    public void addNode(DependencyNode node) {
        if (node != null) {
            nodes.put(node.getId(), node);
        }
    }

    public void addEdge(DependencyEdge edge) {
        if (edge != null && !edges.contains(edge)) {
            edges.add(edge);
        }
    }

    public void addSuggestedStep(SuggestedStep step) {
        if (step != null) {
            suggestedSteps.add(step);
        }
    }

    public String getRootNodeId() {
        return rootNodeId;
    }

    public DependencyNode getRootNode() {
        return nodes.get(rootNodeId);
    }

    public GraphDirection getDirection() {
        return direction;
    }

    public int getDepth() {
        return depth;
    }

    public Map<String, DependencyNode> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public List<DependencyEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public List<SuggestedStep> getSuggestedSteps() {
        return Collections.unmodifiableList(suggestedSteps);
    }

    public DependencyNode getNode(String id) {
        return nodes.get(id);
    }

    public List<DependencyEdge> getOutgoingEdges(String nodeId) {
        List<DependencyEdge> result = new ArrayList<>();
        for (DependencyEdge edge : edges) {
            if (edge.getSourceId().equals(nodeId)) {
                result.add(edge);
            }
        }
        return result;
    }

    public List<DependencyEdge> getIncomingEdges(String nodeId) {
        List<DependencyEdge> result = new ArrayList<>();
        for (DependencyEdge edge : edges) {
            if (edge.getTargetId().equals(nodeId)) {
                result.add(edge);
            }
        }
        return result;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }
}
