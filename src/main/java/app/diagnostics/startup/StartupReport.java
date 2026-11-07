package app.diagnostics.startup;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Summary report of the application startup lifecycle and detected bottlenecks.
 */
public record StartupReport(
        Instant jvmStartTime,
        Instant appStartTime,
        Instant readyTime,
        long totalStartupDurationMs,
        long jvmBootstrapDurationMs,
        List<StartupTask> tasks,
        List<StartupTask> slowestTasks
) {
    public static final long BOTTLENECK_THRESHOLD_MS = 250L;

    public List<StartupTask> getBottlenecks() {
        return tasks.stream()
                .filter(t -> t.durationMs() >= BOTTLENECK_THRESHOLD_MS)
                .sorted(Comparator.comparingLong(StartupTask::durationMs).reversed())
                .toList();
    }

    public boolean hasFailures() {
        return tasks.stream().anyMatch(t -> t.status() == StartupTask.Status.FAILED);
    }
}

