package app.diagnostics.startup;

import java.time.Instant;

/**
 * Record representing an individual task executed during application startup.
 */
public record StartupTask(
        String name,
        Instant startTimestamp,
        Instant endTimestamp,
        long durationMs,
        String threadName,
        Status status,
        String errorMessage
) {
    public enum Status {
        SUCCESS,
        FAILED,
        IN_PROGRESS
    }

    public static StartupTask completed(String name, Instant start, Instant end, String threadName) {
        long duration = Math.max(0, java.time.Duration.between(start, end).toMillis());
        return new StartupTask(name, start, end, duration, threadName, Status.SUCCESS, null);
    }

    public static StartupTask failed(String name, Instant start, Instant end, String threadName, String error) {
        long duration = Math.max(0, java.time.Duration.between(start, end).toMillis());
        return new StartupTask(name, start, end, duration, threadName, Status.FAILED, error);
    }
}

