package com.lltool.core.application.tabledesigner;

import com.lltool.core.application.tabledesigner.dialect.PostgresDdlDialect;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.IndexColumn;
import com.lltool.core.domain.tabledesigner.model.IndexDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tabledesigner.valueobject.IndexType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresDdlDialectTest {

    private PostgresDdlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new PostgresDdlDialect();
    }

    @Test
    @DisplayName("PostgreSQL: generateCreateTable matching Python baseline (orders table)")
    void testPostgreSqlCreateTableDdl() {
        TableDesign meta = new TableDesign("postgresql", "public", "orders");

        ColumnDesign idCol = ColumnDesign.of("id", "serial");
        idCol.setPrimaryKey(true);
        idCol.setNotNull(true);
        meta.addColumn(idCol);

        ColumnDesign orderNoCol = ColumnDesign.of("order_no", "varchar");
        orderNoCol.setLength(20);
        orderNoCol.setNotNull(true);
        meta.addColumn(orderNoCol);

        ColumnDesign totalCol = ColumnDesign.of("total", "numeric");
        totalCol.setLength(12);
        totalCol.setScale(2);
        totalCol.setNotNull(true);
        totalCol.setDefaultValue("0.00");
        meta.addColumn(totalCol);

        IndexDesign idx = new IndexDesign("idx_orders_no");
        idx.setUnique(true);
        idx.setIndexType(IndexType.BTREE);
        idx.setColumns(List.of(new IndexColumn("order_no", false)));
        meta.setIndexes(List.of(idx));

        String ddl = dialect.generateCreateTable(meta);

        assertTrue(ddl.contains("CREATE TABLE \"public\".\"orders\""));
        assertTrue(ddl.contains("\"id\" serial NOT NULL"));
        assertTrue(ddl.contains("\"order_no\" varchar(20) NOT NULL"));
        assertTrue(ddl.contains("\"total\" numeric(12, 2) DEFAULT 0.00 NOT NULL"));
        assertTrue(ddl.contains("CONSTRAINT \"PK_orders\" PRIMARY KEY (\"id\")"));
        assertTrue(ddl.contains("CREATE UNIQUE INDEX \"idx_orders_no\" ON \"public\".\"orders\" USING btree (\"order_no\");"));
        assertFalse(ddl.contains("nonclustered")); // Fix B3
    }

    @Test
    @DisplayName("PostgreSQL: Quoting identifiers with quotes and spaces")
    void testPostgresQuoting() {
        assertTrue(dialect.quoteIdentifier("Users Table").equals("\"Users Table\""));
        assertTrue(dialect.quoteIdentifier("user\"name").equals("\"user\"\"name\""));
        assertTrue(dialect.formatTableName("my_schema", "Users Table").equals("\"my_schema\".\"Users Table\""));
    }

    @Test
    @DisplayName("PostgreSQL: Table and Column comments")
    void testPostgresComments() {
        String tblComment = dialect.generateTableComment("public", "users", "User's Table", false);
        assertTrue(tblComment.contains("COMMENT ON TABLE \"public\".\"users\" IS 'User''s Table';"));

        String colComment = dialect.generateColumnComment("public", "users", "email", "Login email", false);
        assertTrue(colComment.contains("COMMENT ON COLUMN \"public\".\"users\".\"email\" IS 'Login email';"));
    }
}

