package app.diagnostics.tasks;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable tracking record for an asynchronous or background task.
 */
public record TaskRecord(
        String taskId,
        String name,
        String category,
        Instant queuedTime,
        Instant startTime,
        Instant endTime,
        long durationMs,
        TaskStatus status,
        String threadName,
        String errorMessage
) {
    public static TaskRecord queued(String name, String category) {
        String id = "task_" + UUID.randomUUID().toString().substring(0, 8);
        return new TaskRecord(id, name, category, Instant.now(), null, null, 0L,
                TaskStatus.QUEUED, null, null);
    }

    public TaskRecord toRunning(String threadName) {
        Instant now = Instant.now();
        return new TaskRecord(taskId, name, category, queuedTime, now, null, 0L,
                TaskStatus.RUNNING, threadName, null);
    }

    public TaskRecord toCompleted() {
        Instant now = Instant.now();
        Instant effectiveStart = startTime != null ? startTime : queuedTime;
        long duration = Math.max(0, java.time.Duration.between(effectiveStart, now).toMillis());
        return new TaskRecord(taskId, name, category, queuedTime, startTime, now, duration,
                TaskStatus.COMPLETED, threadName, null);
    }

    public TaskRecord toFailed(String error) {
        Instant now = Instant.now();
        Instant effectiveStart = startTime != null ? startTime : queuedTime;
        long duration = Math.max(0, java.time.Duration.between(effectiveStart, now).toMillis());
        return new TaskRecord(taskId, name, category, queuedTime, startTime, now, duration,
                TaskStatus.FAILED, threadName, error);
    }

    public TaskRecord toCancelled() {
        Instant now = Instant.now();
        Instant effectiveStart = startTime != null ? startTime : queuedTime;
        long duration = Math.max(0, java.time.Duration.between(effectiveStart, now).toMillis());
        return new TaskRecord(taskId, name, category, queuedTime, startTime, now, duration,
                TaskStatus.CANCELLED, threadName, null);
    }
}

