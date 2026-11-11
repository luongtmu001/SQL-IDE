package com.lltool.core.domain.tabledesigner.model;

import com.lltool.core.domain.tabledesigner.valueobject.ForeignKeyAction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing a Foreign Key constraint definition.
 */
public class ForeignKeyDesign implements Serializable {

    private String name;
    private List<ForeignKeyColumnMapping> columnMappings = new ArrayList<>();
    private String targetSchema;
    private String targetTable;
    private ForeignKeyAction onDelete = ForeignKeyAction.NO_ACTION;
    private ForeignKeyAction onUpdate = ForeignKeyAction.NO_ACTION;

    public ForeignKeyDesign() {}

    public ForeignKeyDesign(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public ForeignKeyDesign(String name, String targetSchema, String targetTable,
                            List<ForeignKeyColumnMapping> columnMappings) {
        this.name = name != null ? name.trim() : "";
        this.targetSchema = targetSchema != null ? targetSchema.trim() : "";
        this.targetTable = targetTable != null ? targetTable.trim() : "";
        if (columnMappings != null) {
            this.columnMappings = new ArrayList<>(columnMappings);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public List<ForeignKeyColumnMapping> getColumnMappings() {
        return columnMappings;
    }

    public void setColumnMappings(List<ForeignKeyColumnMapping> columnMappings) {
        this.columnMappings = columnMappings != null ? new ArrayList<>(columnMappings) : new ArrayList<>();
    }

    public String getTargetSchema() {
        return targetSchema;
    }

    public void setTargetSchema(String targetSchema) {
        this.targetSchema = targetSchema != null ? targetSchema.trim() : "";
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable != null ? targetTable.trim() : "";
    }

    public ForeignKeyAction getOnDelete() {
        return onDelete;
    }

    public void setOnDelete(ForeignKeyAction onDelete) {
        this.onDelete = onDelete != null ? onDelete : ForeignKeyAction.NO_ACTION;
    }

    public ForeignKeyAction getOnUpdate() {
        return onUpdate;
    }

    public void setOnUpdate(ForeignKeyAction onUpdate) {
        this.onUpdate = onUpdate != null ? onUpdate : ForeignKeyAction.NO_ACTION;
    }

    public boolean containsSourceColumn(String columnName) {
        if (columnName == null) return false;
        for (ForeignKeyColumnMapping mapping : columnMappings) {
            if (columnName.equalsIgnoreCase(mapping.getSourceColumn())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForeignKeyDesign that = (ForeignKeyDesign) o;
        return Objects.equals(name != null ? name.toLowerCase() : null, that.name != null ? that.name.toLowerCase() : null) &&
                Objects.equals(columnMappings, that.columnMappings) &&
                Objects.equals(targetSchema != null ? targetSchema.toLowerCase() : null, that.targetSchema != null ? that.targetSchema.toLowerCase() : null) &&
                Objects.equals(targetTable != null ? targetTable.toLowerCase() : null, that.targetTable != null ? that.targetTable.toLowerCase() : null) &&
                onDelete == that.onDelete &&
                onUpdate == that.onUpdate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name != null ? name.toLowerCase() : null, columnMappings,
                targetSchema != null ? targetSchema.toLowerCase() : null,
                targetTable != null ? targetTable.toLowerCase() : null,
                onDelete, onUpdate);
    }
}

