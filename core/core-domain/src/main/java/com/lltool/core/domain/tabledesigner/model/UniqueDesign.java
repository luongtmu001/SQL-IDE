package com.lltool.core.domain.tabledesigner.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing a Unique Constraint.
 */
public class UniqueDesign implements Serializable {

    private String name;
    private List<String> columns = new ArrayList<>();
    private boolean clustered;

    public UniqueDesign() {}

    public UniqueDesign(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public UniqueDesign(String name, List<String> columns, boolean clustered) {
        this.name = name != null ? name.trim() : "";
        if (columns != null) {
            this.columns = new ArrayList<>(columns);
        }
        this.clustered = clustered;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>();
    }

    public boolean isClustered() {
        return clustered;
    }

    public void setClustered(boolean clustered) {
        this.clustered = clustered;
    }

    public boolean containsColumn(String columnName) {
        if (columnName == null) return false;
        for (String col : columns) {
            if (columnName.equalsIgnoreCase(col)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UniqueDesign that = (UniqueDesign) o;
        return clustered == that.clustered &&
                Objects.equals(name != null ? name.toLowerCase() : null, that.name != null ? that.name.toLowerCase() : null) &&
                Objects.equals(columns, that.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name != null ? name.toLowerCase() : null, columns, clustered);
    }
}

