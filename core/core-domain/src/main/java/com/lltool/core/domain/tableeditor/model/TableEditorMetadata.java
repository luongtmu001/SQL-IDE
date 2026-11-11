package com.lltool.core.domain.tableeditor.model;

import java.io.Serializable;
import java.util.*;

/**
 * Domain model representing metadata of a table needed for inline data editing.
 */
public class TableEditorMetadata implements Serializable {

    public record ColumnMeta(
            String name,
            String dataType,
            boolean primaryKey,
            boolean identity,
            boolean computed,
            boolean nullable,
            String defaultValue,
            int sqlType,
            int precision,
            int scale
    ) implements Serializable {
        public ColumnMeta(String name, String dataType, boolean primaryKey, boolean identity, boolean computed, boolean nullable, String defaultValue) {
            this(name, dataType, primaryKey, identity, computed, nullable, defaultValue, java.sql.Types.OTHER, 0, 0);
        }

        public ColumnMeta(String name, String dataType, boolean primaryKey, boolean identity, boolean computed, boolean nullable, String defaultValue, int sqlType) {
            this(name, dataType, primaryKey, identity, computed, nullable, defaultValue, sqlType, 0, 0);
        }
    }

    private String engine = "sqlserver";
    private String database;
    private String schema = "dbo";
    private String table;
    private List<ColumnMeta> columns = new ArrayList<>();

    public TableEditorMetadata() {}

    public TableEditorMetadata(String engine, String database, String schema, String table, List<ColumnMeta> columns) {
        this.engine = engine != null ? engine.toLowerCase() : "sqlserver";
        this.database = database;
        this.schema = schema != null ? schema : ("postgresql".equalsIgnoreCase(this.engine) ? "public" : "dbo");
        this.table = table != null ? table : "";
        if (columns != null) {
            this.columns = new ArrayList<>(columns);
        }
    }

    public static TableEditorMetadata fromTableDesign(com.lltool.core.domain.tabledesigner.model.TableDesign design) {
        if (design == null) return new TableEditorMetadata();
        List<ColumnMeta> cols = new ArrayList<>();
        for (com.lltool.core.domain.tabledesigner.model.ColumnDesign cd : design.getColumns()) {
            cols.add(new ColumnMeta(
                    cd.getName(),
                    cd.getDataType(),
                    cd.isPrimaryKey(),
                    cd.isIdentity(),
                    cd.isComputed(),
                    !cd.isNotNull(),
                    cd.getDefaultValue()
            ));
        }
        return new TableEditorMetadata(
                design.getEngine(),
                design.getDatabase(),
                design.getSchema(),
                design.getTable(),
                cols
        );
    }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine != null ? engine.toLowerCase() : "sqlserver"; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }

    public List<ColumnMeta> getColumns() { return columns; }
    public void setColumns(List<ColumnMeta> columns) { this.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>(); }

    public List<String> getPrimaryKeyNames() {
        List<String> pks = new ArrayList<>();
        for (ColumnMeta c : columns) {
            if (c.primaryKey()) {
                pks.add(c.name());
            }
        }
        return pks;
    }

    public Set<String> getIdentityNames() {
        Set<String> idents = new HashSet<>();
        for (ColumnMeta c : columns) {
            if (c.identity()) {
                idents.add(c.name().toLowerCase());
            }
        }
        return idents;
    }

    public Set<String> getComputedNames() {
        Set<String> comps = new HashSet<>();
        for (ColumnMeta c : columns) {
            if (c.computed()) {
                comps.add(c.name().toLowerCase());
            }
        }
        return comps;
    }

    public Optional<ColumnMeta> findColumn(String name) {
        if (name == null) return Optional.empty();
        for (ColumnMeta c : columns) {
            if (c.name().equalsIgnoreCase(name)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
