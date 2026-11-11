package com.lltool.core.domain.schemacompare.model;

import com.lltool.core.domain.tabledesigner.model.TableDesign;

import java.util.*;

/**
 * Snapshot metadata của một CSDL/Schema bao gồm Bảng, Views, Procedures, Functions, Triggers.
 */
public class SchemaSnapshot {

    private final String engine;
    private final String database;
    private final String schema;

    private final Map<String, TableDesign> tables = new LinkedHashMap<>();
    private final Map<String, String> views = new LinkedHashMap<>();
    private final Map<String, String> procedures = new LinkedHashMap<>();
    private final Map<String, String> functions = new LinkedHashMap<>();
    private final Map<String, String> triggers = new LinkedHashMap<>();
    private final Map<String, String> functionSignatures = new LinkedHashMap<>();
    private final Map<String, String> triggerParentTables = new LinkedHashMap<>();

    public SchemaSnapshot(String engine, String database, String schema) {
        this.engine = engine != null ? engine.toLowerCase() : "sqlserver";
        this.database = database != null ? database : "";
        this.schema = schema != null ? schema : "dbo";
    }

    public String getEngine() {
        return engine;
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    public Map<String, TableDesign> getTables() {
        return tables;
    }

    public Map<String, String> getViews() {
        return views;
    }

    public Map<String, String> getProcedures() {
        return procedures;
    }

    public Map<String, String> getFunctions() {
        return functions;
    }

    public Map<String, String> getTriggers() {
        return triggers;
    }

    public Map<String, String> getFunctionSignatures() {
        return functionSignatures;
    }

    public Map<String, String> getTriggerParentTables() {
        return triggerParentTables;
    }

    public void addTable(TableDesign table) {
        if (table != null && table.getTable() != null) {
            tables.put(table.getTable(), table);
        }
    }

    public void addView(String name, String ddl) {
        if (name != null) views.put(name, ddl != null ? ddl : "");
    }

    public void addProcedure(String name, String ddl) {
        if (name != null) procedures.put(name, ddl != null ? ddl : "");
    }

    public void addFunction(String name, String ddl) {
        addFunction(name, ddl, null);
    }

    public void addFunction(String name, String ddl, String identityArgs) {
        if (name != null) {
            String key = (identityArgs != null && !identityArgs.isBlank()) ? (name + "(" + identityArgs.trim() + ")") : name;
            functions.put(key, ddl != null ? ddl : "");
            if (identityArgs != null && !identityArgs.isBlank()) {
                functionSignatures.put(key, identityArgs.trim());
            }
        }
    }

    public void addTrigger(String name, String ddl) {
        addTrigger(name, ddl, null);
    }

    public void addTrigger(String name, String ddl, String parentTable) {
        if (name != null) {
            triggers.put(name, ddl != null ? ddl : "");
            if (parentTable != null && !parentTable.isBlank()) {
                triggerParentTables.put(name, parentTable.trim());
            }
        }
    }

    public int getTotalObjectCount() {
        return tables.size() + views.size() + procedures.size() + functions.size() + triggers.size();
    }
}
