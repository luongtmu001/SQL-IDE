package app.diagnostics.startup;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High-precision, zero-overhead Startup Profiler.
 * <p>
 * Non-invasive: tasks are registered passively. Does not block or alter startup sequence.
 */
public final class StartupProfiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupProfiler.class);
    private static final StartupProfiler INSTANCE = new StartupProfiler();

    private final Instant jvmStartTime;
    private volatile Instant appStartTime;
    private volatile Instant readyTime;

    private final Map<String, Instant> pendingTasks = new ConcurrentHashMap<>();
    private final List<StartupTask> completedTasks = new CopyOnWriteArrayList<>();

    private StartupProfiler() {
        long jvmStartEpoch = ManagementFactory.getRuntimeMXBean().getStartTime();
        this.jvmStartTime = Instant.ofEpochMilli(jvmStartEpoch);
        this.appStartTime = Instant.now();
    }

    public static StartupProfiler getInstance() {
        return INSTANCE;
    }

    public void markAppStarted() {
        this.appStartTime = Instant.now();
    }

    public void markAppReady() {
        this.readyTime = Instant.now();
        DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.info("STARTUP", "AppReady",
                "Application reached ready state in " + getTotalStartupDurationMs() + "ms"));
    }

    /**
     * Begins timing a startup task. Returns an AutoCloseable handle for use in try-with-resources.
     */
    public TaskHandle beginTask(String taskName) {
        if (taskName == null || taskName.isBlank()) {
            return TaskHandle.NO_OP;
        }
        Instant start = Instant.now();
        pendingTasks.put(taskName, start);
        return new TaskHandle(this, taskName, start);
    }

    public void endTask(String taskName, boolean success, String errorMessage) {
        if (taskName == null) {
            return;
        }
        Instant start = pendingTasks.remove(taskName);
        if (start == null) {
            start = appStartTime != null ? appStartTime : Instant.now();
        }
        Instant end = Instant.now();
        String threadName = Thread.currentThread().getName();

        StartupTask task = success
                ? StartupTask.completed(taskName, start, end, threadName)
                : StartupTask.failed(taskName, start, end, threadName, errorMessage);

        recordTask(task);
    }

    public void recordTask(StartupTask task) {
        if (task == null) {
            return;
        }
        completedTasks.add(task);
        if (task.durationMs() >= StartupReport.BOTTLENECK_THRESHOLD_MS) {
            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.warn("STARTUP", "StartupBottleneck",
                    String.format("Slow startup task detected: '%s' took %d ms", task.name(), task.durationMs())));
        }
    }

    public long getTotalStartupDurationMs() {
        Instant end = readyTime != null ? readyTime : Instant.now();
        Instant start = appStartTime != null ? appStartTime : jvmStartTime;
        return Math.max(0, java.time.Duration.between(start, end).toMillis());
    }

    public long getJvmBootstrapDurationMs() {
        Instant appStart = appStartTime != null ? appStartTime : Instant.now();
        return Math.max(0, java.time.Duration.between(jvmStartTime, appStart).toMillis());
    }

    public StartupReport generateReport() {
        List<StartupTask> tasks = new ArrayList<>(completedTasks);
        List<StartupTask> slowest = tasks.stream()
                .sorted(Comparator.comparingLong(StartupTask::durationMs).reversed())
                .limit(5)
                .toList();

        return new StartupReport(
                jvmStartTime,
                appStartTime,
                readyTime != null ? readyTime : Instant.now(),
                getTotalStartupDurationMs(),
                getJvmBootstrapDurationMs(),
                Collections.unmodifiableList(tasks),
                slowest
        );
    }

    public List<StartupTask> getTasks() {
        return Collections.unmodifiableList(completedTasks);
    }

    public void reset() {
        pendingTasks.clear();
        completedTasks.clear();
        appStartTime = Instant.now();
        readyTime = null;
    }

    /**
     * AutoCloseable handle for measuring startup tasks with minimal syntax.
     */
    public static final class TaskHandle implements AutoCloseable {
        private static final TaskHandle NO_OP = new TaskHandle(null, null, null);

        private final StartupProfiler profiler;
        private final String taskName;
        private final Instant startTime;
        private boolean closed = false;

        private TaskHandle(StartupProfiler profiler, String taskName, Instant startTime) {
            this.profiler = profiler;
            this.taskName = taskName;
            this.startTime = startTime;
        }

        public void fail(String errorMessage) {
            if (!closed && profiler != null) {
                closed = true;
                profiler.endTask(taskName, false, errorMessage);
            }
        }

        @Override
        public void close() {
            if (!closed && profiler != null) {
                closed = true;
                profiler.endTask(taskName, true, null);
            }
        }
    }
}

