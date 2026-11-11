package app.framework.syntax;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BestPracticeRulesTest {

    @Test
    void testBp001SelectStar() {
        String sql = "SELECT * FROM dbo.Orders;";
        List<SyntaxDiagnostic> diags = BestPracticeRules.check(sql, "sqlserver");
        assertEquals(1, diags.size());
        assertEquals("BP001", diags.get(0).code());
        assertEquals(SyntaxDiagnostic.Severity.WARNING, diags.get(0).severity());
    }

    @Test
    void testBp002NolockHint() {
        String sql = "SELECT OrderID FROM dbo.Orders WITH (NOLOCK);";
        List<SyntaxDiagnostic> diags = BestPracticeRules.check(sql, "sqlserver");
        assertEquals(1, diags.size());
        assertEquals("BP002", diags.get(0).code());
        assertEquals(SyntaxDiagnostic.Severity.WARNING, diags.get(0).severity());
    }

    @Test
    void testBp004TopWithoutOrderBy() {
        String sql = "SELECT TOP 10 OrderID FROM dbo.Orders;";
        List<SyntaxDiagnostic> diags = BestPracticeRules.check(sql, "sqlserver");
        assertEquals(1, diags.size());
        assertEquals("BP004", diags.get(0).code());
    }

    @Test
    void testBp004TopWithOrderByPasses() {
        String sql = "SELECT TOP 10 OrderID FROM dbo.Orders ORDER BY OrderID DESC;";
        List<SyntaxDiagnostic> diags = BestPracticeRules.check(sql, "sqlserver");
        assertTrue(diags.stream().noneMatch(d -> "BP004".equals(d.code())));
    }

    @Test
    void testMaskingCommentsAndStrings() {
        String sql = "-- SELECT * FROM dbo.Orders WITH (NOLOCK)\nSELECT 'SELECT *' AS txt FROM dbo.Orders;";
        List<SyntaxDiagnostic> diags = BestPracticeRules.check(sql, "sqlserver");
        assertTrue(diags.isEmpty(), "Rules inside comments or strings must not trigger: " + diags);
    }
}

