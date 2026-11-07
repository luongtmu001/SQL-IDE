package app.framework.query;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** D3: splitting and stop-on-error, on an in-memory SQLite database (no server needed). */
class ScriptRunnerTest {

    @Test
    void sqlServerSplitsOnlyAtGoLines() {
        String sql = "DECLARE @x INT = 1;\nSELECT @x;\nGO\n-- only a comment\nGO\nselect 2\n  go  \nSELECT 'GO' AS txt";
        List<ScriptRunner.Piece> p = ScriptRunner.pieces(sql, ScriptRunner.Dialect.SQLSERVER);
        assertEquals(3, p.size(), p.toString());
        assertTrue(p.get(0).sql().contains("DECLARE") && p.get(0).sql().contains("SELECT @x"), "a batch is not split at ';'");
        assertEquals(1, p.get(0).startLine());
        assertEquals("select 2", p.get(1).sql());
        assertEquals(6, p.get(1).startLine());
        assertEquals("SELECT 'GO' AS txt", p.get(2).sql());
    }

    @Test
    void otherDialectsSplitPerStatement() {
        List<ScriptRunner.Piece> p = ScriptRunner.pieces("SELECT 1;\n/* c */;\nSELECT ';' AS s;\n\nSELECT 3", ScriptRunner.Dialect.POSTGRESQL);
        assertEquals(3, p.size(), p.toString());
        assertEquals(3, p.get(1).startLine());
        assertEquals(5, p.get(2).startLine());
    }

    @Test
    void stopOnErrorStopsAtTheFirstFailure() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            String sql = "SELECT 1 AS a; SELECT * FROM no_such_table; SELECT 2 AS b;";
            var stop = ScriptRunner.run(c, sql, ScriptRunner.Dialect.SQLITE, new QueryOptions(100, 0, true), null);
            assertEquals(1, stop.results().size());
            assertEquals(1, stop.errorCount());
            assertEquals(2, stop.piecesRun());
            assertEquals(3, stop.piecesTotal());
            assertTrue(stop.firstError().contains("dòng 1"), stop.firstError());

            var go = ScriptRunner.run(c, sql, ScriptRunner.Dialect.SQLITE, new QueryOptions(100, 0, false), null);
            assertEquals(2, go.results().size());
            assertEquals(1, go.errorCount());
            assertEquals(3, go.piecesRun());
            assertTrue(go.messages().stream().anyMatch(m -> m.kind() == ScriptRunner.Message.Kind.ERROR));
        }
    }

    @Test
    void cancelledHandleRunsNothing() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ScriptRunner.Handle h = new ScriptRunner.Handle();
            h.cancel();
            var out = ScriptRunner.run(c, "SELECT 1;", ScriptRunner.Dialect.SQLITE, QueryOptions.legacy(), h);
            assertTrue(out.cancelled());
            assertEquals(0, out.piecesRun());
        }
    }
}
