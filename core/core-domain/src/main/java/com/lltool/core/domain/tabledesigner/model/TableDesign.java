package com.lltool.core.domain.tabledesigner.model;

import com.lltool.core.domain.tabledesigner.valueobject.ColumnId;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Root aggregate representing the full schema design of a database table.
 */
public class TableDesign implements Serializable, Cloneable {

    private String engine = "sqlserver";
    private String database;
    private String schema = "dbo";
    private String table;
    private String originalTable;
    private String comment;

    private List<ColumnDesign> columns = new ArrayList<>();
    private List<IndexDesign> indexes = new ArrayList<>();
    private List<ForeignKeyDesign> foreignKeys = new ArrayList<>();
    private List<UniqueDesign> uniques = new ArrayList<>();
    private List<CheckDesign> checks = new ArrayList<>();
    private List<TriggerDesign> triggers = new ArrayList<>();

    public TableDesign() {}

    public TableDesign(String engine, String schema, String table) {
        this.engine = engine != null ? engine.trim().toLowerCase() : "sqlserver";
        this.schema = schema != null ? schema.trim() : ("postgresql".equalsIgnoreCase(this.engine) ? "public" : "dbo");
        this.table = table != null ? table.trim() : "";
    }

    public static TableDesign newTable(String engine, String schema, String tableName) {
        TableDesign design = new TableDesign(engine, schema, tableName);
        if ("postgresql".equalsIgnoreCase(engine)) {
            ColumnDesign pk = ColumnDesign.of("id", "serial");
            pk.setPrimaryKey(true);
            design.addColumn(pk);
        } else {
            ColumnDesign pk = ColumnDesign.of("Id", "int");
            pk.setPrimaryKey(true);
            pk.setIdentity(true);
            pk.setIdentitySeed(1L);
            pk.setIdentityIncrement(1L);
            design.addColumn(pk);
        }
        return design;
    }

    public TableDesign deepCopy() {
        TableDesign copy = new TableDesign(this.engine, this.schema, this.table);
        copy.setDatabase(this.database);
        copy.setOriginalTable(this.originalTable);
        copy.setComment(this.comment);

        for (ColumnDesign col : this.columns) {
            ColumnDesign colCopy = new ColumnDesign(col.getId(), col.getName(), col.getDataType());
            colCopy.setOriginalName(col.getOriginalName());
            colCopy.setLength(col.getLength());
            colCopy.setScale(col.getScale());
            colCopy.setNotNull(col.isNotNull());
            colCopy.setPrimaryKey(col.isPrimaryKey());
            colCopy.setDefaultValue(col.getDefaultValue());
            colCopy.setDefaultIsExpression(col.isDefaultIsExpression());
            colCopy.setDefaultConstraintName(col.getDefaultConstraintName());
            colCopy.setCollation(col.getCollation());
            colCopy.setIdentity(col.isIdentity());
            colCopy.setIdentitySeed(col.getIdentitySeed());
            colCopy.setIdentityIncrement(col.getIdentityIncrement());
            colCopy.setSequenceName(col.getSequenceName());
            colCopy.setComputedFormula(col.getComputedFormula());
            colCopy.setComputedPersisted(col.isComputedPersisted());
            colCopy.setComment(col.getComment());
            copy.columns.add(colCopy);
        }

        for (IndexDesign idx : this.indexes) {
            IndexDesign idxCopy = new IndexDesign(idx.getName());
            idxCopy.setIndexType(idx.getIndexType());
            idxCopy.setUnique(idx.isUnique());
            idxCopy.setClustered(idx.isClustered());
            idxCopy.setFilterPredicate(idx.getFilterPredicate());
            for (IndexColumn ic : idx.getColumns()) {
                idxCopy.getColumns().add(new IndexColumn(ic.getColumnId(), ic.getColumnName(), ic.isDescending()));
            }
            copy.indexes.add(idxCopy);
        }

        for (ForeignKeyDesign fk : this.foreignKeys) {
            ForeignKeyDesign fkCopy = new ForeignKeyDesign(fk.getName(), fk.getTargetSchema(), fk.getTargetTable(), fk.getColumnMappings());
            fkCopy.setOnDelete(fk.getOnDelete());
            fkCopy.setOnUpdate(fk.getOnUpdate());
            copy.foreignKeys.add(fkCopy);
        }

        for (UniqueDesign uq : this.uniques) {
            copy.uniques.add(new UniqueDesign(uq.getName(), uq.getColumns(), uq.isClustered()));
        }

        for (CheckDesign ck : this.checks) {
            copy.checks.add(new CheckDesign(ck.getName(), ck.getCheckClause()));
        }

        for (TriggerDesign tr : this.triggers) {
            TriggerDesign trCopy = new TriggerDesign(tr.getName(), tr.getDefinition());
            trCopy.setTiming(tr.getTiming());
            trCopy.setEvents(tr.getEvents());
            copy.triggers.add(trCopy);
        }

        return copy;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine != null ? engine.trim().toLowerCase() : "sqlserver";
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database != null ? database.trim() : null;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema != null ? schema.trim() : ("postgresql".equalsIgnoreCase(this.engine) ? "public" : "dbo");
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table != null ? table.trim() : "";
    }

