package com.lltool.core.domain.dependency;

import java.util.Objects;

/**
 * Represents a single database object node in the dependency graph.
 */
public class DependencyNode {
    private final String id;
    private final String schema;
    private final String name;
    private final String columnName;
    private final DependencyNodeType type;
    private final boolean root;
    private Long rowCount;
    private String description;
    private String definitionSql;

    // Visual layout coordinates
    private double x;
    private double y;
    private int layer;

    public DependencyNode(String id, String schema, String name, String columnName, DependencyNodeType type, boolean root) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.schema = schema != null ? schema : "dbo";
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.columnName = columnName;
        this.type = type != null ? type : DependencyNodeType.TABLE;
        this.root = root;
    }

    public static DependencyNode of(String schema, String name, DependencyNodeType type, boolean root) {
        String safeSchema = schema != null && !schema.isBlank() ? schema : "dbo";
        String id = safeSchema + "." + name;
        return new DependencyNode(id, safeSchema, name, null, type, root);
    }

    public static DependencyNode ofColumn(String schema, String tableName, String columnName, boolean root) {
        String safeSchema = schema != null && !schema.isBlank() ? schema : "dbo";
        String id = safeSchema + "." + tableName + "." + columnName;
        return new DependencyNode(id, safeSchema, tableName, columnName, DependencyNodeType.COLUMN, root);
    }

    public String getId() {
        return id;
    }

    public String getSchema() {
        return schema;
    }

    public String getName() {
        return name;
    }

    public String getColumnName() {
        return columnName;
    }

    public boolean isColumn() {
        return columnName != null && !columnName.isBlank();
    }

    public String getDisplayName() {
        if (isColumn()) {
            return schema + "." + name + " (" + columnName + ")";
        }
        return (schema != null && !schema.isBlank() ? schema + "." : "") + name;
    }

    public DependencyNodeType getType() {
        return type;
    }

    public boolean isRoot() {
        return root;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefinitionSql() {
        return definitionSql;
    }

    public void setDefinitionSql(String definitionSql) {
        this.definitionSql = definitionSql;
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

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DependencyNode that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DependencyNode{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", root=" + root +
                ", layer=" + layer +
                '}';
    }
}
