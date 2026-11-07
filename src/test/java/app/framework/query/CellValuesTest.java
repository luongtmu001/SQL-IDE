package app.framework.query;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** D4: cell text as LLTool shows it (serialize_cell), without a server. */
class CellValuesTest {

    @Test
    void binaryIsHex() {
        assertEquals("0x0102AB", new CellValues.BinaryValue(new byte[]{1, 2, (byte) 0xAB}).toString());
        assertEquals("0x", new CellValues.BinaryValue(new byte[0]).toString());
    }

    @Test
    void decimalsNeverUseExponent() {
        assertEquals("0.0000001", new CellValues.PlainDecimal(new BigDecimal("1E-7")).toString());
        assertEquals("1234.50", new CellValues.PlainDecimal(new BigDecimal("1234.50")).toString());
        assertTrue(new CellValues.PlainDecimal(new BigDecimal("2")) instanceof Number);
    }

    @Test
    void dateTimesLikeSsms() {
        assertEquals("2024-01-02 10:11:12", CellValues.formatDateTime(LocalDateTime.of(2024, 1, 2, 10, 11, 12)));
        assertEquals("2024-01-02 10:11:12.123", CellValues.formatDateTime(LocalDateTime.of(2024, 1, 2, 10, 11, 12, 123_000_000)));
        assertEquals("2024-01-02 10:11:12.1234567", CellValues.formatDateTime(LocalDateTime.of(2024, 1, 2, 10, 11, 12, 123_456_700)));
        assertEquals("10:11:12.500", CellValues.formatTime(LocalTime.of(10, 11, 12, 500_000_000)));
    }
}
