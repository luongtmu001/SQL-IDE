package com.lltool.core.domain.tabledesigner.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Mapping between a source column and a referenced target column in a foreign key.
 */
public class ForeignKeyColumnMapping implements Serializable {

    private String sourceColumn;
    private String targetColumn;

    public ForeignKeyColumnMapping() {}

    public ForeignKeyColumnMapping(String sourceColumn, String targetColumn) {
        this.sourceColumn = sourceColumn != null ? sourceColumn.trim() : "";
        this.targetColumn = targetColumn != null ? targetColumn.trim() : "";
    }

    public String getSourceColumn() {
        return sourceColumn;
    }

    public void setSourceColumn(String sourceColumn) {
        this.sourceColumn = sourceColumn != null ? sourceColumn.trim() : "";
    }

    public String getTargetColumn() {
        return targetColumn;
    }

    public void setTargetColumn(String targetColumn) {
        this.targetColumn = targetColumn != null ? targetColumn.trim() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForeignKeyColumnMapping that = (ForeignKeyColumnMapping) o;
        return Objects.equals(sourceColumn != null ? sourceColumn.toLowerCase() : null,
                that.sourceColumn != null ? that.sourceColumn.toLowerCase() : null) &&
                Objects.equals(targetColumn != null ? targetColumn.toLowerCase() : null,
                        that.targetColumn != null ? that.targetColumn.toLowerCase() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceColumn != null ? sourceColumn.toLowerCase() : null,
                targetColumn != null ? targetColumn.toLowerCase() : null);
    }

    @Override
    public String toString() {
        return sourceColumn + " -> " + targetColumn;
    }
}

