package com.lltool.core.application.tabledesigner;

import com.lltool.core.application.tabledesigner.dialect.PostgresDdlDialect;
import com.lltool.core.application.tabledesigner.dialect.SqlServerDdlDialect;
import com.lltool.core.domain.tabledesigner.model.CheckDesign;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.IndexColumn;
import com.lltool.core.domain.tabledesigner.model.IndexDesign;
import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.application.tabledesigner.model.MigrationStatement;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tabledesigner.model.TriggerDesign;
import com.lltool.core.domain.tabledesigner.valueobject.IndexType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableDesignDifferTest {

    private TableDesignDiffer differ;

    @BeforeEach
    void setUp() {
        differ = new TableDesignDiffer();
    }

    @Test
    @DisplayName("Differ: Empty diff when original and modified are identical")
    void testNoChangesHasEmptyMigrationPlan() {
        TableDesign orig = new TableDesign("sqlserver", "dbo", "Product");
        orig.addColumn(ColumnDesign.loadedFromDb("Id", "int"));

        TableDesign mod = new TableDesign("sqlserver", "dbo", "Product");
        mod.addColumn(ColumnDesign.loadedFromDb("Id", "int"));

        MigrationPlan plan = differ.computeDiff(orig, mod);
        assertFalse(plan.hasChanges());
        assertFalse(plan.hasDestructive());
        assertTrue(plan.statements().isEmpty());
    }

    @Test
    @DisplayName("Differ: New table generates CREATE TABLE statement")
    void testNewTableDiff() {
        TableDesign mod = TableDesign.newTable("sqlserver", "dbo", "Categories");

        MigrationPlan plan = differ.computeDiff(null, mod);
        assertTrue(plan.hasChanges());
        assertEquals(1, plan.statements().size());
        assertTrue(plan.statements().get(0).sql().contains("CREATE TABLE [dbo].[Categories]"));
    }

    @Test
    @DisplayName("Differ: Table rename generates sp_rename on SQL Server and RENAME TO on PostgreSQL")
    void testTableRename() {
        // SQL Server
        TableDesign origSs = new TableDesign("sqlserver", "dbo", "OldTable");
        origSs.addColumn(ColumnDesign.loadedFromDb("Id", "int"));

        TableDesign modSs = new TableDesign("sqlserver", "dbo", "NewTable");
        modSs.setOriginalTable("OldTable");
        modSs.addColumn(ColumnDesign.loadedFromDb("Id", "int"));

        MigrationPlan planSs = differ.computeDiff(origSs, modSs, new SqlServerDdlDialect());
        assertTrue(planSs.hasChanges());
        assertTrue(planSs.statements().stream().anyMatch(s -> s.sql().contains("sp_rename") && s.sql().contains("OldTable") && s.sql().contains("NewTable")));

        // PostgreSQL
        TableDesign origPg = new TableDesign("postgresql", "public", "old_table");
        origPg.addColumn(ColumnDesign.loadedFromDb("id", "int"));

        TableDesign modPg = new TableDesign("postgresql", "public", "new_table");
        modPg.setOriginalTable("old_table");
        modPg.addColumn(ColumnDesign.loadedFromDb("id", "int"));

        MigrationPlan planPg = differ.computeDiff(origPg, modPg, new PostgresDdlDialect());
        assertTrue(planPg.hasChanges());
        assertTrue(planPg.statements().stream().anyMatch(s -> s.sql().contains("RENAME TO \"new_table\"")));
    }

    @Test
    @DisplayName("Differ: Full parity with Python test_diff_generation_and_destructive_detection")
    void testPythonBaselineDiffAndDestructiveDetection() {
        TableDesign orig = new TableDesign("sqlserver", "dbo", "Product");
        ColumnDesign idCol = ColumnDesign.loadedFromDb("Id", "int");
        idCol.setPrimaryKey(true);
        orig.addColumn(idCol);

        ColumnDesign oldCol = ColumnDesign.loadedFromDb("OldColumn", "varchar");
        oldCol.setLength(50);
        orig.addColumn(oldCol);

        ColumnDesign priceCol = ColumnDesign.loadedFromDb("Price", "decimal");
        priceCol.setLength(10);
        priceCol.setScale(2);
        orig.addColumn(priceCol);

        IndexDesign oldIdx = new IndexDesign("IX_Old");
        oldIdx.setColumns(List.of(new IndexColumn("OldColumn", false)));
        orig.setIndexes(List.of(oldIdx));

        // Modified: OldColumn dropped, Price altered, NewColumn added, IX_Old dropped, IX_New added, CK_Price added
        TableDesign mod = new TableDesign("sqlserver", "dbo", "Product");
        mod.addColumn(idCol);

        ColumnDesign alteredPrice = ColumnDesign.loadedFromDb("Price", "decimal");
        alteredPrice.setLength(18);
        alteredPrice.setScale(2);
        alteredPrice.setNotNull(true); // Changed to NOT NULL
        mod.addColumn(alteredPrice);

        ColumnDesign newCol = ColumnDesign.of("NewColumn", "nvarchar");
        newCol.setLength(100);
        mod.addColumn(newCol);

        IndexDesign newIdx = new IndexDesign("IX_New");
        newIdx.setColumns(List.of(new IndexColumn("NewColumn", false)));
        mod.setIndexes(List.of(newIdx));

        CheckDesign ckPrice = new CheckDesign("CK_Price", "[Price] > 0");
        mod.setChecks(List.of(ckPrice));

        MigrationPlan plan = differ.computeDiff(orig, mod, new SqlServerDdlDialect());

        assertTrue(plan.hasDestructive());
        assertTrue(plan.warnings().size() > 0);

        List<String> sqls = plan.statements().stream().map(s -> s.sql().toLowerCase()).toList();

        // 1. Dropped OldColumn
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("drop column [oldcolumn]")));
        assertTrue(plan.statements().stream().anyMatch(s -> s.destructive() && s.sql().toLowerCase().contains("drop column")));

        // 2. Added NewColumn
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("add [newcolumn] nvarchar(100)")));

        // 3. Altered Price
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("alter column [price] decimal(18, 2) not null")));

        // 4. Dropped Index IX_Old
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("drop index [ix_old]")));

        // 5. Added Index IX_New
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("create nonclustered index [ix_new]")));

        // 6. Added Check CK_Price
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("add constraint [ck_price] check ([price] > 0)")));
    }

    @Test
    @DisplayName("Fix B1: Duplicating a column in an existing table is treated as ADD, NOT as RENAME")
    void testFixB1DuplicateColumnNotMistakenAsRename() {
        TableDesign orig = new TableDesign("sqlserver", "dbo", "Users");
        ColumnDesign emailCol = ColumnDesign.loadedFromDb("Email", "varchar");
        emailCol.setLength(100);
        orig.addColumn(emailCol);

        TableDesign mod = new TableDesign("sqlserver", "dbo", "Users");
        // Keep original Email
        mod.addColumn(emailCol);
        // Duplicate Email -> Email_copy with originalName = null
        ColumnDesign clonedCol = emailCol.duplicate("Email_copy");
        mod.addColumn(clonedCol);

        MigrationPlan plan = differ.computeDiff(orig, mod, new SqlServerDdlDialect());

        List<String> sqls = plan.statements().stream().map(s -> s.sql().toLowerCase()).toList();

        // Should ADD Email_copy
        assertTrue(sqls.stream().anyMatch(s -> s.contains("add [email_copy] varchar(100)")), "Must generate ADD [Email_copy]");
        // Must NEVER generate sp_rename from Email to Email_copy!
        assertFalse(sqls.stream().anyMatch(s -> s.contains("sp_rename") && s.contains("email_copy")), "Must NOT rename original Email");
    }

    @Test
    @DisplayName("Fix B3: PostgreSQL new index defaults to btree, NEVER nonclustered")
    void testFixB3PostgreSqlIndexUsesBtreeNotNonclustered() {
        TableDesign orig = new TableDesign("postgresql", "public", "items");
        orig.addColumn(ColumnDesign.loadedFromDb("name", "varchar"));

        TableDesign mod = new TableDesign("postgresql", "public", "items");
        mod.addColumn(ColumnDesign.loadedFromDb("name", "varchar"));

        IndexDesign idx = new IndexDesign("idx_items_name");
        idx.setColumns(List.of(new IndexColumn("name", false)));
        // Even if raw index_type was initialized to NONCLUSTERED, PG dialect maps to btree!
        idx.setIndexType(IndexType.NONCLUSTERED);
        mod.setIndexes(List.of(idx));

        MigrationPlan plan = differ.computeDiff(orig, mod, new PostgresDdlDialect());

        List<String> sqls = plan.statements().stream().map(s -> s.sql().toLowerCase()).toList();
        assertTrue(sqls.stream().anyMatch(s -> s.contains("using btree")));
        assertFalse(sqls.stream().anyMatch(s -> s.contains("nonclustered")));
    }

    @Test
    @DisplayName("Fix B6: size null vs empty string does not produce spurious ALTER")
    void testFixB6SizeNormalization() {
        TableDesign orig = new TableDesign("sqlserver", "dbo", "Orders");
        ColumnDesign col1 = ColumnDesign.loadedFromDb("Notes", "text");
        col1.setLength(null); // size is null
        orig.addColumn(col1);

        TableDesign mod = new TableDesign("sqlserver", "dbo", "Orders");
        ColumnDesign col2 = ColumnDesign.loadedFromDb("Notes", "text");
        col2.setLength(null); // normalized to null
        mod.addColumn(col2);

        MigrationPlan plan = differ.computeDiff(orig, mod, new SqlServerDdlDialect());
        assertFalse(plan.hasChanges(), "Spurious ALTER COLUMN must not be generated when size is unchanged");
    }

    @Test
    @DisplayName("Fix B7: String default value with parentheses is properly quoted as literal")
    void testFixB7DefaultLiteralVsExpression() {
        SqlServerDdlDialect dialect = new SqlServerDdlDialect();

        // When defaultIsExpression = false, string containing parentheses e.g. "Hello (world)" is wrapped in quotes
        String literalResult = dialect.formatDefaultValue("Hello (world)", "varchar", false);
        assertEquals("'Hello (world)'", literalResult);

        // When defaultIsExpression = true, expression is passed as-is
        String exprResult = dialect.formatDefaultValue("(getdate())", "datetime", true);
        assertEquals("(getdate())", exprResult);
    }

    @Test
    @DisplayName("Fix B8: Modified trigger uses CREATE OR ALTER TRIGGER on SQL Server")
    void testFixB8TriggerUsesCreateOrAlter() {
        TableDesign orig = new TableDesign("sqlserver", "dbo", "Customers");
        orig.addColumn(ColumnDesign.loadedFromDb("Id", "int"));
        TriggerDesign origTrig = new TriggerDesign("TR_Customer_Audit", "CREATE TRIGGER [dbo].[TR_Customer_Audit] ON [dbo].[Customers] AFTER INSERT AS BEGIN PRINT 1 END");
        orig.setTriggers(List.of(origTrig));

        TableDesign mod = new TableDesign("sqlserver", "dbo", "Customers");
        mod.addColumn(ColumnDesign.loadedFromDb("Id", "int"));
        TriggerDesign modTrig = new TriggerDesign("TR_Customer_Audit", "CREATE TRIGGER [dbo].[TR_Customer_Audit] ON [dbo].[Customers] AFTER INSERT AS BEGIN PRINT 2 END");
        mod.setTriggers(List.of(modTrig));

        MigrationPlan plan = differ.computeDiff(orig, mod, new SqlServerDdlDialect());
        assertTrue(plan.hasChanges());
        assertTrue(plan.statements().stream().anyMatch(s -> s.sql().contains("CREATE OR ALTER TRIGGER")));
    }

    @Test
    @DisplayName("Quoting: SQL Server and PostgreSQL handle spaces and keywords correctly")
    void testQuotingSpacesAndKeywords() {
        // SQL Server quoting
        TableDesign origSs = new TableDesign("sqlserver", "sales", "Order Details");
        origSs.addColumn(ColumnDesign.loadedFromDb("Order ID", "int"));
        origSs.addColumn(ColumnDesign.loadedFromDb("User", "varchar"));

        TableDesign modSs = new TableDesign("sqlserver", "sales", "Order Details");
        modSs.addColumn(ColumnDesign.loadedFromDb("Order ID", "int"));
        ColumnDesign alteredUser = ColumnDesign.loadedFromDb("User", "nvarchar");
        alteredUser.setLength(200);
        alteredUser.setNotNull(true);
        modSs.addColumn(alteredUser);

        MigrationPlan planSs = differ.computeDiff(origSs, modSs, new SqlServerDdlDialect());
        String fullSs = planSs.generateFullMigrationSql("sqlserver");
        assertTrue(fullSs.contains("ALTER TABLE [sales].[Order Details] ALTER COLUMN [User] nvarchar(200) NOT NULL;"));

        // PostgreSQL quoting
        TableDesign origPg = new TableDesign("postgresql", "my_schema", "Users Table");
        origPg.addColumn(ColumnDesign.loadedFromDb("First Name", "varchar"));

        TableDesign modPg = new TableDesign("postgresql", "my_schema", "Users Table");
        ColumnDesign alteredFirst = ColumnDesign.loadedFromDb("First Name", "varchar");
        alteredFirst.setLength(100);
        alteredFirst.setNotNull(true);
        modPg.addColumn(alteredFirst);

        MigrationPlan planPg = differ.computeDiff(origPg, modPg, new PostgresDdlDialect());
        String fullPg = planPg.generateFullMigrationSql("postgresql");
        assertTrue(fullPg.contains("ALTER TABLE \"my_schema\".\"Users Table\" ALTER COLUMN \"First Name\" TYPE varchar(100);"));
        assertTrue(fullPg.contains("ALTER TABLE \"my_schema\".\"Users Table\" ALTER COLUMN \"First Name\" SET NOT NULL;"));
    }
}
