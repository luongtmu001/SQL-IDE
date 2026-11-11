package app.framework.syntax;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DiagnosticNormalizerTest {

    @Test
    void testCleanSqlServerMessage() {
        String raw = "[Microsoft][ODBC Driver 18 for SQL Server][SQL Server]Msg 102, Level 15, State 1, Line 3: Incorrect syntax near 'FORM';";
        String clean = DiagnosticNormalizer.cleanMessage(raw);
        assertEquals("Incorrect syntax near 'FORM'", clean);
    }

    @Test
    void testCleanPostgresMessage() {
        String raw = "org.postgresql.util.PSQLException: ERROR: syntax error at or near \"FROM\"";
        String clean = DiagnosticNormalizer.cleanMessage(raw);
        assertEquals("ERROR: syntax error at or near \"FROM\"", clean);
    }

    @Test
    void testNormalizeTokenCoordinates() {
        String sql = "SELECT non_existing_col FROM dbo.Orders;";
        SyntaxDiagnostic raw = new SyntaxDiagnostic(
                SyntaxDiagnostic.Severity.ERROR,
                "SQL_SYNTAX_ERROR",
                "sql",
                "non_existing_col",
                1,
                1,
                1,
                1,
                "Invalid column"
        );
        SyntaxDiagnostic norm = DiagnosticNormalizer.normalize(raw, sql);
        assertNotNull(norm);
        assertEquals(1, norm.line());
        assertEquals(8, norm.column()); // Auto-aligned to index of "non_existing_col"
        assertEquals(24, norm.endColumn());
    }
}

