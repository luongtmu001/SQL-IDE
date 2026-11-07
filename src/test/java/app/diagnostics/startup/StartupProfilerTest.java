package app.diagnostics.startup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StartupProfilerTest {

    private StartupProfiler profiler;

    @BeforeEach
    void setUp() {
        profiler = StartupProfiler.getInstance();
        profiler.reset();
    }

    @Test
    @DisplayName("StartupProfiler should record tasks via handle and generate complete StartupReport")
    void testTaskTimingAndReport() throws InterruptedException {
        profiler.markAppStarted();

        try (var handle = profiler.beginTask("InitConfig")) {
            Thread.sleep(10);
        }

        try (var handle = profiler.beginTask("InitDatabaseDrivers")) {
            Thread.sleep(15);
            handle.fail("Driver timeout");
        }

        profiler.markAppReady();

        StartupReport report = profiler.generateReport();
        assertNotNull(report);
        assertTrue(report.totalStartupDurationMs() >= 20);
        assertEquals(2, report.tasks().size());

        StartupTask configTask = report.tasks().stream()
                .filter(t -> "InitConfig".equals(t.name()))
                .findFirst().orElseThrow();
        assertEquals(StartupTask.Status.SUCCESS, configTask.status());
        assertTrue(configTask.durationMs() >= 10);

        StartupTask dbTask = report.tasks().stream()
                .filter(t -> "InitDatabaseDrivers".equals(t.name()))
                .findFirst().orElseThrow();
        assertEquals(StartupTask.Status.FAILED, dbTask.status());
        assertEquals("Driver timeout", dbTask.errorMessage());
        assertTrue(report.hasFailures());
    }

    @Test
    @DisplayName("StartupProfiler should accurately detect bottlenecks")
    void testBottlenecksDetection() {
        profiler.recordTask(new StartupTask("FastTask", java.time.Instant.now(), java.time.Instant.now(),
                50L, Thread.currentThread().getName(), StartupTask.Status.SUCCESS, null));
        profiler.recordTask(new StartupTask("HeavyTask", java.time.Instant.now(), java.time.Instant.now(),
                320L, Thread.currentThread().getName(), StartupTask.Status.SUCCESS, null));

        StartupReport report = profiler.generateReport();
        List<StartupTask> bottlenecks = report.getBottlenecks();
        assertEquals(1, bottlenecks.size());
        assertEquals("HeavyTask", bottlenecks.get(0).name());
    }
}

