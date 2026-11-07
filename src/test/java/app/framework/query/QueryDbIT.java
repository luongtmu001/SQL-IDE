package app.framework.query;

import app.models.QueryResult;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D3 against REAL servers (SQL Server localhost:1433, PostgreSQL localhost:5432, database Bravo10Setup).
 * Not part of the normal test run (the name does not end in "Test"): run-db-tests.cmd runs it.
 * Connection settings come from %APPDATA%\LLTool-Desktop\it-db.properties (outside the repository).
 * Only read statements are sent (SELECT, WAITFOR, pg_sleep, PRINT, RAISERROR, RAISE NOTICE): nothing is written.
 * Every check also writes a line to target/db-test-report.txt.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryDbIT {

    private static final Path REPORT = Path.of("target", "db-test-report.txt");
    private static Properties props;

    @BeforeAll
    static void setUp() throws Exception {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, "=== DB tests " + LocalDateTime.now() + System.lineSeparator());
        Path cfg = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "LLTool-Desktop", "it-db.properties");
        props = new Properties();
        if (Files.exists(cfg)) {
            try (InputStream in = Files.newInputStream(cfg)) {
                props.load(in);
            }
        }
        report("config " + cfg + (Files.exists(cfg) ? " found" : " MISSING"));
        CountDownLatch fx = new CountDownLatch(1);
        try {
            Platform.startup(fx::countDown);
        } catch (IllegalStateException e) {
            fx.countDown();
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {
        }
        assertTrue(fx.await(5, TimeUnit.SECONDS));
    }

    @AfterAll
    static void tearDown() {
        DesktopQueryExecutionService.setTestConnectionProvider(null);
    }

    static synchronized void report(String line) {
        try {
            Files.writeString(REPORT, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static Connection mssql() throws SQLException {
        String url = props.getProperty("mssql.url");
        Assumptions.assumeTrue(url != null, "mssql.url not configured");
        String user = props.getProperty("mssql.user");
        return user == null || user.isBlank() ? DriverManager.getConnection(url)
                : DriverManager.getConnection(url, user, props.getProperty("mssql.password", ""));
    }

    private static Connection pg() throws SQLException {
        String url = props.getProperty("pg.url");
        Assumptions.assumeTrue(url != null, "pg.url not configured");
        Connection c = DriverManager.getConnection(url, props.getProperty("pg.user"), props.getProperty("pg.password"));
        // same as the app for read-only profiles: the server refuses any write
        try (Statement st = c.createStatement()) {
            st.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY");
        }
        return c;
    }

    private static String texts(ScriptRunner.Outcome o) {
        List<String> t = new ArrayList<>();
        for (ScriptRunner.Message m : o.messages()) t.add(m.kind() + ":" + m.text());
        return t.toString();
    }

    // ------------------------------------------------------------------ connections

    @Test
    @Order(1)
    void connectBoth() throws Exception {
        try (Connection a = mssql()) {
            var o = ScriptRunner.run(a, "SELECT DB_NAME() AS db, @@SPID AS spid", ScriptRunner.Dialect.SQLSERVER, QueryOptions.legacy(), null);
            report("SQL Server connected: db=" + o.results().get(0).getRows().get(0).getValues());
        } catch (SQLException e) {
            report("SQL Server CONNECT FAILED: " + e.getMessage());
            throw e;
        }
        try (Connection b = pg()) {
            var o = ScriptRunner.run(b, "SELECT current_database(), pg_backend_pid()", ScriptRunner.Dialect.POSTGRESQL, QueryOptions.legacy(), null);
            report("PostgreSQL connected: " + o.results().get(0).getRows().get(0).getValues());
        } catch (SQLException e) {
            report("PostgreSQL CONNECT FAILED: " + e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------ batches, messages, stop on error

    @Test
    @Order(2)
    void sqlServerGoBatchesAndPrint() throws Exception {
        try (Connection c = mssql()) {
            String sql = "DECLARE @x INT = 41;\nPRINT N'xin chào từ PRINT';\nSELECT @x + 1 AS answer;\nGO\nSELECT 2 AS b; SELECT 3 AS c;\nGO";
            var o = ScriptRunner.run(c, sql, ScriptRunner.Dialect.SQLSERVER, QueryOptions.legacy(), null);
            report("mssql GO batches: results=" + o.results().size() + " errors=" + o.errorCount() + " msgs=" + texts(o));
            assertEquals(0, o.errorCount(), texts(o));
            assertEquals(3, o.results().size());
            assertEquals(42, ((Number) o.results().get(0).getRows().get(0).getValues().get(0)).intValue());
            assertTrue(o.messages().stream().anyMatch(m -> m.text().contains("xin chào từ PRINT")), texts(o));
        }
    }

    @Test
    @Order(3)
    void sqlServerStopOnError() throws Exception {
        String sql = "SELECT 1 AS a\nGO\nRAISERROR(N'lỗi thử', 16, 1)\nGO\nSELECT 2 AS b\nGO";
        try (Connection c = mssql()) {
            var stop = ScriptRunner.run(c, sql, ScriptRunner.Dialect.SQLSERVER, new QueryOptions(100, 0, true), null);
            var go = ScriptRunner.run(c, sql, ScriptRunner.Dialect.SQLSERVER, new QueryOptions(100, 0, false), null);
            report("mssql stopOnError=true: results=" + stop.results().size() + " ran=" + stop.piecesRun() + "/" + stop.piecesTotal()
                    + " firstError=" + stop.firstError());
            report("mssql stopOnError=false: results=" + go.results().size() + " ran=" + go.piecesRun() + "/" + go.piecesTotal());
            assertEquals(1, stop.results().size());
            assertEquals(1, stop.errorCount());
            assertTrue(stop.firstError().contains("lỗi thử") && stop.firstError().contains("dòng 3"), stop.firstError());
            assertEquals(2, go.results().size());
            assertEquals(1, go.errorCount());
        }
    }

    @Test
    @Order(4)
    void postgresStatementsNoticeAndStopOnError() throws Exception {
        String sql = "DO $$ BEGIN RAISE NOTICE 'xin chao tu NOTICE'; END $$;\nSELECT 1 AS a;\nSELECT 1/0;\nSELECT 2 AS b;";
        try (Connection c = pg()) {
            var stop = ScriptRunner.run(c, sql, ScriptRunner.Dialect.POSTGRESQL, new QueryOptions(100, 0, true), null);
            var go = ScriptRunner.run(c, sql, ScriptRunner.Dialect.POSTGRESQL, new QueryOptions(100, 0, false), null);
            report("pg stopOnError=true: results=" + stop.results().size() + " ran=" + stop.piecesRun() + "/" + stop.piecesTotal()
                    + " msgs=" + texts(stop));
            report("pg stopOnError=false: results=" + go.results().size() + " errors=" + go.errorCount());
            assertTrue(stop.messages().stream().anyMatch(m -> m.text().contains("xin chao tu NOTICE")), texts(stop));
            assertEquals(1, stop.results().size());
            assertEquals(1, stop.errorCount());
            assertTrue(stop.firstError().contains("dòng 3"), stop.firstError());
            assertEquals(2, go.results().size());
        }
    }

    @Test
    @Order(5)
    void postgresServerRefusesWritesOnReadOnlySession() throws Exception {
        try (Connection c = pg()) {
            var o = ScriptRunner.run(c, "CREATE TEMP TABLE claude_ro_probe(id int);", ScriptRunner.Dialect.POSTGRESQL,
                    QueryOptions.legacy(), null);
            report("pg read-only probe: errors=" + o.errorCount() + " " + o.firstError());
            assertEquals(1, o.errorCount(), "the read-only session must refuse CREATE");
            assertTrue(o.firstError().toLowerCase().contains("read-only"), o.firstError());
        }
        var blocked = SqlStatementClassifier.check("DELETE FROM some_table", true, "DEV", false);
        report("classifier DELETE on read-only: allowed=" + blocked.allowed() + " code=" + blocked.errorCode());
        assertFalse(blocked.allowed());
    }

    // ------------------------------------------------------------------ cancel / timeout

    private static ScriptRunner.Outcome runAndCancel(Connection c, String sql, ScriptRunner.Dialect d, long cancelAfterMs,
                                                     long[] elapsed) throws Exception {
        ScriptRunner.Handle h = new ScriptRunner.Handle();
        AtomicReference<ScriptRunner.Outcome> out = new AtomicReference<>();
        long t0 = System.currentTimeMillis();
        Thread t = Thread.ofVirtual().start(() -> out.set(ScriptRunner.run(c, sql, d, new QueryOptions(100, 0, true), h)));
        Thread.sleep(cancelAfterMs);
        h.cancel();
        t.join(15_000);
        elapsed[0] = System.currentTimeMillis() - t0;
        return out.get();
    }

    @Test
    @Order(6)
    void cancelStopsLongStatements() throws Exception {
        long[] ms = new long[1];
        try (Connection c = mssql()) {
            var o = runAndCancel(c, "WAITFOR DELAY '00:00:20'; SELECT 1 AS never", ScriptRunner.Dialect.SQLSERVER, 1500, ms);
            report("mssql cancel after 1.5s: finished in " + ms[0] + " ms cancelled=" + (o != null && o.cancelled()));
            assertNotNull(o);
            assertTrue(o.cancelled());
            assertTrue(ms[0] < 8000, "cancel took " + ms[0] + " ms");
            var again = ScriptRunner.run(c, "SELECT 5 AS ok", ScriptRunner.Dialect.SQLSERVER, QueryOptions.legacy(), null);
            assertEquals(1, again.results().size(), "connection usable after cancel: " + texts(again));
        }
        try (Connection c = pg()) {
            var o = runAndCancel(c, "SELECT pg_sleep(20);", ScriptRunner.Dialect.POSTGRESQL, 1500, ms);
            report("pg cancel after 1.5s: finished in " + ms[0] + " ms cancelled=" + (o != null && o.cancelled()));
            assertNotNull(o);
            assertTrue(o.cancelled());
            assertTrue(ms[0] < 8000, "cancel took " + ms[0] + " ms");
            var again = ScriptRunner.run(c, "SELECT 5 AS ok;", ScriptRunner.Dialect.POSTGRESQL, QueryOptions.legacy(), null);
            assertEquals(1, again.results().size(), "connection usable after cancel: " + texts(again));
        }
    }

    @Test
    @Order(7)
    void timeoutStopsLongStatements() throws Exception {
        try (Connection c = mssql()) {
            long t0 = System.currentTimeMillis();
            var o = ScriptRunner.run(c, "WAITFOR DELAY '00:00:15'", ScriptRunner.Dialect.SQLSERVER, new QueryOptions(100, 2, true), null);
            long ms = System.currentTimeMillis() - t0;
            report("mssql timeout 2s: timedOut=" + o.timedOut() + " in " + ms + " ms " + texts(o));
            assertTrue(o.timedOut(), texts(o));
            assertTrue(ms < 8000);
        }
        try (Connection c = pg()) {
            long t0 = System.currentTimeMillis();
            var o = ScriptRunner.run(c, "SELECT pg_sleep(15);", ScriptRunner.Dialect.POSTGRESQL, new QueryOptions(100, 2, true), null);
            long ms = System.currentTimeMillis() - t0;
            report("pg timeout 2s: timedOut=" + o.timedOut() + " in " + ms + " ms " + texts(o));
            assertTrue(o.timedOut(), texts(o));
            assertTrue(ms < 8000);
        }
    }

    // ------------------------------------------------------------------ tabs in parallel (execution service)

    /** Result of one tab run through DesktopQueryExecutionService. */
    private static final class TabRun implements DesktopQueryExecutionService.QueryCallback {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> state = new AtomicReference<>("running");
        final List<QueryResult> results = Collections.synchronizedList(new ArrayList<>());
        volatile long finishedAt;

        @Override public void onStart(String sql, String connectionName) { }
        @Override public void onSuccess(QueryResult r, List<QueryResult> all, int rows, String summary, long ms) {
            results.addAll(all);
            end("success");
        }
        @Override public void onError(String msg, Throwable cause) { end("error: " + msg); }
        @Override public void onCancelled() { end("cancelled"); }

        private void end(String s) {
            state.set(s);
            finishedAt = System.currentTimeMillis();
            done.countDown();
        }
    }

    private static void runOnFx(Runnable r) throws InterruptedException {
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } finally {
                l.countDown();
            }
        });
        assertTrue(l.await(5, TimeUnit.SECONDS));
    }

    @Test
    @Order(8)
    void tabsRunInParallelAndStopIsPerTab() throws Exception {
        Map<String, Connection> conns = new ConcurrentHashMap<>();
        conns.put("tabA", mssql());
        conns.put("tabB", pg());
        conns.put("tabC", mssql());
        Map<String, String> types = Map.of("tabA", "SQLSERVER", "tabB", "POSTGRESQL", "tabC", "SQLSERVER");
        DesktopQueryExecutionService.setTestConnectionProvider((tab, conn) ->
                new DesktopQueryExecutionService.TestConnection(conns.get(tab), types.get(tab)));
        DesktopQueryExecutionService svc = DesktopQueryExecutionService.getInstance();
        try {
            // 1) A and B (3 s each) at the same time: parallel means ~3 s in total, not ~6 s
            TabRun a = new TabRun();
            TabRun b = new TabRun();
            long t0 = System.currentTimeMillis();
            runOnFx(() -> {
                svc.execute("tabA", "WAITFOR DELAY '00:00:03'; SELECT 'A' AS tab", "it-mssql", null, null, false, QueryOptions.legacy(), a);
                svc.execute("tabB", "SELECT pg_sleep(3); SELECT 'B' AS tab;", "it-pg", null, null, false, QueryOptions.legacy(), b);
            });
            assertTrue(a.done.await(20, TimeUnit.SECONDS) && b.done.await(20, TimeUnit.SECONDS));
            long total = Math.max(a.finishedAt, b.finishedAt) - t0;
            report("parallel A(mssql 3s)+B(pg 3s): A=" + a.state + " B=" + b.state + " total=" + total + " ms");
            assertEquals("success", a.state.get());
            assertEquals("success", b.state.get());
            assertTrue(total < 5500, "tabs did not run in parallel: " + total + " ms");

            // 2) the same tab cannot start twice; Stop on A leaves C running
            TabRun a2 = new TabRun();
            TabRun aDup = new TabRun();
            TabRun c = new TabRun();
            runOnFx(() -> {
                svc.execute("tabA", "WAITFOR DELAY '00:00:20'; SELECT 1", "it-mssql", null, null, false, QueryOptions.legacy(), a2);
                svc.execute("tabA", "SELECT 1", "it-mssql", null, null, false, QueryOptions.legacy(), aDup);
                svc.execute("tabC", "WAITFOR DELAY '00:00:04'; SELECT 'C' AS tab", "it-mssql", null, null, false, QueryOptions.legacy(), c);
            });
            assertTrue(aDup.done.await(5, TimeUnit.SECONDS));
            report("second run on a busy tab: " + aDup.state);
            assertTrue(aDup.state.get().startsWith("error"), aDup.state.get());

            Thread.sleep(1000);
            long tc = System.currentTimeMillis();
            runOnFx(() -> svc.cancel("tabA"));
            assertTrue(a2.done.await(10, TimeUnit.SECONDS));
            report("stop tab A: A=" + a2.state + " after " + (a2.finishedAt - tc) + " ms; C running=" + svc.isExecuting("tabC"));
            assertEquals("cancelled", a2.state.get());
            assertTrue(svc.isExecuting("tabC"), "stopping tab A must not stop tab C");
            assertTrue(c.done.await(15, TimeUnit.SECONDS));
            report("tab C after A was stopped: " + c.state + " rows=" + (c.results.isEmpty() ? 0 : c.results.get(0).getRows().size()));
            assertEquals("success", c.state.get());
        } finally {
            DesktopQueryExecutionService.setTestConnectionProvider(null);
            for (Connection c : conns.values()) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
    // ------------------------------------------------------------------ D4: grid values and empty results

    private static String cell(ScriptRunner.Outcome o, String col) {
        QueryResult r = o.results().get(0);
        for (int i = 0; i < r.getColumns().size(); i++) {
            if (r.getColumns().get(i).getName().equalsIgnoreCase(col)) {
                Object v = r.getRows().get(0).getValues().get(i);
                return v == null ? null : v.toString();
            }
        }
        throw new AssertionError("no column " + col);
    }

    private static String row(ScriptRunner.Outcome o) {
        QueryResult r = o.results().get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.getColumns().size(); i++) {
            Object v = r.getRows().get(0).getValues().get(i);
            sb.append(r.getColumns().get(i).getName()).append('(').append(r.getColumns().get(i).getDataType()).append(")=")
                    .append(v == null ? "NULL" : v + " [" + v.getClass().getSimpleName() + "]").append("; ");
        }
        return sb.toString();
    }

    @Test
    @Order(9)
    void sqlServerValuesAsShownInTheGrid() throws Exception {
        String sql = "SELECT CAST(NULL AS int) AS n, CAST(0x0102AB AS varbinary(10)) AS bin, CAST(1234.50 AS decimal(10,2)) AS dec,"
                + " CAST(0.0000001 AS decimal(18,9)) AS small, CAST('2024-01-02 10:11:12.123' AS datetime) AS dt,"
                + " CAST('2024-01-02 10:11:12.1234567' AS datetime2) AS dt2, CAST('2024-01-02' AS date) AS d,"
                + " CAST('10:11:12.5' AS time) AS t, CAST('2024-01-02 10:11:12 +07:00' AS datetimeoffset) AS dto,"
                + " CAST(1 AS bit) AS b, CAST('6F9619FF-8B86-D011-B42D-00C04FC964FF' AS uniqueidentifier) AS g,"
                + " CAST(12.5 AS money) AS m, N'chữ Việt' AS s, CAST(1.5 AS float) AS f";
        try (Connection c = mssql()) {
            var o = ScriptRunner.run(c, sql, ScriptRunner.Dialect.SQLSERVER, QueryOptions.legacy(), null);
            report("mssql values: " + row(o));
            assertNull(cell(o, "n"));
            assertEquals("0x0102AB", cell(o, "bin"));
            assertEquals("1234.50", cell(o, "dec"));
            assertEquals("0.000000100", cell(o, "small"));
            assertEquals("2024-01-02 10:11:12.123", cell(o, "dt"));
            assertEquals("2024-01-02 10:11:12.1234567", cell(o, "dt2"));
            assertEquals("2024-01-02", cell(o, "d"));
            assertEquals("10:11:12.500", cell(o, "t"));
            assertTrue(cell(o, "dto").contains("+07:00"), cell(o, "dto"));
            assertEquals("true", cell(o, "b"));
            assertEquals("chữ Việt", cell(o, "s"));
            Object m = o.results().get(0).getRows().get(0).getValues().get(11);
            assertTrue(m instanceof Number, "money stays numeric: " + m);
        }
    }

    @Test
    @Order(10)
    void postgresValuesAsShownInTheGrid() throws Exception {
        String sql = "SELECT NULL::int AS n, '\\x0102ab'::bytea AS bin, 1234.50::numeric(10,2) AS dec, 0.0000001::numeric AS small,"
                + " '2024-01-02 10:11:12.123'::timestamp AS ts, '2024-01-02 10:11:12+07'::timestamptz AS tstz,"
                + " '2024-01-02'::date AS d, '1 day 2 hours'::interval AS iv, '{\"a\":1}'::jsonb AS j, ARRAY[1,2] AS arr,"
                + " true AS b, '6f9619ff-8b86-d011-b42d-00c04fc964ff'::uuid AS g, 'chữ Việt'::text AS s;";
        try (Connection c = pg()) {
            var o = ScriptRunner.run(c, sql, ScriptRunner.Dialect.POSTGRESQL, QueryOptions.legacy(), null);
            report("pg values: " + row(o) + " " + texts(o));
            assertNull(cell(o, "n"));
            assertEquals("0x0102AB", cell(o, "bin"));
            assertEquals("1234.50", cell(o, "dec"));
            assertEquals("0.0000001", cell(o, "small"));
            assertEquals("2024-01-02 10:11:12.123", cell(o, "ts"));
            assertTrue(cell(o, "tstz").startsWith("2024-01-02"), cell(o, "tstz"));
            assertEquals("2024-01-02", cell(o, "d"));
            assertEquals("1 day 02:00:00", cell(o, "iv"));
            assertEquals("{\"a\": 1}", cell(o, "j"));
            assertEquals("{1,2}", cell(o, "arr"));
            assertEquals("true", cell(o, "b"));
            assertEquals("chữ Việt", cell(o, "s"));
        }
    }

    @Test
    @Order(11)
    void emptyResultsKeepTheirColumns() throws Exception {
        try (Connection c = mssql()) {
            var o = ScriptRunner.run(c, "SELECT TOP 0 * FROM sys.objects", ScriptRunner.Dialect.SQLSERVER, QueryOptions.legacy(), null);
            QueryResult r = o.results().get(0);
            report("mssql TOP 0 sys.objects: rows=" + r.getRowCount() + " columns=" + r.getColumns().size());
            assertEquals(0, r.getRowCount());
            assertTrue(r.getColumns().size() >= 5);
        }
        try (Connection c = pg()) {
            var o = ScriptRunner.run(c, "SELECT * FROM pg_class LIMIT 0;", ScriptRunner.Dialect.POSTGRESQL, QueryOptions.legacy(), null);
            QueryResult r = o.results().get(0);
            report("pg LIMIT 0 pg_class: rows=" + r.getRowCount() + " columns=" + r.getColumns().size());
            assertEquals(0, r.getRowCount());
            assertTrue(r.getColumns().size() >= 5);
        }
    }
    // ------------------------------------------------------------------ D4: streaming

    private static String stream(Connection c, String sql, ScriptRunner.Dialect d, int expectRows) {
        List<Integer> sizes = Collections.synchronizedList(new ArrayList<>());
        long[] first = {-1};
        long t0 = System.nanoTime();
        var o = ScriptRunner.run(c, sql, d, new QueryOptions(0, 0, true), null, new ScriptRunner.RowSink() {
            @Override public void resultStarted(int index, String s, List<app.models.QueryResultColumn> cols) { }
            @Override public void rows(int index, List<app.models.QueryResultRow> batch) {
                if (first[0] < 0) first[0] = System.nanoTime() - t0;
                sizes.add(batch.size());
            }
        });
        long total = System.nanoTime() - t0;
        int sum = sizes.stream().mapToInt(Integer::intValue).sum();
        String line = String.format("%s: %,d rows in %d batches, first batch %d rows after %d ms, all after %d ms, errors=%d",
                d, sum, sizes.size(), sizes.isEmpty() ? 0 : sizes.get(0), first[0] / 1_000_000, total / 1_000_000, o.errorCount());
        report("stream " + line);
        assertEquals(0, o.errorCount(), texts(o));
        assertEquals(expectRows, sum);
        assertEquals(expectRows, o.results().get(0).getRowCount());
        assertTrue(sizes.size() > 1, "rows must arrive in several batches");
        assertTrue(sizes.get(0) <= ScriptRunner.FIRST_BATCH, "first batch " + sizes.get(0));
        return line;
    }

    @Test
    @Order(12)
    void rowsArriveInBatches() throws Exception {
        try (Connection c = mssql()) {
            stream(c, "SELECT TOP 50000 a.object_id, a.name, b.type_desc FROM sys.all_objects a CROSS JOIN sys.all_objects b",
                    ScriptRunner.Dialect.SQLSERVER, 50000);
        }
        try (Connection c = pg()) {
            stream(c, "SELECT g AS id, md5(g::text) AS txt, now() AS ts FROM generate_series(1, 50000) g;",
                    ScriptRunner.Dialect.POSTGRESQL, 50000);
            assertTrue(c.getAutoCommit(), "autocommit must be restored after a cursor read");
        }
    }
}
