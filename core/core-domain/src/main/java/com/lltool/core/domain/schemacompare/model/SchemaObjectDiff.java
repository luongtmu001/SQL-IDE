package com.lltool.core.domain.schemacompare.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Biểu diễn kết quả so sánh của một đối tượng CSDL (bảng, view, procedure, function, trigger).
 */
public class SchemaObjectDiff {

    private final String name;
    private final String schema;
    private final SchemaObjectType objectType;
    private final DiffStatus status;
    private final DiffAction action;
    private final boolean destructive;

    private final String sourceDefinition;
    private final String targetDefinition;
    private final String migrationSql;

    private final List<String> statements;
    private final List<ColumnDiff> columnDiffs;
    private final int sourceColumnCount;
    private final int targetColumnCount;

    private boolean selected;

    public SchemaObjectDiff(
            String name,
            String schema,
            SchemaObjectType objectType,
            DiffStatus status,
            DiffAction action,
            boolean destructive,
            String sourceDefinition,
            String targetDefinition,
            String migrationSql,
            List<String> statements,
            List<ColumnDiff> columnDiffs,
            int sourceColumnCount,
            int targetColumnCount) {
        this.name = name != null ? name : "";
        this.schema = schema != null ? schema : "dbo";
        this.objectType = objectType != null ? objectType : SchemaObjectType.TABLE;
        this.status = status != null ? status : DiffStatus.IDENTICAL;
        this.action = action != null ? action : DiffAction.NONE;
        this.destructive = destructive;
        this.sourceDefinition = sourceDefinition != null ? sourceDefinition : "";
        this.targetDefinition = targetDefinition != null ? targetDefinition : "";
        this.migrationSql = migrationSql != null ? migrationSql : "";
        this.statements = statements != null ? new ArrayList<>(statements) : Collections.emptyList();
        this.columnDiffs = columnDiffs != null ? new ArrayList<>(columnDiffs) : Collections.emptyList();
        this.sourceColumnCount = sourceColumnCount;
        this.targetColumnCount = targetColumnCount;
        // Default selected if different/missing and NOT destructive (SC-9)
        this.selected = (status != DiffStatus.IDENTICAL) && !this.destructive;
    }

    public String getName() {
        return name;
    }

    public String getSchema() {
        return schema;
    }

    public String getQualifiedName() {
        return schema + "." + name;
    }

    public SchemaObjectType getObjectType() {
        return objectType;
    }

    public DiffStatus getStatus() {
        return status;
    }

    public DiffAction getAction() {
        return action;
    }

    public boolean isDestructive() {
        return destructive;
    }

    public String getSourceDefinition() {
        return sourceDefinition;
    }

    public String getTargetDefinition() {
        return targetDefinition;
    }

    public String getMigrationSql() {
        return migrationSql;
    }

    public List<String> getStatements() {
        return Collections.unmodifiableList(statements);
    }

    public List<ColumnDiff> getColumnDiffs() {
        return Collections.unmodifiableList(columnDiffs);
    }

    public int getSourceColumnCount() {
        return sourceColumnCount;
    }

    public int getTargetColumnCount() {
        return targetColumnCount;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}

