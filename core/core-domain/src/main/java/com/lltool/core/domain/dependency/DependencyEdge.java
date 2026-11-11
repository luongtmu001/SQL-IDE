package com.lltool.core.domain.dependency;

import java.util.Objects;

/**
 * Represents a directed connection between two database objects.
 */
public class DependencyEdge {
    private final String id;
    private final String sourceId;
    private final String targetId;
    private final RelationType relationType;
    private final DependencyType dependencyType;
    private final Confidence confidence;
    private final String label;

    public DependencyEdge(String sourceId, String targetId, RelationType relationType,
                          DependencyType dependencyType, Confidence confidence, String label) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId must not be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
        this.id = sourceId + "->" + targetId + "[" + (relationType != null ? relationType.name() : "DEP") + "]";
        this.relationType = relationType != null ? relationType : RelationType.DEPENDS_ON;
        this.dependencyType = dependencyType != null ? dependencyType : DependencyType.DIRECT;
        this.confidence = confidence != null ? confidence : Confidence.CONFIRMED;
        this.label = label != null ? label : "";
    }

    public static DependencyEdge of(String sourceId, String targetId, RelationType relationType, String label) {
        return new DependencyEdge(sourceId, targetId, relationType, DependencyType.DIRECT, Confidence.CONFIRMED, label);
    }

    public String getId() {
        return id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public DependencyType getDependencyType() {
        return dependencyType;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DependencyEdge that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DependencyEdge{" +
                "sourceId='" + sourceId + '\'' +
                ", targetId='" + targetId + '\'' +
                ", relationType=" + relationType +
                ", label='" + label + '\'' +
                '}';
    }
}
