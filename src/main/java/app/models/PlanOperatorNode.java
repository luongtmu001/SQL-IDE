package app.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a physical or logical operator node in a standardized SQL Execution Plan.
 * Directly corresponds to PlanOperatorNode in Python query_plan_service.py.
 */
public class PlanOperatorNode {

    private String id;
    private String nodeId;
    private String operatorType;
    private String physicalOp;
    private String logicalOp;
    private String relationName = "";
    private String schema = "";
    private String database = "";
    private double estimatedRows = 0.0;
    private Double actualRows = null;
    private double subtreeCost = 0.0;
    private double operatorCost = 0.0;
    private double costPercent = 0.0;
    private String costSeverity = "low"; // "high", "medium", "low"
    private Double actualTimeMs = null;
    private Integer actualLoops = null;
    private List<String> warnings = new ArrayList<>();
    private Map<String, Object> details = new LinkedHashMap<>();
    private List<String> outputColumns = new ArrayList<>();
    private List<PlanOperatorNode> children = new ArrayList<>();

    public PlanOperatorNode() {
    }

    public PlanOperatorNode(String nodeId, String operatorType) {
        this.nodeId = nodeId;
        this.id = "op_" + nodeId;
        this.operatorType = operatorType;
        this.physicalOp = operatorType;
        this.logicalOp = operatorType;
    }

    public void updateSeverity() {
        if (costPercent >= 30.0) {
            this.costSeverity = "high";
        } else if (costPercent >= 10.0) {
            this.costSeverity = "medium";
        } else {
            this.costSeverity = "low";
        }
    }

    public void addChild(PlanOperatorNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if (operatorType != null && !operatorType.isBlank()) {
            sb.append(operatorType);
        } else {
            sb.append("Operator");
        }
        if (relationName != null && !relationName.isBlank()) {
            sb.append(" (").append(relationName).append(")");
        }
        return sb.toString();
    }

    public String getIconLiteral() {
        String op = (operatorType != null ? operatorType : "").toLowerCase();
        if (op.contains("scan")) {
            return "fth-search";
        } else if (op.contains("seek")) {
            return "fth-crosshair";
        } else if (op.contains("join")) {
            return "fth-git-merge";
        } else if (op.contains("sort") || op.contains("order")) {
            return "fth-bar-chart-2";
        } else if (op.contains("aggregate") || op.contains("group") || op.contains("hash")) {
            return "fth-layers";
        } else if (op.contains("filter")) {
            return "fth-filter";
        } else if (op.contains("insert") || op.contains("update") || op.contains("delete")) {
            return "fth-edit-3";
        }
        return "fth-cpu";
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getOperatorType() { return operatorType; }
    public void setOperatorType(String operatorType) { this.operatorType = operatorType; }

    public String getPhysicalOp() { return physicalOp; }
    public void setPhysicalOp(String physicalOp) { this.physicalOp = physicalOp; }

    public String getLogicalOp() { return logicalOp; }
    public void setLogicalOp(String logicalOp) { this.logicalOp = logicalOp; }

    public String getRelationName() { return relationName; }
    public void setRelationName(String relationName) { this.relationName = relationName; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public double getEstimatedRows() { return estimatedRows; }
    public void setEstimatedRows(double estimatedRows) { this.estimatedRows = estimatedRows; }

    public Double getActualRows() { return actualRows; }
    public void setActualRows(Double actualRows) { this.actualRows = actualRows; }

    public double getSubtreeCost() { return subtreeCost; }
    public void setSubtreeCost(double subtreeCost) { this.subtreeCost = subtreeCost; }

    public double getOperatorCost() { return operatorCost; }
    public void setOperatorCost(double operatorCost) { this.operatorCost = operatorCost; }

    public double getCostPercent() { return costPercent; }
    public void setCostPercent(double costPercent) { 
        this.costPercent = costPercent;
        updateSeverity();
    }

    public String getCostSeverity() { return costSeverity; }
    public void setCostSeverity(String costSeverity) { this.costSeverity = costSeverity; }

    public Double getActualTimeMs() { return actualTimeMs; }
    public void setActualTimeMs(Double actualTimeMs) { this.actualTimeMs = actualTimeMs; }

    public Integer getActualLoops() { return actualLoops; }
    public void setActualLoops(Integer actualLoops) { this.actualLoops = actualLoops; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public List<String> getOutputColumns() { return outputColumns; }
    public void setOutputColumns(List<String> outputColumns) { this.outputColumns = outputColumns; }

    public List<PlanOperatorNode> getChildren() { return children; }
    public void setChildren(List<PlanOperatorNode> children) { this.children = children; }

    @Override
    public String toString() {
        return getDisplayName() + " [Cost: " + String.format("%.1f%%", costPercent) + "]";
    }
}

