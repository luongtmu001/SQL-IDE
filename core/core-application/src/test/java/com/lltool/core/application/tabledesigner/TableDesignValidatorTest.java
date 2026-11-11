package com.lltool.core.application.tabledesigner;

import com.lltool.core.common.validation.ValidationResult;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableDesignValidatorTest {

    private TableDesignValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TableDesignValidator();
    }

    @Test
    @DisplayName("Validator: Valid table passes")
    void testValidTable() {
        TableDesign table = TableDesign.newTable("sqlserver", "dbo", "Customers");
        ValidationResult result = validator.validate(table);
        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Validator: Empty table name fails")
    void testEmptyTableName() {
        TableDesign table = new TableDesign("sqlserver", "dbo", "   ");
        table.addColumn(ColumnDesign.of("Id", "int"));
        ValidationResult result = validator.validate(table);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals(TableDesignValidator.ERR_TABLE_EMPTY)));
    }

    @Test
    @DisplayName("Validator: No columns fails")
    void testNoColumns() {
        TableDesign table = new TableDesign("sqlserver", "dbo", "Orders");
        ValidationResult result = validator.validate(table);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals(TableDesignValidator.ERR_NO_COLUMNS)));
    }

    @Test
    @DisplayName("Validator: Empty column name fails")
    void testEmptyColumnName() {
        TableDesign table = new TableDesign("sqlserver", "dbo", "Orders");
        table.addColumn(ColumnDesign.of("  ", "int"));
        ValidationResult result = validator.validate(table);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals(TableDesignValidator.ERR_COL_NAME_EMPTY)));
    }

    @Test
    @DisplayName("Validator: Duplicate column names (case-insensitive) fails (Fix B13)")
    void testDuplicateColumnNames() {
        TableDesign table = new TableDesign("sqlserver", "dbo", "Orders");
        table.addColumn(ColumnDesign.of("Status", "int"));
        table.addColumn(ColumnDesign.of("status", "varchar")); // Duplicate with different case!

        ValidationResult result = validator.validate(table);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals(TableDesignValidator.ERR_COL_NAME_DUPLICATE)));
    }

    @Test
    @DisplayName("Validator: Empty data type fails")
    void testEmptyDataType() {
        TableDesign table = new TableDesign("sqlserver", "dbo", "Orders");
        table.addColumn(ColumnDesign.of("Id", "  "));
        ValidationResult result = validator.validate(table);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals(TableDesignValidator.ERR_TYPE_EMPTY)));
    }

    @Test
    @DisplayName("Validator: Decimal scale greater than precision fails")
    void testDecimalScaleGreaterThanPrecision() {
        TableDesign table = new TableDesign("sqlserver", "dbo", "Orders");
        ColumnDesign dec = ColumnDesign.of("Amount", "decimal");
        dec.setLength(5);
        dec.setScale(7); // scale 7 > precision 5!
        table.addColumn(dec);

        ValidationResult result = validator.validate(table);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals(TableDesignValidator.ERR_INVALID_SCALE)));
    }

    @Test
    @DisplayName("Validator: Identity column with default value fails")
    void testIdentityWithDefault() {
        TableDesign table = new TableDesign("sqlserver", "dbo", "Orders");
        ColumnDesign idCol = ColumnDesign.of("Id", "int");
        idCol.setIdentity(true);
        idCol.setDefaultValue("100");
        table.addColumn(idCol);

        ValidationResult result = validator.validate(table);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals(TableDesignValidator.ERR_IDENTITY_WITH_DEFAULT)));
    }
}

