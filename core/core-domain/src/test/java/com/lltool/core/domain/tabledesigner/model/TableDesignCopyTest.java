package com.lltool.core.domain.tabledesigner.model;

import com.lltool.core.domain.tabledesigner.valueobject.ColumnId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TableDesign Copy / Duplication Tests (SCR-TD-001 / TD5)")
class TableDesignCopyTest {

    @Test
    @DisplayName("makeSafeIdentifier truncates identifiers to respect engine limits (128 for SQL Server, 63 for PostgreSQL)")
    void testMakeSafeIdentifier() {
        // SQL Server: max 128
        String longSqlName = "A".repeat(150);
        String safeSql = TableDesign.makeSafeIdentifier(longSqlName, "_Copy", "sqlserver");
        assertEquals(128, safeSql.length());
        assertTrue(safeSql.endsWith("_Copy"));

        // PostgreSQL: max 63
        String longPgName = "B".repeat(100);
        String safePg = TableDesign.makeSafeIdentifier(longPgName, "_Copy", "postgresql");
        assertEquals(63, safePg.length());
        assertTrue(safePg.endsWith("_Copy"));

        // Normal short name
        assertEquals("Users_Copy", TableDesign.makeSafeIdentifier("Users", "_Copy", "sqlserver"));
    }

    @Test
    @DisplayName("cloneForCopy fixes Bug B1: generates new ColumnIds and resets originalName to null")
    void testCloneForCopy_FixesBugB1() {
        TableDesign original = new TableDesign("sqlserver", "dbo", "Customers");
        ColumnDesign c1 = ColumnDesign.loadedFromDb("CustomerId", "int");
        c1.setPrimaryKey(true);
        ColumnDesign c2 = ColumnDesign.loadedFromDb("CustomerName", "nvarchar");
        c2.setLength(100);
        original.addColumn(c1);
        original.addColumn(c2);

        CopyTableOptions options = CopyTableOptions.defaults();
        TableDesign copy = original.cloneForCopy("sales", "Customers_Copy", options);

        assertEquals("sales", copy.getSchema());
        assertEquals("Customers_Copy", copy.getTable());
        assertNull(copy.getOriginalTable(), "Copy Table must be treated as a brand-new table, originalTable must be null");

        assertEquals(2, copy.getColumns().size());

        ColumnDesign copyCol1 = copy.getColumns().get(0);
        assertNotEquals(c1.getId(), copyCol1.getId(), "Copied column must receive a brand-new ColumnId");
        assertNull(copyCol1.getOriginalName(), "Copied column must have originalName = null (Fixes Bug B1)");
        assertEquals("CustomerId", copyCol1.getName());
        assertTrue(copyCol1.isPrimaryKey());

        ColumnDesign copyCol2 = copy.getColumns().get(1);
        assertNotEquals(c2.getId(), copyCol2.getId(), "Copied column must receive a brand-new ColumnId");
        assertNull(copyCol2.getOriginalName(), "Copied column must have originalName = null (Fixes Bug B1)");
        assertEquals(100, copyCol2.getLength());
    }

    @Test
    @DisplayName("cloneForCopy respects CopyTableOptions: Primary Key toggle")
    void testCloneForCopy_PrimaryKeyToggle() {
        TableDesign original = new TableDesign("sqlserver", "dbo", "Products");
        ColumnDesign pk = ColumnDesign.loadedFromDb("Id", "int");
        pk.setPrimaryKey(true);
        pk.setIdentity(true);
        original.addColumn(pk);

        // Copy WITHOUT PK
        CopyTableOptions noPk = new CopyTableOptions(true, false, true, true, true, false);
        TableDesign copy = original.cloneForCopy("dbo", "Products_NoPk", noPk);

        assertFalse(copy.getColumns().get(0).isPrimaryKey(), "PK flag should be cleared when copyPrimaryKey is false");
        assertFalse(copy.getColumns().get(0).isIdentity(), "Identity flag should be cleared when copyPrimaryKey is false");
    }

