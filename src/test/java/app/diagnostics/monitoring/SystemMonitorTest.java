package app.diagnostics.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SystemMonitorTest {

    @Test
    @DisplayName("CpuMonitor should read valid CPU information and core count")
    void testCpuMonitor() {
        CpuMonitor cpu = new CpuMonitor();
        assertTrue(cpu.getAvailableProcessors() >= 1, "Should have at least 1 processor core");
        assertNotNull(cpu.getArch());
        assertNotNull(cpu.getOsName());

        double processCpu = cpu.getProcessCpuLoad();
        double systemCpu = cpu.getSystemCpuLoad();

        assertTrue(processCpu >= 0.0 && processCpu <= 1.0, "Process CPU load should be between 0.0 and 1.0");
        assertTrue(systemCpu >= 0.0 && systemCpu <= 1.0, "System CPU load should be between 0.0 and 1.0");
    }

    @Test
    @DisplayName("MemoryMonitor should report positive heap and non-heap metrics")
    void testMemoryMonitor() {
        MemoryMonitor memory = new MemoryMonitor();
        assertNotNull(memory.getHeapUsage());
        assertNotNull(memory.getNonHeapUsage());

        assertTrue(memory.getHeapUsedBytes() > 0, "Heap used bytes should be positive");
        assertTrue(memory.getHeapCommittedBytes() >= memory.getHeapUsedBytes(), "Committed bytes should be >= used");
        assertTrue(memory.getNonHeapUsedBytes() > 0, "Non-heap used bytes should be positive");
    }

    @Test
    @DisplayName("ThreadMonitor should read live, daemon, and peak thread counts")
    void testThreadMonitor() {
        ThreadMonitor threads = new ThreadMonitor();
        assertTrue(threads.getLiveThreadCount() > 0, "Live thread count should be positive");
        assertTrue(threads.getDaemonThreadCount() >= 0, "Daemon thread count should be >= 0");
        assertTrue(threads.getPeakThreadCount() >= threads.getLiveThreadCount(), "Peak should be >= live count");
        assertTrue(threads.getTotalStartedThreadCount() > 0, "Total started thread count should be > 0");
    }

    @Test
    @DisplayName("JvmMonitor should report JVM uptime, VM identity and GC stats")
    void testJvmMonitor() {
        JvmMonitor jvm = new JvmMonitor();
        assertTrue(jvm.getUptimeMs() > 0, "JVM uptime should be positive");
        assertTrue(jvm.getStartTimeMs() > 0, "JVM start time should be positive");
        assertNotNull(jvm.getVmName());
        assertNotNull(jvm.getVmVersion());

        assertTrue(jvm.getTotalGcCount() >= 0, "GC count should be >= 0");
        assertTrue(jvm.getTotalGcTimeMs() >= 0, "GC time should be >= 0");
    }

    @Test
    @DisplayName("SystemMetricsSnapshot calculation helpers")
    void testSnapshotCalculations() {
        SystemMetricsSnapshot snapshot = new SystemMetricsSnapshot(
                Instant.now(),
                0.25, // 25% CPU
                0.50, // 50% system CPU
                50 * 1024 * 1024L, // 50MB used
                100 * 1024 * 1024L, // 100MB committed
                200 * 1024 * 1024L, // 200MB max
                20 * 1024 * 1024L, // 20MB non-heap
                40 * 1024 * 1024L,
                15,
                5,
                20,
                10000L,
                3,
                150L
        );

        assertEquals(25.0, snapshot.getProcessCpuPercentage(), 0.001);
        assertEquals(50.0, snapshot.getSystemCpuPercentage(), 0.001);
        assertEquals(25.0, snapshot.getHeapUsagePercentage(), 0.001); // 50MB / 200MB = 25%
        assertEquals(50.0, snapshot.getHeapUsedMb(), 0.001);
        assertEquals(200.0, snapshot.getHeapMaxMb(), 0.001);
        assertEquals(20.0, snapshot.getNonHeapUsedMb(), 0.001);
    }

    @Test
    @DisplayName("SystemMonitor background sampling lifecycle, observer dispatch, and bounded history")
    void testSystemMonitorLifecycle() throws InterruptedException {
        int maxCapacity = 15;
        SystemMonitor monitor = new SystemMonitor(maxCapacity);
        assertFalse(monitor.isRunning());

        CountDownLatch sampleLatch = new CountDownLatch(3);
        AtomicReference<SystemMetricsSnapshot> lastSample = new AtomicReference<>();

        monitor.addSnapshotListener(snapshot -> {
            lastSample.set(snapshot);
            sampleLatch.countDown();
        });

        // Fast interval for testing (50ms)
        monitor.start(50);
        assertTrue(monitor.isRunning());

        boolean received = sampleLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "Should have received at least 3 samples from background thread");
        assertNotNull(lastSample.get());
        assertTrue(lastSample.get().heapUsedBytes() > 0);

        // Verify history is accumulating but bounded
        List<SystemMetricsSnapshot> history = monitor.getSnapshotHistory();
        assertFalse(history.isEmpty());
        assertTrue(history.size() <= maxCapacity);

        monitor.stop();
        assertFalse(monitor.isRunning());
    }
}

