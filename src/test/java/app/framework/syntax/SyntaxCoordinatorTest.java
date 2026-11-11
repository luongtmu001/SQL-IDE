package app.framework.syntax;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxCoordinatorTest {

    @Test
    void testCacheHitWithinTtl() {
        SyntaxCoordinator coordinator = SyntaxCoordinator.getInstance();
        coordinator.invalidateCache(null);

        String sql = "SELECT * FROM dbo.Orders;";
        List<SyntaxDiagnostic> d1 = coordinator.validate(
                sql, "sqlserver", null, "conn-test-1", "testdb", "dbo", false, true, null);
        assertFalse(d1.isEmpty());
        assertEquals(1, coordinator.getCacheSize());

        // Second call with same parameters should hit cache
        List<SyntaxDiagnostic> d2 = coordinator.validate(
                sql, "sqlserver", null, "conn-test-1", "testdb", "dbo", false, true, null);
        assertEquals(d1.size(), d2.size());
        assertEquals(1, coordinator.getCacheSize());
    }

    @Test
    void testCacheInvalidation() {
        SyntaxCoordinator coordinator = SyntaxCoordinator.getInstance();
        coordinator.invalidateCache(null);

        String sql = "SELECT * FROM dbo.Orders;";
        coordinator.validate(sql, "sqlserver", null, "conn-test-2", "testdb", "dbo", false, true, null);
        assertEquals(1, coordinator.getCacheSize());

        // Invalidate specific connection
        coordinator.invalidateCache("conn-test-2");
        assertEquals(0, coordinator.getCacheSize());
    }

    @Test
    void testDeduplication() {
        SyntaxDiagnostic d1 = new SyntaxDiagnostic(
                SyntaxDiagnostic.Severity.ERROR, "ERR1", "s1", "col", 1, 5, 1, 8, "Error 1");
        SyntaxDiagnostic d2 = new SyntaxDiagnostic(
                SyntaxDiagnostic.Severity.ERROR, "ERR2", "s2", "col", 1, 5, 1, 8, "Error 2");
        SyntaxDiagnostic d3 = new SyntaxDiagnostic(
                SyntaxDiagnostic.Severity.WARNING, "WARN1", "s3", "other", 2, 1, 2, 5, "Warn");

        List<SyntaxDiagnostic> deduplicated = SyntaxCoordinator.deduplicateDiagnostics(List.of(d1, d2, d3));
        assertEquals(2, deduplicated.size());
        assertEquals("col", deduplicated.get(0).token());
        assertEquals("other", deduplicated.get(1).token());
    }

    @Test
    void testOfflineDisconnectedValidationDoesNotPerformDeepSemanticChecks() {
        SyntaxCoordinator coordinator = SyntaxCoordinator.getInstance();
        coordinator.invalidateCache(null);

        // When disconnected (connection == null), unknown/missing column should NOT be flagged as an error
        String sql = "SELECT non_existing_col FROM dbo.Orders;";
        List<SyntaxDiagnostic> diags = coordinator.validate(
                sql, "sqlserver", null, null, null, null, false, false, null);
        assertTrue(diags.isEmpty(), "Disconnected query must not flag missing columns: " + diags);

        // Alias reference without connection must not be flagged as unresolvable
        String aliasSql = "SELECT o.non_existing_col FROM dbo.Orders o;";
        List<SyntaxDiagnostic> aliasDiags = coordinator.validate(
                aliasSql, "sqlserver", null, null, null, null, false, false, null);
        assertTrue(aliasDiags.isEmpty(), "Disconnected query must not flag alias/column errors: " + aliasDiags);

        // Normal structural syntax errors (unclosed quote, unbalanced parentheses, etc.) MUST still be caught
        String badSql = "SELECT col FROM dbo.Orders WHERE name = 'John";
        List<SyntaxDiagnostic> structural = coordinator.validate(
                badSql, "sqlserver", null, null, null, null, false, false, null);
        assertFalse(structural.isEmpty(), "Disconnected query must still catch structural syntax errors");
        assertEquals("UNCLOSED_STRING", structural.get(0).code());
    }

    @Test
    void testOfflineDisconnectedPinpointsFormTypoAsError() {
        SyntaxCoordinator coordinator = SyntaxCoordinator.getInstance();
        coordinator.invalidateCache(null);

        String sql = "-- Local / Bravo10Setup / dbo\nSELECT * FORM";
        List<SyntaxDiagnostic> diags = coordinator.validate(
                sql, "sqlserver", null, null, null, null, false, true, null);

        assertFalse(diags.isEmpty(), "Should report diagnostics for SELECT * FORM");

        // 1. Must pinpoint FORM as an ERROR at line 2, col 10
        SyntaxDiagnostic formErr = diags.stream()
                .filter(d -> "FORM".equals(d.token()) && d.severity() == SyntaxDiagnostic.Severity.ERROR)
                .findFirst()
                .orElse(null);
        assertNotNull(formErr, "Must flag 'FORM' as an ERROR: " + diags);
        assertEquals(2, formErr.line());
        assertEquals(10, formErr.column());
        assertEquals(14, formErr.endColumn());

        // 2. BP001 must only flag '*' at line 2, col 8, NOT the entire line
        SyntaxDiagnostic bp001 = diags.stream()
                .filter(d -> "BP001".equals(d.code()))
                .findFirst()
                .orElse(null);
        assertNotNull(bp001, "BP001 warning must be present for SELECT *");
        assertEquals(2, bp001.line());
        assertEquals(8, bp001.column());
        assertEquals(9, bp001.endColumn());
        assertEquals("*", bp001.token());
    }
}