    @Test
    @DisplayName("cloneForCopy renames Indexes, FKs and Constraints with safe suffix _Copy")
    void testCloneForCopy_RenameConstraintsAndIndexes() {
        TableDesign original = new TableDesign("postgresql", "public", "Orders");
        ColumnDesign idCol = ColumnDesign.loadedFromDb("id", "int");
        idCol.setPrimaryKey(true);
        ColumnDesign codeCol = ColumnDesign.loadedFromDb("order_code", "varchar");
        original.addColumn(idCol);
        original.addColumn(codeCol);

        // Index
        IndexDesign idx = new IndexDesign("idx_orders_code");
        idx.getColumns().add(new IndexColumn(codeCol.getId(), "order_code", false));
        original.addIndex(idx);

        // Unique
        original.addUnique(new UniqueDesign("uq_orders_code", List.of("order_code"), false));

        // Check
        original.addCheck(new CheckDesign("chk_orders_code", "order_code <> ''"));

        CopyTableOptions options = CopyTableOptions.defaults();
        TableDesign copy = original.cloneForCopy("public", "Orders_Copy", options);

        assertEquals(1, copy.getIndexes().size());
        assertEquals("idx_orders_code_Copy", copy.getIndexes().get(0).getName());
        // Verify index column points to the new ColumnId
        ColumnId newCodeColId = copy.getColumns().get(1).getId();
        assertEquals(newCodeColId, copy.getIndexes().get(0).getColumns().get(0).getColumnId());

        assertEquals(1, copy.getUniques().size());
        assertEquals("uq_orders_code_Copy", copy.getUniques().get(0).getName());

        assertEquals(1, copy.getChecks().size());
        assertEquals("chk_orders_code_Copy", copy.getChecks().get(0).getName());
    }

    @Test
    @DisplayName("cloneForCopy fixes Bug B10: Regex word-boundary replacements in trigger definition")
    void testCloneForCopy_FixesBugB10_TriggerDefinitionReplacement() {
        TableDesign original = new TableDesign("sqlserver", "dbo", "Order");
        ColumnDesign col = ColumnDesign.loadedFromDb("OrderId", "int");
        original.addColumn(col);

        // Trigger definition references [Order] and also OrderItems (substring should NOT be altered)
        String def = "CREATE TRIGGER tr_Order_Insert ON [Order] AFTER INSERT AS\n" +
                "BEGIN\n" +
                "    INSERT INTO OrderItems (OrderId) SELECT OrderId FROM inserted;\n" +
                "END";
        TriggerDesign tr = new TriggerDesign("tr_Order_Insert", def);
        original.addTrigger(tr);

        // Copy WITH triggers enabled
        CopyTableOptions withTrig = new CopyTableOptions(true, true, true, true, true, true);
        TableDesign copy = original.cloneForCopy("dbo", "Order_Copy", withTrig);

        assertEquals(1, copy.getTriggers().size());
        TriggerDesign copyTrig = copy.getTriggers().get(0);
        assertEquals("tr_Order_Insert_Copy", copyTrig.getName());

        String updatedDef = copyTrig.getDefinition();
        assertTrue(updatedDef.contains("CREATE TRIGGER tr_Order_Insert_Copy"), "Trigger name should be replaced with suffix");
        assertTrue(updatedDef.contains("ON [Order_Copy]"), "Table name in ON clause should be replaced");
        assertTrue(updatedDef.contains("INSERT INTO OrderItems"), "OrderItems substring must NOT be replaced (Bug B10 fix)");

        // Copy WITHOUT triggers
        CopyTableOptions noTrig = new CopyTableOptions(true, true, true, true, true, false);
        TableDesign copyNoTrig = original.cloneForCopy("dbo", "Order_NoTrig", noTrig);
        assertTrue(copyNoTrig.getTriggers().isEmpty(), "Triggers must be excluded when copyTriggers is false");
    }
}