    public String getOriginalTable() {
        return originalTable;
    }

    public void setOriginalTable(String originalTable) {
        this.originalTable = originalTable != null ? originalTable.trim() : null;
    }

    public boolean isTableRenamed() {
        return originalTable != null && !originalTable.equalsIgnoreCase(table);
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment != null && !comment.isBlank() ? comment.trim() : null;
    }

    public List<ColumnDesign> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnDesign> columns) {
        this.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>();
    }

    public void addColumn(ColumnDesign column) {
        if (column != null) {
            this.columns.add(column);
        }
    }

    public void addIndex(IndexDesign index) {
        if (index != null) {
            this.indexes.add(index);
        }
    }

    public void addForeignKey(ForeignKeyDesign foreignKey) {
        if (foreignKey != null) {
            this.foreignKeys.add(foreignKey);
        }
    }

    public void addUnique(UniqueDesign unique) {
        if (unique != null) {
            this.uniques.add(unique);
        }
    }

    public void addCheck(CheckDesign check) {
        if (check != null) {
            this.checks.add(check);
        }
    }

    public void addTrigger(TriggerDesign trigger) {
        if (trigger != null) {
            this.triggers.add(trigger);
        }
    }

    public List<IndexDesign> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexDesign> indexes) {
        this.indexes = indexes != null ? new ArrayList<>(indexes) : new ArrayList<>();
    }

    public List<ForeignKeyDesign> getForeignKeys() {
        return foreignKeys;
    }

    public void setForeignKeys(List<ForeignKeyDesign> foreignKeys) {
        this.foreignKeys = foreignKeys != null ? new ArrayList<>(foreignKeys) : new ArrayList<>();
    }

    public List<UniqueDesign> getUniques() {
        return uniques;
    }

    public void setUniques(List<UniqueDesign> uniques) {
        this.uniques = uniques != null ? new ArrayList<>(uniques) : new ArrayList<>();
    }

    public List<CheckDesign> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckDesign> checks) {
        this.checks = checks != null ? new ArrayList<>(checks) : new ArrayList<>();
    }

    public List<TriggerDesign> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<TriggerDesign> triggers) {
        this.triggers = triggers != null ? new ArrayList<>(triggers) : new ArrayList<>();
    }

    public ColumnDesign findColumnById(ColumnId id) {
        if (id == null) return null;
        for (ColumnDesign col : columns) {
            if (id.equals(col.getId())) {
                return col;
            }
        }
        return null;
    }

    public ColumnDesign findColumnByName(String name) {
        if (name == null) return null;
        for (ColumnDesign col : columns) {
            if (name.equalsIgnoreCase(col.getName())) {
                return col;
            }
        }
        return null;
    }

    public ColumnDesign findColumnByOriginalName(String originalName) {
        if (originalName == null) return null;
        for (ColumnDesign col : columns) {
            if (originalName.equalsIgnoreCase(col.getOriginalName())) {
                return col;
            }
        }
        return null;
    }

    public List<ColumnDesign> getPrimaryKeyColumns() {
        return columns.stream().filter(ColumnDesign::isPrimaryKey).collect(Collectors.toList());
    }

    public List<String> getPrimaryKeyColumnNames() {
        return columns.stream()
                .filter(ColumnDesign::isPrimaryKey)
                .map(ColumnDesign::getName)
                .collect(Collectors.toList());
    }

    /**
     * Truncates and appends suffix so that identifier does not exceed database maximum length.
     * PostgreSQL: 63 characters.
     * SQL Server: 128 characters.
     */
    public static String makeSafeIdentifier(String baseName, String suffix, String engine) {
        int maxLen = ("postgresql".equalsIgnoreCase(engine) || "postgres".equalsIgnoreCase(engine)) ? 63 : 128;
        String s = suffix != null ? suffix : "";
        String base = (baseName != null && !baseName.isBlank()) ? baseName.trim() : "Obj";
        String combined = base + s;
        if (combined.length() <= maxLen) {
            return combined;
        }
        int keep = Math.max(1, maxLen - s.length());
        return base.substring(0, Math.min(base.length(), keep)) + s;
    }

    /**
     * Clones this TableDesign for a Copy Table / Table Duplication workflow (SCR-TD-001 / TD5).
     * Fixes Bug B1 (new ColumnId, originalName = null) and Bug B10 (safe identifier length, trigger regex replacement).
     */
    public TableDesign cloneForCopy(String newSchema, String newTableName, CopyTableOptions options) {
        CopyTableOptions opt = options != null ? options : CopyTableOptions.defaults();
        String safeTableName = makeSafeIdentifier(newTableName, "", this.engine);
        String targetSchema = (newSchema != null && !newSchema.isBlank()) ? newSchema.trim() : this.schema;

        TableDesign copy = new TableDesign(this.engine, targetSchema, safeTableName);
        copy.setDatabase(this.database);
        copy.setOriginalTable(null); // Copy Table is always a brand new table
        copy.setComment(this.comment);

        Map<ColumnId, ColumnId> columnIdMap = new HashMap<>();
        for (ColumnDesign col : this.columns) {
            ColumnId newId = ColumnId.generate();
            columnIdMap.put(col.getId(), newId);
            ColumnDesign colCopy = new ColumnDesign(newId, col.getName(), col.getDataType());
            colCopy.setOriginalName(null); // Fix B1: brand new column
            colCopy.setLength(col.getLength());
            colCopy.setScale(col.getScale());
            colCopy.setNotNull(col.isNotNull());

            if (opt.copyPrimaryKey()) {
                colCopy.setPrimaryKey(col.isPrimaryKey());
                colCopy.setIdentity(col.isIdentity());
                colCopy.setIdentitySeed(col.getIdentitySeed());
                colCopy.setIdentityIncrement(col.getIdentityIncrement());
            } else {
                colCopy.setPrimaryKey(false);
                colCopy.setIdentity(false);
            }

            colCopy.setDefaultValue(col.getDefaultValue());
            colCopy.setDefaultIsExpression(col.isDefaultIsExpression());
            if (col.getDefaultConstraintName() != null && !col.getDefaultConstraintName().isBlank()) {
                colCopy.setDefaultConstraintName(makeSafeIdentifier(col.getDefaultConstraintName(), "_Copy", this.engine));
            }
            colCopy.setCollation(col.getCollation());
            colCopy.setSequenceName(col.getSequenceName());
            colCopy.setComputedFormula(col.getComputedFormula());
            colCopy.setComputedPersisted(col.isComputedPersisted());
            colCopy.setComment(col.getComment());
            copy.columns.add(colCopy);
        }

        if (opt.copyIndexes()) {
            for (IndexDesign idx : this.indexes) {
                String safeIdxName = makeSafeIdentifier(idx.getName(), "_Copy", this.engine);
                IndexDesign idxCopy = new IndexDesign(safeIdxName);
                idxCopy.setIndexType(idx.getIndexType());
                idxCopy.setUnique(idx.isUnique());
                idxCopy.setClustered(idx.isClustered());
                idxCopy.setFilterPredicate(idx.getFilterPredicate());
                for (IndexColumn ic : idx.getColumns()) {
                    ColumnId newColId = columnIdMap.getOrDefault(ic.getColumnId(), ic.getColumnId());
                    idxCopy.getColumns().add(new IndexColumn(newColId, ic.getColumnName(), ic.isDescending()));
                }
                copy.indexes.add(idxCopy);
            }
        }

        if (opt.copyForeignKeys()) {
            for (ForeignKeyDesign fk : this.foreignKeys) {
                String safeFkName = makeSafeIdentifier(fk.getName(), "_Copy", this.engine);
                ForeignKeyDesign fkCopy = new ForeignKeyDesign(safeFkName, fk.getTargetSchema(), fk.getTargetTable(), null);
                for (ForeignKeyColumnMapping m : fk.getColumnMappings()) {
                    fkCopy.getColumnMappings().add(new ForeignKeyColumnMapping(m.getSourceColumn(), m.getTargetColumn()));
                }
                fkCopy.setOnDelete(fk.getOnDelete());
                fkCopy.setOnUpdate(fk.getOnUpdate());
                copy.foreignKeys.add(fkCopy);
            }
        }

        if (opt.copyConstraints()) {
            for (UniqueDesign uq : this.uniques) {
                String safeUqName = makeSafeIdentifier(uq.getName(), "_Copy", this.engine);
                copy.uniques.add(new UniqueDesign(safeUqName, uq.getColumns(), uq.isClustered()));
            }
            for (CheckDesign ck : this.checks) {
                String safeCkName = makeSafeIdentifier(ck.getName(), "_Copy", this.engine);
                copy.checks.add(new CheckDesign(safeCkName, ck.getCheckClause()));
            }
        }

        if (opt.copyTriggers()) {
            for (TriggerDesign tr : this.triggers) {
                String origTrigName = tr.getName();
                String safeTrigName = makeSafeIdentifier(origTrigName, "_Copy", this.engine);
                String def = tr.getDefinition();
                if (def != null && !def.isBlank()) {
                    // Fix B10: Regex word-boundary replacement for table and trigger names
                    if (this.table != null && !this.table.isBlank()) {
                        String escOldTable = Pattern.quote(this.table);
                        String tableRegex = "(?<=[\\s\\[\\\"`.,]|^)" + escOldTable + "(?=[\\s\\]\\\"`.,;]|$)";
                        def = def.replaceAll(tableRegex, safeTableName);
                    }
                    if (origTrigName != null && !origTrigName.isBlank()) {
                        String escOldTrig = Pattern.quote(origTrigName);
                        String trigRegex = "(?<=[\\s\\[\\\"`.,]|^)" + escOldTrig + "(?=[\\s\\]\\\"`.,;]|$)";
                        def = def.replaceAll(trigRegex, safeTrigName);
                    }
                }
                TriggerDesign trCopy = new TriggerDesign(safeTrigName, def);
                trCopy.setTiming(tr.getTiming());
                trCopy.setEvents(tr.getEvents());
                copy.triggers.add(trCopy);
            }
        }

        return copy;
    }
}

