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
    private final ObjectProperty<RowState> stateProperty = new SimpleObjectProperty<>(RowState.UNCHANGED);
    private final Map<String, Object> originalValues = new LinkedHashMap<>();
    private final Map<String, ObjectProperty<Object>> currentProperties = new LinkedHashMap<>();
    private final Set<String> dirtyColumns = new HashSet<>();

    public ObservableDataRow(int rowId, Map<String, Object> initialValues, boolean isNewRow) {
        this.rowId = rowId;
        setState(isNewRow ? RowState.INSERTED : RowState.UNCHANGED);

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
    public RowState getState() { return stateProperty.get(); }
    public ObjectProperty<RowState> stateProperty() { return stateProperty; }

    private void setState(RowState newState) {
        stateProperty.set(newState);
    }

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

        RowState state = getState();
        if (state == RowState.INSERTED) {
            dirtyColumns.add(colName);
        } else if (state != RowState.DELETED) {
            if (!areValuesEqual(oldVal, newVal)) {
                dirtyColumns.add(colName);
                setState(RowState.MODIFIED);
            } else {
                dirtyColumns.remove(colName);
                if (dirtyColumns.isEmpty()) {
                    setState(RowState.UNCHANGED);
                }
            }
        }
    }

    public static boolean areValuesEqual(Object oldVal, Object newVal) {
        if (oldVal == null && newVal == null) return true;
        if (oldVal == null || newVal == null) return false;
        if (oldVal instanceof java.math.BigDecimal bd1 && newVal instanceof java.math.BigDecimal bd2) {
            return bd1.compareTo(bd2) == 0;
        }
        if (oldVal instanceof Number n1 && newVal instanceof Number n2) {
            if (n1.getClass().equals(n2.getClass())) {
                return n1.equals(n2);
            }
            return Double.compare(n1.doubleValue(), n2.doubleValue()) == 0;
        }
        return Objects.equals(oldVal, newVal);
    }

    public boolean isCellDirty(String colName) {
        return dirtyColumns.contains(colName);
    }

    public Set<String> getDirtyColumns() {
        return Collections.unmodifiableSet(dirtyColumns);
    }

    public void markDeleted() {
        if (getState() == RowState.INSERTED) {
            // New row marked deleted -> handled by removing from list
        } else {
            setState(RowState.DELETED);
        }
    }

    public void unmarkDeleted() {
        if (getState() == RowState.DELETED) {
            setState(dirtyColumns.isEmpty() ? RowState.UNCHANGED : RowState.MODIFIED);
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
        setState(RowState.UNCHANGED);
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

