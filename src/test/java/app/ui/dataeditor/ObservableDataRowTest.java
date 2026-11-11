package app.ui.dataeditor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObservableDataRowTest {

    @Test
    @DisplayName("Should initialize unchanged row with initial values")
    void testInitialStateUnchanged() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("id", 1);
        initial.put("name", "Alice");
        initial.put("active", true);

        ObservableDataRow row = new ObservableDataRow(100, initial, false);

        assertEquals(100, row.getRowId());
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());
        assertEquals(1, row.getValue("id"));
        assertEquals("Alice", row.getValue("name"));
        assertEquals(true, row.getValue("active"));
        assertFalse(row.isCellDirty("name"));
        assertTrue(row.getDirtyColumns().isEmpty());
    }

    @Test
    @DisplayName("Should track modified columns and update row state")
    void testModifyAndRevertState() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("id", 1);
        initial.put("name", "Alice");

        ObservableDataRow row = new ObservableDataRow(100, initial, false);

        // Edit name
        row.setValue("name", "Bob");
        assertEquals(ObservableDataRow.RowState.MODIFIED, row.getState());
        assertTrue(row.isCellDirty("name"));
        assertFalse(row.isCellDirty("id"));
        assertEquals("Bob", row.getValue("name"));
        assertEquals(1, row.getDirtyColumns().size());

        // Edit name back to original value -> should return to UNCHANGED
        row.setValue("name", "Alice");
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());
        assertFalse(row.isCellDirty("name"));
        assertTrue(row.getDirtyColumns().isEmpty());

        // Modify again and revert
        row.setValue("name", "Charlie");
        assertEquals(ObservableDataRow.RowState.MODIFIED, row.getState());
        row.revert();
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());
        assertEquals("Alice", row.getValue("name"));
        assertFalse(row.isCellDirty("name"));
    }

    @Test
    @DisplayName("Should handle INSERTED state for newly added rows")
    void testInsertedState() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("id", 999);
        initial.put("name", "New Employee");

        ObservableDataRow row = new ObservableDataRow(-1, initial, true);

        assertEquals(ObservableDataRow.RowState.INSERTED, row.getState());
        assertFalse(row.isCellDirty("name"));
        assertFalse(row.isCellDirty("id"));

        row.setValue("name", "Changed New Employee");
        // State remains INSERTED, but dirtyColumns tracks touched column
        assertEquals(ObservableDataRow.RowState.INSERTED, row.getState());
        assertTrue(row.isCellDirty("name"));
        assertFalse(row.isCellDirty("id"));
    }

    @Test
    @DisplayName("Should handle DELETED and undelete transitions")
    void testDeleteAndUndelete() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("id", 1);
        initial.put("name", "Alice");

        ObservableDataRow row = new ObservableDataRow(100, initial, false);
        row.markDeleted();
        assertEquals(ObservableDataRow.RowState.DELETED, row.getState());

        row.unmarkDeleted();
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());

        // Modify then delete then undelete -> should restore MODIFIED
        row.setValue("name", "Bob");
        row.markDeleted();
        assertEquals(ObservableDataRow.RowState.DELETED, row.getState());
        row.unmarkDeleted();
        assertEquals(ObservableDataRow.RowState.MODIFIED, row.getState());
    }

    @Test
    @DisplayName("DE-6: Normalized equality prevents false dirty transitions (NULL->NULL, 5->5, BigDecimal compareTo)")
    void testNormalizedEqualityMaintainsUnchanged() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("id", 5);
        initial.put("note", null);
        initial.put("price", new java.math.BigDecimal("10.00"));

        ObservableDataRow row = new ObservableDataRow(1, initial, false);
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());

        // Set 5 to Integer 5 -> UNCHANGED
        row.setValue("id", 5);
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());
        assertFalse(row.isCellDirty("id"));

        // Set note from null to null -> UNCHANGED
        row.setValue("note", null);
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());
        assertFalse(row.isCellDirty("note"));

        // Set price 10.00 to 10.0 -> UNCHANGED (compareTo == 0)
        row.setValue("price", new java.math.BigDecimal("10.0"));
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());
        assertFalse(row.isCellDirty("price"));
    }
}

