package app.framework.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlStatementSplitterTest {

    private static List<String> texts(String sql) {
        return SqlStatementSplitter.split(sql).stream().map(SqlStatementSplitter.Part::text).toList();
    }

    @Test
    void splitsOnSemicolon() {
        assertEquals(List.of("SELECT 1;", "SELECT 2;"), texts("SELECT 1; SELECT 2;"));
    }

    @Test
    void lastStatementWithoutSemicolonIsKept() {
        assertEquals(List.of("SELECT 1;", "SELECT 2"), texts("SELECT 1;\nSELECT 2"));
    }

    @Test
    void ignoresSeparatorsInStringsQuotedIdentifiersAndComments() {
        assertEquals(1, texts("SELECT 'a;b';").size());
        assertEquals(1, texts("SELECT \"a;b\" FROM t;").size());
        assertEquals(1, texts("SELECT [a;b] FROM t;").size());
        assertEquals(1, texts("SELECT 1 -- ; here\n;").size());
        assertEquals(1, texts("SELECT 1 /* ; /* nested ; */ ; */;").size());
    }

    @Test
    void ignoresSeparatorsInDollarQuotedBody() {
        assertEquals(1, texts("DO $$ BEGIN PERFORM 1; PERFORM 2; END $$;").size());
        assertEquals(1, texts("DO $f$ BEGIN PERFORM 1; END $f$;").size());
    }

    @Test
    void goLineSeparatesBatches() {
        assertEquals(List.of("SELECT 1", "SELECT 2"), texts("SELECT 1\nGO\nSELECT 2\ngo 3\n"));
        assertEquals(1, texts("SELECT 1 AS go").size());
    }

    @Test
    void commentOnlyPartsAreDropped() {
        // a leading comment stays with its statement; a trailing comment-only part is dropped
        assertEquals(List.of("-- header\nSELECT 1;"), texts("-- header\nSELECT 1;\n-- trailing\n"));
        assertTrue(texts("   \n ;; ").isEmpty());
        assertTrue(texts(null).isEmpty());
    }

    @Test
    void atCursorPicksStatementUnderCaret() {
        String sql = "SELECT 1;\nSELECT 2;\nSELECT 3;";
        assertEquals("SELECT 1;", SqlStatementSplitter.atCursor(sql, 1, 3).orElseThrow().text());
        assertEquals("SELECT 2;", SqlStatementSplitter.atCursor(sql, 2, 5).orElseThrow().text());
        assertEquals("SELECT 3;", SqlStatementSplitter.atCursor(sql, 3, 1).orElseThrow().text());
    }

    @Test
    void atCursorInBlankSpacePicksPreviousStatement() {
        String sql = "SELECT 1;\n\n\nSELECT 2;";
        assertEquals("SELECT 1;", SqlStatementSplitter.atCursor(sql, 2, 1).orElseThrow().text());
    }

    @Test
    void multiLineStatementReportsLines() {
        var parts = SqlStatementSplitter.split("SELECT *\nFROM t\nWHERE a = 1;\nSELECT 2;");
        assertEquals(1, parts.get(0).startLine());
        assertEquals(3, parts.get(0).endLine());
        assertEquals(4, parts.get(1).startLine());
    }
}
