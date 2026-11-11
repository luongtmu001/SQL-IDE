package app.ui.dataeditor;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

import java.util.*;

/**
 * Observable view-model for an editable data row in Table Data Editor.
 */
public class ObservableDataRow {

    public enum RowState {
        UNCHANGED,
        INSERTED,
        MODIFIED,
        DELETED
    }

    private final int rowId;
    private RowState state = RowState.UNCHANGED;
    private final Map<String, Object> originalValues = new LinkedHashMap<>();
    private final Map<String, ObjectProperty<Object>> currentProperties = new LinkedHashMap<>();
    private final Set<String> dirtyColumns = new HashSet<>();

    public ObservableDataRow(int rowId, Map<String, Object> initialValues, boolean isNewRow) {
        this.rowId = rowId;
        this.state = isNewRow ? RowState.INSERTED : RowState.UNCHANGED;

        if (initialValues != null) {
            for (Map.Entry<String, Object> e : initialValues.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();
                if (!isNewRow) {
                    originalValues.put(key, val);
                }
                currentProperties.put(key, new SimpleObjectProperty<>(val));
            }
        }
    }

    public int getRowId() { return rowId; }
    public RowState getState() { return state; }

    public ObservableValue<Object> getProperty(String colName) {
        return currentProperties.computeIfAbsent(colName, k -> new SimpleObjectProperty<>(null));
    }

    public Object getValue(String colName) {
        ObjectProperty<Object> prop = currentProperties.get(colName);
        return prop != null ? prop.get() : null;
    }

    public void setValue(String colName, Object newVal) {
        ObjectProperty<Object> prop = currentProperties.computeIfAbsent(colName, k -> new SimpleObjectProperty<>(null));
        Object oldVal = originalValues.get(colName);

        prop.set(newVal);

        if (state == RowState.INSERTED) {
            dirtyColumns.add(colName);
        } else if (state != RowState.DELETED) {
            if (!Objects.equals(oldVal, newVal)) {
                dirtyColumns.add(colName);
                state = RowState.MODIFIED;
            } else {
                dirtyColumns.remove(colName);
                if (dirtyColumns.isEmpty()) {
                    state = RowState.UNCHANGED;
                }
            }
        }
    }

    public boolean isCellDirty(String colName) {
        return dirtyColumns.contains(colName);
    }

    public Set<String> getDirtyColumns() {
        return Collections.unmodifiableSet(dirtyColumns);
    }

    public void markDeleted() {
        if (state == RowState.INSERTED) {
            // New row marked deleted -> handled by removing from list
        } else {
            state = RowState.DELETED;
        }
    }

    public void unmarkDeleted() {
        if (state == RowState.DELETED) {
            state = dirtyColumns.isEmpty() ? RowState.UNCHANGED : RowState.MODIFIED;
        }
    }

    public void revert() {
        for (Map.Entry<String, Object> entry : originalValues.entrySet()) {
            ObjectProperty<Object> prop = currentProperties.get(entry.getKey());
            if (prop != null) {
                prop.set(entry.getValue());
            }
        }
        dirtyColumns.clear();
        state = RowState.UNCHANGED;
    }

    public Map<String, Object> getOriginalValues() {
        return Collections.unmodifiableMap(originalValues);
    }

    public Map<String, Object> getCurrentValues() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, ObjectProperty<Object>> e : currentProperties.entrySet()) {
            map.put(e.getKey(), e.getValue().get());
        }
        return map;
    }
}

