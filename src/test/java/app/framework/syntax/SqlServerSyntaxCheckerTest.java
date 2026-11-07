package app.framework.syntax;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlServerSyntaxCheckerTest {

    @Test
    void tokenOnSecondLine() {
        String sql = "SELECT a\nFORM t";
        SyntaxDiagnostic d = SqlServerSyntaxChecker.diagnosticFor("Incorrect syntax near 'FORM'.", sql, 5, 2);
        assertEquals(6, d.line());
        assertEquals(1, d.column());
        assertEquals(6, d.endLine());
        assertEquals(5, d.endColumn());
    }

    @Test
    void firstLineUsesColumnOffset() {
        String sql = "SELEC 1";
        SyntaxDiagnostic d = SqlServerSyntaxChecker.diagnosticFor("Incorrect syntax near '1'.", sql, 3, 4);
        assertEquals(3, d.line());
        assertEquals(11, d.column());
    }

    @Test
    void keywordMessageAndWholeWord() {
        String sql = "SELECT fromage FROM";
        SyntaxDiagnostic d = SqlServerSyntaxChecker.diagnosticFor("Incorrect syntax near the keyword 'FROM'.", sql, 1, 0);
        assertEquals(16, d.column());   // not inside "fromage"
    }

    @Test
    void noTokenMarksFirstLine() {
        SyntaxDiagnostic d = SqlServerSyntaxChecker.diagnosticFor("Incorrect syntax near the keyword.", "SELECT 1\nx", 2, 0);
        assertEquals(2, d.line());
        assertEquals(1, d.column());
    }

    @Test
    void escapedQuoteInToken() {
        SyntaxDiagnostic d = SqlServerSyntaxChecker.diagnosticFor("Unclosed quotation mark after the character string 'it''s'.", "SELECT 'it's", 1, 0);
        assertEquals(1, d.line());
    }
}
