package com.lltool.core.domain.visualizer;

import java.util.Objects;

/**
 * Represents a single stage or operator node in the SQL visual pipeline.
 */
public class QueryFlowNode {
    private final String id;
    private final QueryNodeType type;
    private String title;
    private String subtitle;
    private String details;
    private String sqlFragment;
    private SourceRange sourceRange;
    private int stageIndex;
    private int stepIndex;
    private String estimatedCost;
    private Long rowCount;

    // Visual layout coordinates
    private double x;
    private double y;

    public QueryFlowNode(String id, QueryNodeType type, String title, String subtitle) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.type = type != null ? type : QueryNodeType.UNKNOWN;
        this.title = title != null ? title : "";
        this.subtitle = subtitle != null ? subtitle : "";
        this.sourceRange = new SourceRange();
    }

    public String getId() {
        return id;
    }

    public QueryNodeType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getSqlFragment() {
        return sqlFragment;
    }

    public void setSqlFragment(String sqlFragment) {
        this.sqlFragment = sqlFragment;
    }

    public SourceRange getSourceRange() {
        return sourceRange;
    }

    public void setSourceRange(SourceRange sourceRange) {
        this.sourceRange = sourceRange != null ? sourceRange : new SourceRange();
    }

    public int getStageIndex() {
        return stageIndex;
    }

    public void setStageIndex(int stageIndex) {
        this.stageIndex = stageIndex;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(String estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryFlowNode that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "QueryFlowNode{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", title='" + title + '\'' +
                ", stageIndex=" + stageIndex +
                '}';
    }
}
