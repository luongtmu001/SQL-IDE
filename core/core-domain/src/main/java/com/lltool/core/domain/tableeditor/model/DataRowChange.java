package com.lltool.core.domain.tableeditor.model;

import java.io.Serializable;
import java.util.*;

/**
 * Represents a single row mutation (Insert, Update, or Delete) in Table Data Editor.
 */
public class DataRowChange implements Serializable {

    public enum Type {
        INSERT,
        UPDATE,
        DELETE
    }

    private final Type type;
    private final int rowId;
    private final Map<String, Object> pkValues = new LinkedHashMap<>();
    private final Map<String, Object> oldValues = new LinkedHashMap<>();
    private final Map<String, Object> newValues = new LinkedHashMap<>();

    public DataRowChange(Type type, int rowId) {
        this.type = type;
        this.rowId = rowId;
    }

    public Type getType() { return type; }
    public int getRowId() { return rowId; }

    public Map<String, Object> getPkValues() { return pkValues; }
    public void setPkValues(Map<String, Object> pks) {
        this.pkValues.clear();
        if (pks != null) this.pkValues.putAll(pks);
    }

    public Map<String, Object> getOldValues() { return oldValues; }
    public void setOldValues(Map<String, Object> olds) {
        this.oldValues.clear();
        if (olds != null) this.oldValues.putAll(olds);
    }

    public Map<String, Object> getNewValues() { return newValues; }
    public void setNewValues(Map<String, Object> news) {
        this.newValues.clear();
        if (news != null) this.newValues.putAll(news);
    }
}

