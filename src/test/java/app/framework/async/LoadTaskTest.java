package app.framework.async;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LoadTaskTest {

    @Test
    void testSuccessfulExecution() throws Exception {
        LoadTask<String> task = new LoadTask<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();

        task.execute(
                ctx -> "data_success",
                result -> {
                    resultRef.set(result);
                    latch.countDown();
                },
                error -> fail("Should not fail: " + error.getMessage())
        );

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals("data_success", resultRef.get());
        assertFalse(task.isRunning());
    }

    @Test
    void testErrorExecution() throws Exception {
        LoadTask<String> task = new LoadTask<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        task.execute(
                ctx -> {
                    throw new RuntimeException("DB Connection Timeout");
                },
                result -> fail("Should not succeed"),
                error -> {
                    errorRef.set(error);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals("DB Connection Timeout", errorRef.get().getMessage());
    }

    @Test
    void testCancellationCallsStatementCancel() throws Exception {
        LoadTask<String> task = new LoadTask<>();
        AtomicBoolean statementCancelled = new AtomicBoolean(false);

        Statement mockStmt = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    if ("cancel".equals(method.getName())) {
                        statementCancelled.set(true);
                        return null;
                    }
                    return null;
                }
        );

        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        AtomicBoolean cancelledCallback = new AtomicBoolean(false);

        task.execute(
                ctx -> {
                    ctx.registerStatement(mockStmt);
                    startedLatch.countDown();
                    while (!ctx.isCancelled()) {
                        Thread.sleep(20);
                    }
                    return "cancelled_result";
                },
                result -> fail("Should not succeed when cancelled"),
                error -> fail("Should not report error when cancelled"),
                () -> {
                    cancelledCallback.set(true);
                    cancelLatch.countDown();
                }
        );

        assertTrue(startedLatch.await(3, TimeUnit.SECONDS));
        task.cancel();

        assertTrue(cancelLatch.await(3, TimeUnit.SECONDS));
        assertTrue(statementCancelled.get(), "Statement.cancel() should have been invoked");
        assertTrue(cancelledCallback.get());
    }

    @Test
    void testGenerationSupersededDiscardsOldResult() throws Exception {
        LoadTask<String> task = new LoadTask<>();
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);
        AtomicReference<String> finalResult = new AtomicReference<>();
        AtomicBoolean slowSuccessCalled = new AtomicBoolean(false);

        // 1st task: slow
        task.execute(
                ctx -> {
                    slowStarted.countDown();
                    Thread.sleep(400);
                    return "task-1-slow";
                },
                res -> slowSuccessCalled.set(true),
                err -> {}
        );

        assertTrue(slowStarted.await(3, TimeUnit.SECONDS));

        // 2nd task: fast, started immediately afterwards
        task.execute(
                ctx -> "task-2-fast",
                res -> {
                    finalResult.set(res);
                    finishLatch.countDown();
                },
                err -> fail("Task 2 failed: " + err)
        );

        assertTrue(finishLatch.await(3, TimeUnit.SECONDS));
        assertEquals("task-2-fast", finalResult.get());

        // Wait to make sure task 1 finishes and its callback is ignored
        Thread.sleep(500);
        assertFalse(slowSuccessCalled.get(), "Slow task 1 callback should be discarded by generation check");
    }
}
