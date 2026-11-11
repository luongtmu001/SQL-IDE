package com.lltool.core.domain.tableeditor.model;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A batch of row modifications pending application to the database.
 */
public class DataChangeBatch implements Serializable {

    private final String engine;
    private final String schema;
    private final String table;
    private final List<DataRowChange> changes = new ArrayList<>();

    public DataChangeBatch(String engine, String schema, String table) {
        this.engine = engine != null ? engine.toLowerCase() : "sqlserver";
        this.schema = schema != null ? schema : "dbo";
        this.table = table != null ? table : "";
    }

    public String getEngine() { return engine; }
    public String getSchema() { return schema; }
    public String getTable() { return table; }

    public List<DataRowChange> getChanges() { return Collections.unmodifiableList(changes); }

    public void addChange(DataRowChange change) {
        if (change != null) {
            changes.add(change);
        }
    }

    public List<DataRowChange> getInserts() {
        return changes.stream().filter(c -> c.getType() == DataRowChange.Type.INSERT).collect(Collectors.toList());
    }

    public List<DataRowChange> getUpdates() {
        return changes.stream().filter(c -> c.getType() == DataRowChange.Type.UPDATE).collect(Collectors.toList());
    }

    public List<DataRowChange> getDeletes() {
        return changes.stream().filter(c -> c.getType() == DataRowChange.Type.DELETE).collect(Collectors.toList());
    }

    public int size() { return changes.size(); }
    public boolean isEmpty() { return changes.isEmpty(); }
}

