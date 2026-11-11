package app.framework.syntax;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresSyntaxCheckerTest {

    @Test
    void testDiagnosticWithPositionOnLine2() {
        // EXPLAIN (VERBOSE) is 18 chars long.
        // Piece:
        // "SELECT a\nFORM t"
        // In full query:
        // "EXPLAIN (VERBOSE) SELECT a\nFORM t"
        // Offset of "FORM":
        // "EXPLAIN (VERBOSE) SELECT a\n" = 18 + 8 + 1 = 27 chars.
        // 1-based pos = 28.
        String piece = "SELECT a\nFORM t";
        String msg = "ERROR: syntax error at or near \"FORM\"\n  Position: 28";
        SQLException ex = new SQLException(msg, "42601");

        SyntaxDiagnostic d = PostgresSyntaxChecker.diagnosticFor(ex, piece, 1);
        assertNotNull(d);
        assertEquals(2, d.line());
        assertEquals(1, d.column());
        assertEquals(2, d.endLine());
        assertEquals(5, d.endColumn());
        assertTrue(d.message().contains("syntax error at or near \"FORM\""));
    }

    @Test
    void testDiagnosticWithStartLineOffset() {
        String piece = "FORM t";
        String msg = "ERROR: syntax error at or near \"FORM\"\n  Position: 19";
        SQLException ex = new SQLException(msg, "42601");

        // Statement started on line 10
        SyntaxDiagnostic d = PostgresSyntaxChecker.diagnosticFor(ex, piece, 10);
        assertNotNull(d);
        assertEquals(10, d.line());
        assertEquals(1, d.column());
        assertEquals(10, d.endLine());
        assertEquals(5, d.endColumn());
    }

    @Test
    void testDiagnosticFallbackToTokenMatching() {
        String piece = "SELECT * FORM users";
        String msg = "ERROR: syntax error at or near \"FORM\"";
        SQLException ex = new SQLException(msg, "42601");

        SyntaxDiagnostic d = PostgresSyntaxChecker.diagnosticFor(ex, piece, 3);
        assertNotNull(d);
        assertEquals(3, d.line());
        assertEquals(10, d.column());
        assertEquals(3, d.endLine());
        assertEquals(14, d.endColumn());
    }

    @Test
    void testConnectionErrorDetection() {
        SQLException connErr = new SQLException("Connection closed", "08006");
        assertTrue(PostgresSyntaxChecker.isConnectionError(connErr));

        SQLException termErr = new SQLException("Terminated", "57P01");
        assertTrue(PostgresSyntaxChecker.isConnectionError(termErr));

        SQLException syntaxErr = new SQLException("Syntax error", "42601");
        assertTrue(!PostgresSyntaxChecker.isConnectionError(syntaxErr));
    }
}

