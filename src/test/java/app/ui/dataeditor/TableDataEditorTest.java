package app.ui.dataeditor;

import com.lltool.core.application.tableeditor.TableDataEditorService;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tableeditor.model.DataChangeBatch;
import com.lltool.core.domain.tableeditor.model.DataRowChange;
import com.lltool.core.domain.tableeditor.model.TableEditorMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        assertTrue(stmt.contains("[Name] = 'Worker-1'"));
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
        assertEquals("INSERT INTO [dbo].[Products] ([Title]) VALUES ('Clean Architecture');", dml.get(0));
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
}
