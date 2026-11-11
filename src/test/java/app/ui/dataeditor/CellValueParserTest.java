package app.ui.dataeditor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CellValueParserTest {

    @Test
    @DisplayName("DE-6: Should parse numbers, decimals, and booleans correctly")
    void testNumericAndBooleanParsing() {
        assertEquals(42, CellValueParser.parse("42", Types.INTEGER, "int"));
        assertEquals(9999999999L, CellValueParser.parse("9999999999", Types.BIGINT, "bigint"));
        assertEquals(3.1415, (Double) CellValueParser.parse("3.1415", Types.DOUBLE, "float"), 0.0001);
        assertEquals(new BigDecimal("12345.6789"), CellValueParser.parse("12345.6789", Types.DECIMAL, "decimal(18,4)"));

        assertEquals(Boolean.TRUE, CellValueParser.parse("1", Types.BIT, "bit"));
        assertEquals(Boolean.FALSE, CellValueParser.parse("0", Types.BIT, "bit"));
        assertEquals(Boolean.TRUE, CellValueParser.parse("true", Types.BOOLEAN, "boolean"));
        assertEquals(Boolean.FALSE, CellValueParser.parse("false", Types.BOOLEAN, "boolean"));
    }

    @Test
    @DisplayName("DE-6: Should parse temporal and UUID values")
    void testDateAndTimeAndUuid() {
        assertEquals(Date.valueOf(LocalDate.of(2026, 11, 11)), CellValueParser.parse("2026-11-11", Types.DATE, "date"));
        assertEquals(Timestamp.valueOf("2026-11-11 12:30:00"), CellValueParser.parse("2026-11-11 12:30:00", Types.TIMESTAMP, "datetime2"));

        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, CellValueParser.parse(uuid.toString(), Types.OTHER, "uuid"));
    }

    @Test
    @DisplayName("DE-6: Should handle NULL representations and empty strings")
    void testNullHandling() {
        assertNull(CellValueParser.parse(null, Types.VARCHAR, "varchar"));
        assertNull(CellValueParser.parse("[NULL]", Types.INTEGER, "int"));
        assertNull(CellValueParser.parse("   ", Types.INTEGER, "int"));

        // For string types, empty string is preserved
        assertEquals("", CellValueParser.parse("", Types.VARCHAR, "varchar"));
    }

    @Test
    @DisplayName("DE-6: Should throw IllegalArgumentException when format is invalid")
    void testInvalidNumberThrows() {
        assertThrows(IllegalArgumentException.class, () -> CellValueParser.parse("abc", Types.INTEGER, "int"));
        assertThrows(IllegalArgumentException.class, () -> CellValueParser.parse("not-a-boolean", Types.BIT, "bit"));
        assertThrows(IllegalArgumentException.class, () -> CellValueParser.parse("invalid-uuid", Types.OTHER, "uuid"));
    }
}
