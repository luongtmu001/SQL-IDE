package app.framework.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 8 read-only guard (port of LLTool sql_safety.py). */
class SqlStatementClassifierTest {

    private static boolean ro(String sql) {
        return SqlStatementClassifier.classify(sql).readOnly();
    }

    @Test
    void readsAreReadOnly() {
        assertTrue(ro("SELECT * FROM t"));
        assertTrue(ro("with x as (select 1) select * from x"));
        assertTrue(ro("SELECT 'delete from x' AS s -- drop table\n FROM t"));
        assertTrue(ro("select [update], \"delete\" from t"));
        assertTrue(ro("SELECT REPLACE(name,'a','b') FROM t"));
        assertTrue(ro("SELECT update_date, deleted FROM t"));
        assertTrue(ro("DECLARE @x int; SET @x = 1; SELECT @x"));
        assertTrue(ro("EXPLAIN SELECT 1"));
    }

    @Test
    void writesAreBlocked() {
        assertFalse(ro("DELETE FROM t"));
        assertFalse(ro("insert into t select * from s"));
        assertFalse(ro("select * into #tmp from t"));
        assertFalse(ro("CREATE OR REPLACE VIEW v AS SELECT 1"));
        assertFalse(ro("EXEC sp_who"));
        assertFalse(ro("EXPLAIN ANALYZE DELETE FROM t"));
        assertFalse(ro("WITH d AS (DELETE FROM t RETURNING *) SELECT * FROM d"));
        assertFalse(ro("DO $$ BEGIN DELETE FROM t; END $$"));
        assertFalse(ro("SET TRANSACTION READ WRITE"));
        assertFalse(ro("SET default_transaction_read_only = off"));
        assertFalse(ro("SELECT * FROM t FOR UPDATE"));
        assertFalse(ro("TRUNCATE TABLE t"));
        assertFalse(ro("GRANT SELECT ON t TO u"));
    }

    @Test
    void readOnlyAndProdDecisions() {
        var blocked = SqlStatementClassifier.check("UPDATE t SET a = 1", true, "DEV", false);
        assertFalse(blocked.allowed());
        assertEquals("ERR_READ_ONLY", blocked.errorCode());

        var ask = SqlStatementClassifier.check("UPDATE t SET a = 1", false, "PROD", false);
        assertFalse(ask.allowed());
        assertTrue(ask.needsConfirmation());

        assertTrue(SqlStatementClassifier.check("UPDATE t SET a = 1", false, "PROD", true).allowed());
        assertTrue(SqlStatementClassifier.check("SELECT 1", true, "PROD", false).allowed());
    }
}
