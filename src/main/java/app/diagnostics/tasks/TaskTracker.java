package app.diagnostics.tasks;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe tracker for task lifecycles with bounded in-memory retention.
 */
public final class TaskTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskTracker.class);
    private static final TaskTracker INSTANCE = new TaskTracker();
    private static final int DEFAULT_MAX_COMPLETED_TASKS = 200;

    private final Map<String, TaskRecord> activeTasks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<TaskRecord> completedTasks = new ConcurrentLinkedDeque<>();
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final int maxCompleted;

    private TaskTracker() {
        this(DEFAULT_MAX_COMPLETED_TASKS);
    }

    public TaskTracker(int maxCompleted) {
        this.maxCompleted = Math.max(10, maxCompleted);
    }

    public static TaskTracker getInstance() {
        return INSTANCE;
    }

    public TaskRecord registerTask(String name, String category) {
        TaskRecord task = TaskRecord.queued(name, category);
        activeTasks.put(task.taskId(), task);
        return task;
    }

    public void startTask(String taskId) {
        if (taskId == null) return;
        activeTasks.computeIfPresent(taskId, (id, task) ->
                task.toRunning(Thread.currentThread().getName()));
    }

    public void completeTask(String taskId) {
        if (taskId == null) return;
        TaskRecord task = activeTasks.remove(taskId);
        if (task != null) {
            TaskRecord completed = task.toCompleted();
            archiveTask(completed);
            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.debug("TASK", "TaskCompleted",
                    String.format("Task '%s' completed in %d ms", completed.name(), completed.durationMs())));
        }
    }

    public void failTask(String taskId, String errorMessage) {
        if (taskId == null) return;
        TaskRecord task = activeTasks.remove(taskId);
        if (task != null) {
            TaskRecord failed = task.toFailed(errorMessage);
            archiveTask(failed);
            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.warn("TASK", "TaskFailed",
                    String.format("Task '%s' failed after %d ms: %s", failed.name(), failed.durationMs(), errorMessage)));
        }
    }

    public void cancelTask(String taskId) {
        if (taskId == null) return;
        TaskRecord task = activeTasks.remove(taskId);
        if (task != null) {
            TaskRecord cancelled = task.toCancelled();
            archiveTask(cancelled);
        }
    }

    private void archiveTask(TaskRecord record) {
        completedTasks.addLast(record);
        if (completedCount.incrementAndGet() > maxCompleted) {
            if (completedTasks.pollFirst() != null) {
                completedCount.decrementAndGet();
            }
        }
    }

    public List<TaskRecord> getActiveTasks() {
        return Collections.unmodifiableList(new ArrayList<>(activeTasks.values()));
    }

    public List<TaskRecord> getCompletedTasks() {
        return Collections.unmodifiableList(new ArrayList<>(completedTasks));
    }

    public List<TaskRecord> getRecentTasks(int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<TaskRecord> all = new ArrayList<>(completedTasks);
        if (all.size() <= limit) return Collections.unmodifiableList(all);
        return Collections.unmodifiableList(all.subList(all.size() - limit, all.size()));
    }

    public void clear() {
        activeTasks.clear();
        completedTasks.clear();
        completedCount.set(0);
    }
}

