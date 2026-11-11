package com.lltool.core.application.tabledesigner;

import com.lltool.core.application.tabledesigner.dialect.SqlServerDdlDialect;
import com.lltool.core.domain.tabledesigner.model.CheckDesign;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.ForeignKeyColumnMapping;
import com.lltool.core.domain.tabledesigner.model.ForeignKeyDesign;
import com.lltool.core.domain.tabledesigner.model.IndexColumn;
import com.lltool.core.domain.tabledesigner.model.IndexDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tabledesigner.model.UniqueDesign;
import com.lltool.core.domain.tabledesigner.valueobject.ForeignKeyAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerDdlDialectTest {

    private SqlServerDdlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new SqlServerDdlDialect();
    }

    @Test
    @DisplayName("SQL Server: generateCreateTable matching Python baseline (Customer table)")
    void testSqlServerCreateTableDdl() {
        TableDesign meta = new TableDesign("sqlserver", "dbo", "Customer");

        ColumnDesign idCol = ColumnDesign.of("Id", "int");
        idCol.setPrimaryKey(true);
        idCol.setIdentity(true);
        idCol.setIdentitySeed(1L);
        idCol.setIdentityIncrement(1L);
        meta.addColumn(idCol);

        ColumnDesign codeCol = ColumnDesign.of("CustomerCode", "varchar");
        codeCol.setLength(50);
        codeCol.setNotNull(true);
        meta.addColumn(codeCol);

        ColumnDesign nameCol = ColumnDesign.of("CustomerName", "nvarchar");
        nameCol.setLength(100);
        nameCol.setNotNull(false);
        nameCol.setDefaultValue("''");
        meta.addColumn(nameCol);

        ColumnDesign balCol = ColumnDesign.of("Balance", "decimal");
        balCol.setLength(18);
        balCol.setScale(2);
        balCol.setNotNull(true);
        balCol.setDefaultValue("0");
        meta.addColumn(balCol);

        IndexDesign idx = new IndexDesign("IX_Customer_Code");
        idx.setUnique(true);
        idx.setClustered(false);
        idx.setColumns(List.of(new IndexColumn("CustomerCode", false)));
        meta.setIndexes(List.of(idx));

        UniqueDesign uq = new UniqueDesign("UQ_Customer_Code", List.of("CustomerCode"), false);
        meta.setUniques(List.of(uq));

        CheckDesign ck = new CheckDesign("CK_Customer_Balance", "[Balance] >= 0");
        meta.setChecks(List.of(ck));

        String ddl = dialect.generateCreateTable(meta);

        assertTrue(ddl.contains("CREATE TABLE [dbo].[Customer]"));
        assertTrue(ddl.contains("[Id] int IDENTITY(1,1) NOT NULL"));
        assertTrue(ddl.contains("[CustomerCode] varchar(50) NOT NULL"));
        assertTrue(ddl.contains("[CustomerName] nvarchar(100) DEFAULT '' NULL"));
        assertTrue(ddl.contains("[Balance] decimal(18, 2) DEFAULT 0 NOT NULL"));
        assertTrue(ddl.contains("CONSTRAINT [PK_Customer] PRIMARY KEY CLUSTERED ([Id])"));
        assertTrue(ddl.contains("CONSTRAINT [UQ_Customer_Code] UNIQUE ([CustomerCode])"));
        assertTrue(ddl.contains("CONSTRAINT [CK_Customer_Balance] CHECK ([Balance] >= 0)"));
        assertTrue(ddl.contains("CREATE UNIQUE NONCLUSTERED INDEX [IX_Customer_Code] ON [dbo].[Customer] ([CustomerCode]);"));
    }

    @Test
    @DisplayName("SQL Server: Extended properties escaping single quotes (Fix B16)")
    void testSqlServerExtendedPropertyEscaping() {
        String comment = "John's 'Special' Orders";
        String sql = dialect.generateTableComment("dbo", "Orders", comment, false);

        assertTrue(sql.contains("N'John''s ''Special'' Orders'"));
        assertFalse(sql.contains("N'John's"));
    }

    @Test
    @DisplayName("SQL Server: Quoting identifiers with spaces and special brackets")
    void testSqlServerQuoting() {
        assertTrue(dialect.quoteIdentifier("Order Details").equals("[Order Details]"));
        assertTrue(dialect.quoteIdentifier("weird]name").equals("[weird]]name]"));
        assertTrue(dialect.formatTableName("sales", "Order Details").equals("[sales].[Order Details]"));
    }

    @Test
    @DisplayName("SQL Server: Bit default value formatting")
    void testBitDefaultValueFormatting() {
        assertTrue(dialect.formatDefaultValue("1", "bit", false).equals("((1))"));
        assertTrue(dialect.formatDefaultValue("true", "bit", false).equals("((1))"));
        assertTrue(dialect.formatDefaultValue("0", "bit", false).equals("((0))"));
        assertTrue(dialect.formatDefaultValue("false", "bit", false).equals("((0))"));
    }
}

