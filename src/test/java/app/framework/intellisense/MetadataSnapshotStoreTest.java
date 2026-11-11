package app.framework.intellisense;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetadataSnapshotStoreTest {

    @Test
    void testChangesObjectsDetection() {
        // Table / View DDL
        assertTrue(MetadataSnapshotStore.changesObjects("CREATE TABLE dbo.Users (id INT);"));
        assertTrue(MetadataSnapshotStore.changesObjects("ALTER TABLE dbo.Users ADD Email VARCHAR(100);"));
        assertTrue(MetadataSnapshotStore.changesObjects("DROP VIEW dbo.ActiveUsers;"));

        // Trigger DDL (Giai đoạn 2 parity)
        assertTrue(MetadataSnapshotStore.changesObjects("CREATE TRIGGER tr_audit ON dbo.Users AFTER INSERT AS BEGIN SELECT 1 END;"));
        assertTrue(MetadataSnapshotStore.changesObjects("DROP TRIGGER tr_audit;"));

        // Procedure / Function / Synonym
        assertTrue(MetadataSnapshotStore.changesObjects("CREATE OR ALTER PROCEDURE usp_GetOrders AS SELECT 1;"));
        assertTrue(MetadataSnapshotStore.changesObjects("CREATE SYNONYM SynUsers FOR OtherDb.dbo.Users;"));

        // Temp tables should NOT trigger server snapshot reload
        assertFalse(MetadataSnapshotStore.changesObjects("CREATE TABLE #temp_orders (id INT);"));
        assertFalse(MetadataSnapshotStore.changesObjects("CREATE TABLE @temp_var (id INT);"));

        // Queries and DML should NOT trigger snapshot reload
        assertFalse(MetadataSnapshotStore.changesObjects("SELECT * FROM dbo.Users;"));
        assertFalse(MetadataSnapshotStore.changesObjects("INSERT INTO dbo.Users (id) VALUES (1);"));
        assertFalse(MetadataSnapshotStore.changesObjects("UPDATE dbo.Users SET id = 2 WHERE id = 1;"));
    }

    @Test
    void testSqlServerKindMapping() {
        assertEquals("table", MetadataSnapshotStore.sqlServerKind("U"));
        assertEquals("view", MetadataSnapshotStore.sqlServerKind("V"));
        assertEquals("procedure", MetadataSnapshotStore.sqlServerKind("P"));
        assertEquals("procedure", MetadataSnapshotStore.sqlServerKind("PC"));
        assertEquals("procedure", MetadataSnapshotStore.sqlServerKind("X"));
        assertEquals("trigger", MetadataSnapshotStore.sqlServerKind("TR"));
        assertEquals("trigger", MetadataSnapshotStore.sqlServerKind("TA"));
        assertEquals("function", MetadataSnapshotStore.sqlServerKind("AF"));
        assertEquals("function", MetadataSnapshotStore.sqlServerKind("FN"));
        assertEquals("function", MetadataSnapshotStore.sqlServerKind("IF"));
        assertEquals("function", MetadataSnapshotStore.sqlServerKind("TF"));
        assertEquals("sequence", MetadataSnapshotStore.sqlServerKind("SO"));
        assertEquals("synonym", MetadataSnapshotStore.sqlServerKind("SN"));
    }
}

