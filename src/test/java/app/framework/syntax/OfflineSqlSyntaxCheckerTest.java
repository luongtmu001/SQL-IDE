package app.framework.syntax;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OfflineSqlSyntaxCheckerTest {

    @Test
    void testValidSqlPassesCleanly() {
        String sql = "SELECT u.id, u.name FROM users u WHERE u.age > 18;";
        List<SyntaxDiagnostic> d = OfflineSqlSyntaxChecker.check(sql);
        assertTrue(d.isEmpty(), "Valid SQL should yield 0 offline diagnostics");
    }

    @Test
    void testUnclosedStringLiteral() {
        String sql = "SELECT * FROM users WHERE name = 'John";
        List<SyntaxDiagnostic> d = OfflineSqlSyntaxChecker.check(sql);
        assertEquals(1, d.size());
        assertEquals(1, d.get(0).line());
        assertEquals(34, d.get(0).column());
        assertTrue(d.get(0).message().contains("Unclosed quotation mark"));
    }

    @Test
    void testEscapedQuoteDoesNotTriggerError() {
        String sql = "SELECT * FROM users WHERE name = 'O''Reilly';";
        List<SyntaxDiagnostic> d = OfflineSqlSyntaxChecker.check(sql);
        assertTrue(d.isEmpty(), "Escaped quote '' should not be flagged as unclosed");
    }

    @Test
    void testUnclosedBlockComment() {
        String sql = "SELECT 1;\n/* unclosed comment\nSELECT 2;";
        List<SyntaxDiagnostic> d = OfflineSqlSyntaxChecker.check(sql);
        assertEquals(1, d.size());
        assertEquals(2, d.get(0).line());
        assertEquals(1, d.get(0).column());
        assertTrue(d.get(0).message().contains("Unclosed block comment"));
    }

    @Test
    void testUnmatchedClosingParenthesis() {
        String sql = "SELECT (1 + 2)) FROM t;";
        List<SyntaxDiagnostic> d = OfflineSqlSyntaxChecker.check(sql);
        assertEquals(1, d.size());
        assertEquals(1, d.get(0).line());
        assertEquals(15, d.get(0).column());
        assertTrue(d.get(0).message().contains("Unmatched closing parenthesis"));
    }

    @Test
    void testUnclosedOpenParenthesis() {
        String sql = "SELECT (1 + 2 FROM t;";
        List<SyntaxDiagnostic> d = OfflineSqlSyntaxChecker.check(sql);
        assertEquals(1, d.size());
        assertEquals(1, d.get(0).line());
        assertEquals(8, d.get(0).column());
        assertTrue(d.get(0).message().contains("Unclosed parenthesis"));
    }

    @Test
    void testConsecutiveCommas() {
        String sql = "SELECT a,, b FROM t;";
        List<SyntaxDiagnostic> d = OfflineSqlSyntaxChecker.check(sql);
        assertEquals(1, d.size());
        assertEquals(1, d.get(0).line());
        assertEquals(10, d.get(0).column());
        assertTrue(d.get(0).message().contains("consecutive commas"));
    }

    @Test
    void testTrailingCommaBeforeFrom() {
        String sql = "SELECT a, b, FROM t;";
        List<SyntaxDiagnostic> d = OfflineSqlSyntaxChecker.check(sql);
        assertEquals(1, d.size());
        assertEquals(1, d.get(0).line());
        assertEquals(12, d.get(0).column());
        assertTrue(d.get(0).message().contains("before keyword 'FROM'"));
    }
}

