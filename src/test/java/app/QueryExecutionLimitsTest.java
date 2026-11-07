package app;

import app.framework.query.DesktopQueryExecutionService;
import app.framework.query.QueryOptions;
import app.models.QueryResult;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 8 / D3: row limit and truncation message on the mock SQLite sandbox. */
class QueryExecutionLimitsTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ignored) {
            latch.countDown();
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {}
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        app.framework.bootstrap.DesktopBootstrap.getInstance();
    }

    @Test
    void rowLimitCutsResultAndReportsIt() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<QueryResult> res = new AtomicReference<>();
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());

        DesktopQueryExecutionService.getInstance().execute(null,
                "WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x < 50) SELECT x FROM n;",
                null, null, null, false, new QueryOptions(10, 0),
                new DesktopQueryExecutionService.QueryCallback() {
                    @Override public void onStart(String sql, String connectionName) {}
                    @Override public void onMessage(String m, MessageKind kind) {
                        if (kind == MessageKind.WARNING) warnings.add(m);
                    }
                    @Override public void onSuccess(QueryResult r, int rowsAffected, String summary, long ms) {
                        res.set(r);
                        latch.countDown();
                    }
                    @Override public void onError(String m, Throwable c) { latch.countDown(); }
                    @Override public void onCancelled() { latch.countDown(); }
                });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(res.get());
        assertEquals(10, res.get().getRows().size());
        assertEquals(1, warnings.size(), "a truncation warning is expected");
        assertTrue(warnings.get(0).contains("10"));
    }

    @Test
    void resultUnderTheLimitHasNoWarning() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<QueryResult> res = new AtomicReference<>();

        DesktopQueryExecutionService.getInstance().execute(null, "SELECT 1 AS a;",
                null, null, null, false, new QueryOptions(10, 0),
                new DesktopQueryExecutionService.QueryCallback() {
                    @Override public void onStart(String sql, String connectionName) {}
                    @Override public void onMessage(String m, MessageKind kind) {
                        if (kind != MessageKind.INFO) warnings.add(m); // "(n row(s) returned)" is INFO
                    }
                    @Override public void onSuccess(QueryResult r, int rowsAffected, String summary, long ms) {
                        res.set(r);
                        latch.countDown();
                    }
                    @Override public void onError(String m, Throwable c) { latch.countDown(); }
                    @Override public void onCancelled() { latch.countDown(); }
                });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(1, res.get().getRows().size());
        assertTrue(warnings.isEmpty());
    }
}
