package app.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates a standardized, parsed SQL Physical Execution Plan.
 * Directly corresponds to PhysicalExecutionPlan in Python query_plan_service.py.
 */
public class PhysicalExecutionPlan {

    private String dialect = "sqlserver";
    private String planType = "estimated"; // "estimated" or "actual"
    private double totalCost = 0.0;
    private PlanOperatorNode rootNode;
    private String rawPlanText = "";
    private Long executionDurationMs = null;
    private List<PlanOperatorNode> allNodes = new ArrayList<>();

    public PhysicalExecutionPlan() {
    }

    public PhysicalExecutionPlan(String dialect, String planType, double totalCost, PlanOperatorNode rootNode) {
        this.dialect = dialect;
        this.planType = planType;
        this.totalCost = totalCost;
        this.rootNode = rootNode;
        if (rootNode != null) {
            flatten(rootNode);
        }
    }

    public void setRootNode(PlanOperatorNode rootNode) {
        this.rootNode = rootNode;
        this.allNodes.clear();
        if (rootNode != null) {
            flatten(rootNode);
        }
    }

    private void flatten(PlanOperatorNode node) {
        allNodes.add(node);
        for (PlanOperatorNode child : node.getChildren()) {
            flatten(child);
        }
    }

    public PlanOperatorNode findHighestCostNode() {
        PlanOperatorNode highest = null;
        for (PlanOperatorNode node : allNodes) {
            if (highest == null || node.getCostPercent() > highest.getCostPercent()) {
                highest = node;
            }
        }
        return highest;
    }

    public int getNodeCount() {
        return allNodes.size();
    }

    /**
     * Generates a clean indented text tree representation of the execution plan.
     */
    public String toTextTree() {
        StringBuilder sb = new StringBuilder();
        sb.append("Execution Plan (").append(dialect.toUpperCase()).append(" - ").append(planType.toUpperCase()).append(")\n");
        sb.append("Total Cost: ").append(String.format("%.4f", totalCost)).append(" | Total Operators: ").append(allNodes.size()).append("\n");
        if (executionDurationMs != null) {
            sb.append("Execution Duration: ").append(executionDurationMs).append(" ms\n");
        }
        sb.append("--------------------------------------------------------------------------------\n");
        if (rootNode != null) {
            renderTextNode(rootNode, sb, "", true);
        } else {
            sb.append("(Empty plan)\n");
        }
        return sb.toString();
    }

    private void renderTextNode(PlanOperatorNode node, StringBuilder sb, String prefix, boolean isTail) {
        sb.append(prefix).append(isTail ? "└── " : "├── ");
        sb.append(node.getOperatorType());
        if (node.getRelationName() != null && !node.getRelationName().isBlank()) {
            sb.append(" [").append(node.getRelationName()).append("]");
        }
        sb.append(" (Cost: ").append(String.format("%.1f%%", node.getCostPercent()));
        sb.append(", SubtreeCost: ").append(String.format("%.4f", node.getSubtreeCost()));
        sb.append(", EstRows: ").append((long) node.getEstimatedRows());
        if (node.getActualRows() != null) {
            sb.append(", ActRows: ").append(node.getActualRows().longValue());
        }
        if (node.getActualTimeMs() != null) {
            sb.append(", Time: ").append(String.format("%.2f ms", node.getActualTimeMs()));
        }
        sb.append(")\n");

        List<PlanOperatorNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean childIsTail = (i == children.size() - 1);
            renderTextNode(children.get(i), sb, prefix + (isTail ? "    " : "│   "), childIsTail);
        }
    }

    // Getters and Setters
    public String getDialect() { return dialect; }
    public void setDialect(String dialect) { this.dialect = dialect; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public PlanOperatorNode getRootNode() { return rootNode; }

    public String getRawPlanText() { return rawPlanText; }
    public void setRawPlanText(String rawPlanText) { this.rawPlanText = rawPlanText; }

    public Long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public List<PlanOperatorNode> getAllNodes() { return allNodes; }
}

