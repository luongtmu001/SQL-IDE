package app.diagnostics.advanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight stack trace sampling profiler for on-demand Level 3 CPU analysis.
 */
public final class CpuProfiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CpuProfiler.class);
    private static final CpuProfiler INSTANCE = new CpuProfiler();

    public record HotMethod(String methodSignature, long sampleCount, double percentage) {}

    private final ThreadMXBean threadBean;
    private final Map<String, AtomicLong> methodSampleCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalSamples = new AtomicLong(0);
    private final AtomicBoolean active = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> samplingTask;

    private CpuProfiler() {
        this.threadBean = ManagementFactory.getThreadMXBean();
    }

    public static CpuProfiler getInstance() {
        return INSTANCE;
    }

    public synchronized void startSampling(long intervalMs) {
        if (active.get()) return;

        methodSampleCounts.clear();
        totalSamples.set(0);

        long safeInterval = Math.max(20L, intervalMs);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LLTool-Cpu-Sampler");
            t.setDaemon(true);
            return t;
        });

        samplingTask = scheduler.scheduleAtFixedRate(this::sampleTick, 0, safeInterval, TimeUnit.MILLISECONDS);
        active.set(true);
        LOGGER.info("CPU Profiler sampling started with interval: {} ms", safeInterval);
    }

    public synchronized void stopSampling() {
        if (!active.get()) return;

        active.set(false);
        if (samplingTask != null) {
            samplingTask.cancel(false);
            samplingTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        LOGGER.info("CPU Profiler sampling stopped.");
    }

    private void sampleTick() {
        try {
            ThreadInfo[] threads = threadBean.dumpAllThreads(false, false);
            for (ThreadInfo ti : threads) {
                if (ti.getThreadState() == Thread.State.RUNNABLE) {
                    StackTraceElement[] stack = ti.getStackTrace();
                    if (stack != null && stack.length > 0) {
                        StackTraceElement top = stack[0];
                        String signature = top.getClassName() + "." + top.getMethodName();
                        methodSampleCounts.computeIfAbsent(signature, k -> new AtomicLong(0)).incrementAndGet();
                        totalSamples.incrementAndGet();
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Error during CPU sample tick: {}", t.getMessage());
        }
    }

    public List<HotMethod> getHotMethods(int limit) {
        long total = totalSamples.get();
        if (total == 0) return Collections.emptyList();

        List<HotMethod> result = new ArrayList<>();
        methodSampleCounts.forEach((method, count) -> {
            double pct = (count.doubleValue() / (double) total) * 100.0;
            result.add(new HotMethod(method, count.get(), pct));
        });

        result.sort(Comparator.comparingLong(HotMethod::sampleCount).reversed());
        return limit > 0 && result.size() > limit
                ? Collections.unmodifiableList(result.subList(0, limit))
                : Collections.unmodifiableList(result);
    }

    public boolean isActive() {
        return active.get();
    }
}

