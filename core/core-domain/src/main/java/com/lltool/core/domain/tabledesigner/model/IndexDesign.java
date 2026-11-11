package com.lltool.core.domain.tabledesigner.model;

import com.lltool.core.domain.tabledesigner.valueobject.IndexType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing an index definition on a table.
 */
public class IndexDesign implements Serializable {

    private String name;
    private List<IndexColumn> columns = new ArrayList<>();
    private IndexType indexType = IndexType.NONCLUSTERED;
    private boolean unique;
    private boolean clustered;
    private String filterPredicate;

    public IndexDesign() {}

    public IndexDesign(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public IndexDesign(String name, List<IndexColumn> columns, boolean unique) {
        this.name = name != null ? name.trim() : "";
        if (columns != null) {
            this.columns = new ArrayList<>(columns);
        }
        this.unique = unique;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public List<IndexColumn> getColumns() {
        return columns;
    }

    public void setColumns(List<IndexColumn> columns) {
        this.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>();
    }

    public IndexType getIndexType() {
        return indexType;
    }

    public void setIndexType(IndexType indexType) {
        this.indexType = indexType != null ? indexType : IndexType.NONCLUSTERED;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public boolean isClustered() {
        return clustered;
    }

    public void setClustered(boolean clustered) {
        this.clustered = clustered;
        if (clustered) {
            this.indexType = IndexType.CLUSTERED;
        }
    }

    public String getFilterPredicate() {
        return filterPredicate;
    }

    public void setFilterPredicate(String filterPredicate) {
        this.filterPredicate = filterPredicate != null && !filterPredicate.isBlank() ? filterPredicate.trim() : null;
    }

    public boolean containsColumn(String columnName) {
        if (columnName == null) return false;
        for (IndexColumn col : columns) {
            if (columnName.equalsIgnoreCase(col.getColumnName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexDesign that = (IndexDesign) o;
        return unique == that.unique &&
                clustered == that.clustered &&
                Objects.equals(name != null ? name.toLowerCase() : null, that.name != null ? that.name.toLowerCase() : null) &&
                Objects.equals(columns, that.columns) &&
                indexType == that.indexType &&
                Objects.equals(filterPredicate, that.filterPredicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name != null ? name.toLowerCase() : null, columns, indexType, unique, clustered, filterPredicate);
    }
}

