package app.diagnostics.monitoring;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Background Monitoring service coordinator for Phase 02.
 * <p>
 * Samples JVM and system health metrics periodically on an isolated daemon thread.
 * Maintains a bounded in-memory sliding window of metrics snapshots.
 */
public final class SystemMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemMonitor.class);
    private static final int DEFAULT_MAX_HISTORY_SAMPLES = 120; // 2-4 minutes of history

    private final CpuMonitor cpuMonitor;
    private final MemoryMonitor memoryMonitor;
    private final ThreadMonitor threadMonitor;
    private final JvmMonitor jvmMonitor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentLinkedDeque<SystemMetricsSnapshot> snapshotHistory = new ConcurrentLinkedDeque<>();
    private final AtomicInteger historyCount = new AtomicInteger(0);
    private final int maxHistorySamples;

    private final List<Consumer<SystemMetricsSnapshot>> listeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> samplingTask;

    public SystemMonitor() {
        this(DEFAULT_MAX_HISTORY_SAMPLES);
    }

    public SystemMonitor(int maxHistorySamples) {
        this.maxHistorySamples = Math.max(10, maxHistorySamples);
        this.cpuMonitor = new CpuMonitor();
        this.memoryMonitor = new MemoryMonitor();
        this.threadMonitor = new ThreadMonitor();
        this.jvmMonitor = new JvmMonitor();
    }

    /**
     * Captures a single instantaneous snapshot of system and JVM metrics.
     */
    public SystemMetricsSnapshot captureSnapshot() {
        try {
            return new SystemMetricsSnapshot(
                    Instant.now(),
                    cpuMonitor.getProcessCpuLoad(),
                    cpuMonitor.getSystemCpuLoad(),
                    memoryMonitor.getHeapUsedBytes(),
                    memoryMonitor.getHeapCommittedBytes(),
                    memoryMonitor.getHeapMaxBytes(),
                    memoryMonitor.getNonHeapUsedBytes(),
                    memoryMonitor.getNonHeapCommittedBytes(),
                    threadMonitor.getLiveThreadCount(),
                    threadMonitor.getDaemonThreadCount(),
                    threadMonitor.getPeakThreadCount(),
                    jvmMonitor.getUptimeMs(),
                    jvmMonitor.getTotalGcCount(),
                    jvmMonitor.getTotalGcTimeMs()
            );
        } catch (Throwable t) {
            LOGGER.warn("Failed to capture system snapshot safely: {}", t.getMessage());
            return new SystemMetricsSnapshot(
                    Instant.now(), 0.0, 0.0, 0L, 0L, 0L, 0L, 0L, 0, 0, 0, 0L, 0L, 0L
            );
        }
    }

    /**
     * Starts background periodic sampling on an isolated daemon thread.
     */
    public synchronized void start(long intervalMs) {
        if (running.get()) {
            return;
        }

        long safeInterval = Math.max(500L, intervalMs);

        ThreadFactory daemonFactory = runnable -> {
            Thread t = new Thread(runnable, "LLTool-Diagnostics-Monitor");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        };

        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory);
        this.samplingTask = scheduler.scheduleAtFixedRate(this::sampleTick, 0, safeInterval, TimeUnit.MILLISECONDS);
        this.running.set(true);

        LOGGER.info("Background SystemMonitor started with sampling interval: {}ms", safeInterval);
    }

    /**
     * Stops background sampling and frees scheduler resources.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        if (samplingTask != null) {
            samplingTask.cancel(false);
            samplingTask = null;
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        LOGGER.info("Background SystemMonitor stopped.");
    }

    private void sampleTick() {
        if (!running.get()) {
            return;
        }

        try {
            SystemMetricsSnapshot snapshot = captureSnapshot();

            // Store in bounded sliding window
            snapshotHistory.addLast(snapshot);
            if (historyCount.incrementAndGet() > maxHistorySamples) {
                if (snapshotHistory.pollFirst() != null) {
                    historyCount.decrementAndGet();
                }
            }

            // Dispatch to observers
            for (Consumer<SystemMetricsSnapshot> listener : listeners) {
                try {
                    listener.accept(snapshot);
                } catch (Throwable t) {
                    LOGGER.warn("Metrics snapshot listener threw error: {}", t.getMessage());
                }
            }

            // High memory alert check (> 90% heap)
            if (snapshot.getHeapUsagePercentage() > 90.0) {
                DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.warn("MONITORING",
                        "HighHeapUsage",
                        String.format("Heap memory usage is critical: %.1f%% (%.1f MB / %.1f MB)",
                                snapshot.getHeapUsagePercentage(),
                                snapshot.getHeapUsedMb(),
                                snapshot.getHeapMaxMb())));
            }
        } catch (Throwable t) {
            LOGGER.warn("Error during background metrics sample tick: {}", t.getMessage());
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public List<SystemMetricsSnapshot> getSnapshotHistory() {
        return Collections.unmodifiableList(new ArrayList<>(snapshotHistory));
    }

    public SystemMetricsSnapshot getLatestSnapshot() {
        return snapshotHistory.peekLast();
    }

    public void addSnapshotListener(Consumer<SystemMetricsSnapshot> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeSnapshotListener(Consumer<SystemMetricsSnapshot> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public CpuMonitor getCpuMonitor() {
        return cpuMonitor;
    }

    public MemoryMonitor getMemoryMonitor() {
        return memoryMonitor;
    }

    public ThreadMonitor getThreadMonitor() {
        return threadMonitor;
    }

    public JvmMonitor getJvmMonitor() {
        return jvmMonitor;
    }
}

