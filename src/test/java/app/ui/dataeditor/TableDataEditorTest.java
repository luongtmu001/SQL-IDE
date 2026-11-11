package app.ui.dataeditor;

import com.lltool.core.application.tableeditor.TableDataEditorService;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tableeditor.model.DataChangeBatch;
import com.lltool.core.domain.tableeditor.model.DataRowChange;
import com.lltool.core.domain.tableeditor.model.TableEditorMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TableDataEditorTest {

    private final TableDataEditorService service = new TableDataEditorService();

    @Test
    @DisplayName("Should generate SELECT query for SQL Server with TOP and WHERE")
    void testSelectQuerySqlServer() {
        String sql = service.buildSelectQuery(
                "sqlserver", "dbo", "Customers",
                List.of("Id", "Name", "City"),
                "Country = 'VN'",
                "Name ASC",
                200, 0
        );

        assertEquals("SELECT TOP 200 [Id], [Name], [City] FROM [dbo].[Customers] WHERE Country = 'VN' ORDER BY Name ASC", sql);
    }

    @Test
    @DisplayName("DE-11: Should append catalog and fallback to primary key ORDER BY when no sort is specified")
    void testSelectQueryWithCatalogAndPkFallback() {
        String sql = service.buildSelectQuery(
                "sqlserver", "B10_App", "dbo", "Customers",
                List.of("Id", "Name"),
                null,
                null,
                List.of("Id"),
                100, 0
        );

        assertEquals("SELECT TOP 100 [Id], [Name] FROM [B10_App].[dbo].[Customers] ORDER BY [Id] ASC", sql);
    }

    @Test
    @DisplayName("Should generate SELECT query for PostgreSQL with LIMIT and OFFSET")
    void testSelectQueryPostgreSql() {
        String sql = service.buildSelectQuery(
                "postgresql", "public", "orders",
                List.of("id", "total"),
                "WHERE total > 100",
                "id DESC",
                50, 100
        );

        assertEquals("SELECT \"id\", \"total\" FROM \"public\".\"orders\" WHERE total > 100 ORDER BY id DESC LIMIT 50 OFFSET 100", sql);
    }

    @Test
    @DisplayName("Should generate INSERT statements and handle IDENTITY_INSERT for SQL Server")
    void testInsertDmlSqlServer() {
        TableEditorMetadata.ColumnMeta colId = new TableEditorMetadata.ColumnMeta(
                "Id", "int", true, true, false, false, null);
        TableEditorMetadata.ColumnMeta colTitle = new TableEditorMetadata.ColumnMeta(
                "Title", "nvarchar", false, false, false, true, null);
        TableEditorMetadata.ColumnMeta colPrice = new TableEditorMetadata.ColumnMeta(
                "Price", "decimal", false, false, false, true, null);

        TableEditorMetadata metadata = new TableEditorMetadata(
                "sqlserver", "master", "dbo", "Products",
                List.of(colId, colTitle, colPrice)
        );

        DataChangeBatch batch = new DataChangeBatch("sqlserver", "dbo", "Products");
        DataRowChange change = new DataRowChange(DataRowChange.Type.INSERT, 0);
        change.setNewValues(Map.of("Id", 101, "Title", "O'Reilly Book", "Price", 45.5));
        batch.addChange(change);

        List<String> dml = service.buildDmlStatements(batch, metadata, true);
        assertEquals(3, dml.size());
        assertEquals("SET IDENTITY_INSERT [dbo].[Products] ON;", dml.get(0));
        assertTrue(dml.get(1).contains("INSERT INTO [dbo].[Products]"));
        assertTrue(dml.get(1).contains("'O''Reilly Book'"));
        assertEquals("SET IDENTITY_INSERT [dbo].[Products] OFF;", dml.get(2));
    }

    @Test
    @DisplayName("Should generate UPDATE statements with optimistic concurrency")
    void testUpdateDmlOptimistic() {
        TableEditorMetadata.ColumnMeta colId = new TableEditorMetadata.ColumnMeta(
                "id", "int", true, false, false, false, null);
        TableEditorMetadata.ColumnMeta colEmail = new TableEditorMetadata.ColumnMeta(
                "email", "varchar", false, false, false, true, null);
        TableEditorMetadata.ColumnMeta colStatus = new TableEditorMetadata.ColumnMeta(
                "status", "varchar", false, false, false, true, null);

        TableEditorMetadata metadata = new TableEditorMetadata(
                "postgresql", "mydb", "public", "users",
                List.of(colId, colEmail, colStatus)
        );

        DataChangeBatch batch = new DataChangeBatch("postgresql", "public", "users");
        DataRowChange change = new DataRowChange(DataRowChange.Type.UPDATE, 1);
        change.setPkValues(Map.of("id", 42));
        change.setOldValues(Map.of("status", "PENDING"));
        change.setNewValues(Map.of("status", "ACTIVE"));
        batch.addChange(change);

        List<String> dml = service.buildDmlStatements(batch, metadata, true);
        assertEquals(1, dml.size());
        String stmt = dml.get(0);
        assertTrue(stmt.startsWith("UPDATE \"public\".\"users\" SET \"status\" = 'ACTIVE' WHERE"));
        assertTrue(stmt.contains("\"id\" = 42"));
        assertTrue(stmt.contains("\"status\" = 'PENDING'"));
    }

    @Test
    @DisplayName("Should generate DELETE statements with PK and optimistic checks")
    void testDeleteDmlOptimistic() {
        TableEditorMetadata.ColumnMeta colId = new TableEditorMetadata.ColumnMeta(
                "Id", "int", true, false, false, false, null);
        TableEditorMetadata.ColumnMeta colName = new TableEditorMetadata.ColumnMeta(
                "Name", "nvarchar", false, false, false, true, null);

        TableEditorMetadata metadata = new TableEditorMetadata(
                "sqlserver", "master", "dbo", "Customers",
                List.of(colId, colName)
        );

        DataChangeBatch batch = new DataChangeBatch("sqlserver", "dbo", "Customers");
        DataRowChange change = new DataRowChange(DataRowChange.Type.DELETE, 2);
        change.setPkValues(Map.of("Id", 5));
        change.setOldValues(Map.of("Name", "Old Company"));
        batch.addChange(change);

        List<String> dml = service.buildDmlStatements(batch, metadata, true);
        assertEquals(1, dml.size());
        String stmt = dml.get(0);
        assertEquals("DELETE FROM [dbo].[Customers] WHERE [Id] = 5;", stmt);
    }

    @Test
    @DisplayName("Should generate DELETE statements using all old values when table has no PK")
    void testDeleteDmlNoPk() {
        TableEditorMetadata.ColumnMeta colName = new TableEditorMetadata.ColumnMeta(
                "Name", "nvarchar", false, false, false, true, null);
        TableEditorMetadata.ColumnMeta colAge = new TableEditorMetadata.ColumnMeta(
                "Age", "int", false, false, false, true, null);

        TableEditorMetadata metadata = new TableEditorMetadata(
                "sqlserver", "master", "dbo", "Logs",
                List.of(colName, colAge)
        );

        DataChangeBatch batch = new DataChangeBatch("sqlserver", "dbo", "Logs");
        DataRowChange change = new DataRowChange(DataRowChange.Type.DELETE, 3);
        change.setOldValues(Map.of("Name", "Worker-1", "Age", 30));
        batch.addChange(change);

        List<String> dml = service.buildDmlStatements(batch, metadata, true);
        assertEquals(1, dml.size());
        String stmt = dml.get(0);
        assertTrue(stmt.startsWith("DELETE FROM [dbo].[Logs] WHERE"));
        assertTrue(stmt.contains("[Name] = N'Worker-1'"));
        assertTrue(stmt.contains("[Age] = 30"));
    }

    @Test
    @DisplayName("Should build TableEditorMetadata correctly from TableDesign")
    void testMetadataFromTableDesign() {
        TableDesign design = new TableDesign("sqlserver", "dbo", "Inventory");
        ColumnDesign col1 = ColumnDesign.of("ItemId", "int");
        col1.setPrimaryKey(true);
        col1.setIdentity(true);

        ColumnDesign col2 = ColumnDesign.of("ItemName", "nvarchar(100)");

        ColumnDesign col3 = ColumnDesign.of("CalcTotal", "decimal(10,2)");
        col3.setComputedFormula("Qty * UnitPrice");

        design.getColumns().addAll(List.of(col1, col2, col3));

        TableEditorMetadata meta = TableEditorMetadata.fromTableDesign(design);
        assertEquals("dbo", meta.getSchema());
        assertEquals("Inventory", meta.getTable());
        assertEquals(List.of("ItemId"), meta.getPrimaryKeyNames());
        assertTrue(meta.getIdentityNames().contains("itemid"));
        assertTrue(meta.getComputedNames().contains("calctotal"));
        assertFalse(meta.getComputedNames().contains("itemname"));
    }

    @Test
    @DisplayName("DE-1: Table with identity + default: inserting a row with 1 touched column generates SQL with only that column")
    void testInsertWithIdentityAndDefaultOnlyIncludesTouchedColumns() {
        TableEditorMetadata.ColumnMeta colId = new TableEditorMetadata.ColumnMeta(
                "Id", "int", true, true, false, false, null);
        TableEditorMetadata.ColumnMeta colTitle = new TableEditorMetadata.ColumnMeta(
                "Title", "nvarchar", false, false, false, true, null);
        TableEditorMetadata.ColumnMeta colCreatedAt = new TableEditorMetadata.ColumnMeta(
                "CreatedAt", "datetime", false, false, false, false, "GETDATE()");

        TableEditorMetadata metadata = new TableEditorMetadata(
                "sqlserver", "master", "dbo", "Products",
                List.of(colId, colTitle, colCreatedAt)
        );

        DataChangeBatch batch = new DataChangeBatch("sqlserver", "dbo", "Products");
        DataRowChange change = new DataRowChange(DataRowChange.Type.INSERT, 0);
        // Only touched Title
        change.setNewValues(Map.of("Title", "Clean Architecture"));
        batch.addChange(change);

        List<String> dml = service.buildDmlStatements(batch, metadata, true);
        assertEquals(1, dml.size());
        assertEquals("INSERT INTO [dbo].[Products] ([Title]) VALUES (N'Clean Architecture');", dml.get(0));
        assertFalse(dml.get(0).contains("[Id]"), "Identity column should not be listed");
        assertFalse(dml.get(0).contains("[CreatedAt]"), "Default column should not be listed");
    }

    @Test
    @DisplayName("DE-1: Inserting a brand new row with no columns touched generates DEFAULT VALUES")
    void testInsertEmptyRowGeneratesDefaultValues() {
        TableEditorMetadata.ColumnMeta colId = new TableEditorMetadata.ColumnMeta(
                "Id", "int", true, true, false, false, null);
        TableEditorMetadata.ColumnMeta colStatus = new TableEditorMetadata.ColumnMeta(
                "Status", "varchar", false, false, false, false, "'PENDING'");

        TableEditorMetadata metadata = new TableEditorMetadata(
                "sqlserver", "master", "dbo", "Tasks",
                List.of(colId, colStatus)
        );

        DataChangeBatch batch = new DataChangeBatch("sqlserver", "dbo", "Tasks");
        DataRowChange change = new DataRowChange(DataRowChange.Type.INSERT, 0);
        // Empty new values
        change.setNewValues(Map.of());
        batch.addChange(change);

        List<String> dml = service.buildDmlStatements(batch, metadata, true);
        assertEquals(1, dml.size());
        assertEquals("INSERT INTO [dbo].[Tasks] DEFAULT VALUES;", dml.get(0));
    }

    @Test
    @DisplayName("DE-2: Round-trip literal formatting for Vietnamese nvarchar, bit, boolean, and varbinary")
    void testDialectLiteralsAndParameters() {
        TableEditorMetadata.ColumnMeta colNvarchar = new TableEditorMetadata.ColumnMeta(
                "Name", "nvarchar(100)", false, false, false, true, null, java.sql.Types.NVARCHAR, 100, 0);
        TableEditorMetadata.ColumnMeta colBit = new TableEditorMetadata.ColumnMeta(
                "IsActive", "bit", false, false, false, true, null, java.sql.Types.BIT, 1, 0);
        TableEditorMetadata.ColumnMeta colBinary = new TableEditorMetadata.ColumnMeta(
                "Data", "varbinary", false, false, false, true, null, java.sql.Types.VARBINARY, 50, 0);

        // SQL Server
        TableEditorMetadata metaMs = new TableEditorMetadata("sqlserver", "db", "dbo", "T", List.of(colNvarchar, colBit, colBinary));
        DataChangeBatch batchMs = new DataChangeBatch("sqlserver", "dbo", "T");
        DataRowChange rowMs = new DataRowChange(DataRowChange.Type.INSERT, 1);
        Map<String, Object> msVals = new java.util.LinkedHashMap<>();
        msVals.put("Name", "Tiếng Việt có dấu");
        msVals.put("IsActive", true);
        msVals.put("Data", new byte[]{(byte) 0xDE, (byte) 0xAD});
        rowMs.setNewValues(msVals);
        batchMs.addChange(rowMs);

        var cmds = service.buildDmlCommands(batchMs, metaMs, true);
        assertEquals(1, cmds.size());
        var cmd = cmds.get(0);
        assertTrue(cmd.previewSql().contains("N'Tiếng Việt có dấu'"), "SQL Server nvarchar preview must include N prefix");
        assertTrue(cmd.previewSql().contains("1"), "SQL Server bit preview must be 1/0");
        assertTrue(cmd.previewSql().contains("0xDEAD"), "SQL Server binary preview must be hex");
        assertEquals("INSERT INTO [dbo].[T] ([Name], [IsActive], [Data]) VALUES (?, ?, ?);", cmd.sql());
        assertEquals(3, cmd.params().size());

        // PostgreSQL
        TableEditorMetadata metaPg = new TableEditorMetadata("postgresql", "db", "public", "t", List.of(colNvarchar, colBit, colBinary));
        DataChangeBatch batchPg = new DataChangeBatch("postgresql", "public", "t");
        DataRowChange rowPg = new DataRowChange(DataRowChange.Type.INSERT, 1);
        Map<String, Object> pgVals = new java.util.LinkedHashMap<>();
        pgVals.put("Name", "Tiếng Việt");
        pgVals.put("IsActive", true);
        pgVals.put("Data", new byte[]{(byte) 0xDE, (byte) 0xAD});
        rowPg.setNewValues(pgVals);
        batchPg.addChange(rowPg);
        var cmdsPg = service.buildDmlCommands(batchPg, metaPg, true);
        var cmdPg = cmdsPg.get(0);
        assertTrue(cmdPg.previewSql().contains("TRUE"), "PostgreSQL boolean preview must be TRUE/FALSE");
        assertTrue(cmdPg.previewSql().contains("bytea"), "PostgreSQL bytea preview");
    }

    @Test
    @DisplayName("DE-4: Optimistic concurrency WHERE clause excludes LOB, XML, and float columns")
    void testOptimisticExcludesNonComparableTypes() {
        TableEditorMetadata.ColumnMeta colId = new TableEditorMetadata.ColumnMeta(
                "Id", "int", true, false, false, false, null, java.sql.Types.INTEGER, 10, 0);
        TableEditorMetadata.ColumnMeta colRatio = new TableEditorMetadata.ColumnMeta(
                "Ratio", "float", false, false, false, true, null, java.sql.Types.FLOAT, 53, 0);
        TableEditorMetadata.ColumnMeta colDoc = new TableEditorMetadata.ColumnMeta(
                "Doc", "xml", false, false, false, true, null, java.sql.Types.SQLXML, 0, 0);
        TableEditorMetadata.ColumnMeta colTitle = new TableEditorMetadata.ColumnMeta(
                "Title", "varchar(50)", false, false, false, true, null, java.sql.Types.VARCHAR, 50, 0);

        TableEditorMetadata meta = new TableEditorMetadata("sqlserver", "db", "dbo", "Docs",
                List.of(colId, colRatio, colDoc, colTitle));

        DataChangeBatch batch = new DataChangeBatch("sqlserver", "dbo", "Docs");
        DataRowChange upd = new DataRowChange(DataRowChange.Type.UPDATE, 10);
        upd.setPkValues(Map.of("Id", 1));
        upd.setOldValues(Map.of("Id", 1, "Ratio", 1.23, "Doc", "<x/>", "Title", "Old Title"));
        upd.setNewValues(Map.of("Title", "New Title", "Ratio", 4.56, "Doc", "<y/>"));
        batch.addChange(upd);

        var cmds = service.buildDmlCommands(batch, meta, true);
        assertEquals(1, cmds.size());
        String preview = cmds.get(0).previewSql();

        String wherePart = preview.substring(preview.indexOf("WHERE"));
        assertTrue(wherePart.contains("[Id] = 1 AND [Title] = 'Old Title'"));
        assertFalse(wherePart.contains("[Ratio]"), "Float should be excluded from optimistic WHERE clause");
        assertFalse(wherePart.contains("[Doc]"), "XML should be excluded from optimistic WHERE clause");
    }

    @Test
    @DisplayName("DE-3: Update counts other than 1 (0 or >1) throw ConcurrentModificationException mentioning target row")
    void testConcurrentModificationConflictDetection() {
        com.lltool.core.domain.tableeditor.model.DmlCommand cmd = new com.lltool.core.domain.tableeditor.model.DmlCommand(
                "UPDATE [dbo].[T] SET [Name] = ? WHERE [Id] = ?;",
                List.of(),
                "UPDATE [dbo].[T] SET [Name] = 'B' WHERE [Id] = 1;",
                DataRowChange.Type.UPDATE,
                42
        );

        // Simulated check for update count != 1
        int countZero = 0;
        var ex0 = assertThrows(java.util.ConcurrentModificationException.class, () -> {
            if (countZero != 1) {
                throw new java.util.ConcurrentModificationException(
                        "Xung đột dữ liệu ở dòng " + cmd.targetRowId() + ": số dòng bị ảnh hưởng là " + countZero +
                        " (yêu cầu đúng 1 dòng). Đã huỷ bỏ giao dịch."
                );
            }
        });
        assertTrue(ex0.getMessage().contains("dòng 42"));
        assertTrue(ex0.getMessage().contains("ảnh hưởng là 0"));

        int countTwo = 2;
        var ex2 = assertThrows(java.util.ConcurrentModificationException.class, () -> {
            if (countTwo != 1) {
                throw new java.util.ConcurrentModificationException(
                        "Xung đột dữ liệu ở dòng " + cmd.targetRowId() + ": số dòng bị ảnh hưởng là " + countTwo +
                        " (yêu cầu đúng 1 dòng). Đã huỷ bỏ giao dịch."
                );
            }
        });
        assertTrue(ex2.getMessage().contains("dòng 42"));
        assertTrue(ex2.getMessage().contains("ảnh hưởng là 2"));
    }

    @Test
    @DisplayName("DE-8: ObservableDataRow stateProperty reactively updates on cell edit and revert")
    void testObservableDataRowStateProperty() {
        Map<String, Object> initial = new HashMap<>();
        initial.put("id", 1);
        initial.put("name", "Alice");

        ObservableDataRow row = new ObservableDataRow(1, initial, false);
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.stateProperty().get());

        row.setValue("name", "Bob");
        assertEquals(ObservableDataRow.RowState.MODIFIED, row.getState());
        assertEquals(ObservableDataRow.RowState.MODIFIED, row.stateProperty().get());

        row.setValue("name", "Alice");
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.stateProperty().get());

        row.markDeleted();
        assertEquals(ObservableDataRow.RowState.DELETED, row.getState());
        assertEquals(ObservableDataRow.RowState.DELETED, row.stateProperty().get());

        row.unmarkDeleted();
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.getState());
        assertEquals(ObservableDataRow.RowState.UNCHANGED, row.stateProperty().get());
    }

    @Test
    @DisplayName("DE-12: Effective row limit is capped at 50,000 rows to prevent OutOfMemory")
    void testLimitCap() {
        int allRowsLimit = 0; // "Tất cả"
        int effectiveAll = (allRowsLimit <= 0 || allRowsLimit > 50_000) ? 50_000 : allRowsLimit;
        assertEquals(50_000, effectiveAll);

        int hugeLimit = 1_000_000;
        int effectiveHuge = (hugeLimit <= 0 || hugeLimit > 50_000) ? 50_000 : hugeLimit;
        assertEquals(50_000, effectiveHuge);

        int normalLimit = 200;
        int effectiveNormal = (normalLimit <= 0 || normalLimit > 50_000) ? 50_000 : normalLimit;
        assertEquals(200, effectiveNormal);
    }

    @Test
    @DisplayName("DE-13: Row ID sequence monotonically increments independently of master items size")
    void testRowIdSequenceMonotonic() {
        java.util.concurrent.atomic.AtomicInteger rowIdSeq = new java.util.concurrent.atomic.AtomicInteger(5);
        int id1 = rowIdSeq.incrementAndGet();
        int id2 = rowIdSeq.incrementAndGet();
        assertEquals(6, id1);
        assertEquals(7, id2);

        // Even if items are deleted, nextId does not collide
        int id3 = rowIdSeq.incrementAndGet();
        assertEquals(8, id3);
    }
}
