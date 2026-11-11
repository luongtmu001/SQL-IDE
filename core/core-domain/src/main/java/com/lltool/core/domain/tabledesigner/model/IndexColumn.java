package com.lltool.core.domain.tabledesigner.model;

import com.lltool.core.domain.tabledesigner.valueobject.ColumnId;

import java.io.Serializable;
import java.util.Objects;

/**
 * References a column participating in an index.
 */
public class IndexColumn implements Serializable {

    private ColumnId columnId;
    private String columnName;
    private boolean descending;

    public IndexColumn() {}

    public IndexColumn(String columnName) {
        this(null, columnName, false);
    }

    public IndexColumn(String columnName, boolean descending) {
        this(null, columnName, descending);
    }

    public IndexColumn(ColumnId columnId, String columnName, boolean descending) {
        this.columnId = columnId;
        this.columnName = columnName != null ? columnName.trim() : "";
        this.descending = descending;
    }

    public ColumnId getColumnId() {
        return columnId;
    }

    public void setColumnId(ColumnId columnId) {
        this.columnId = columnId;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName != null ? columnName.trim() : "";
    }

    public boolean isDescending() {
        return descending;
    }

    public void setDescending(boolean descending) {
        this.descending = descending;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexColumn that = (IndexColumn) o;
        return descending == that.descending &&
                Objects.equals(columnName != null ? columnName.toLowerCase() : null,
                        that.columnName != null ? that.columnName.toLowerCase() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName != null ? columnName.toLowerCase() : null, descending);
    }

    @Override
    public String toString() {
        return columnName + (descending ? " DESC" : " ASC");
    }
}

