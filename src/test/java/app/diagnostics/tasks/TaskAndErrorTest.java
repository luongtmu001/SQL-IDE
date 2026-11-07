package app.diagnostics.tasks;

import app.diagnostics.errors.ErrorRecord;
import app.diagnostics.errors.ExceptionCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskAndErrorTest {

    private TaskTracker tracker;
    private ExceptionCollector collector;

    @BeforeEach
    void setUp() {
        tracker = new TaskTracker(15);
        collector = new ExceptionCollector(15);
    }

    @Test
    @DisplayName("TaskTracker should transition tasks from queued to completed with duration")
    void testTaskTrackerLifecycle() {
        TaskRecord queued = tracker.registerTask("ExecuteQuery", "DATABASE");
        assertEquals(TaskStatus.QUEUED, queued.status());
        assertEquals(1, tracker.getActiveTasks().size());

        tracker.startTask(queued.taskId());
        TaskRecord running = tracker.getActiveTasks().get(0);
        assertEquals(TaskStatus.RUNNING, running.status());
        assertNotNull(running.startTime());

        tracker.completeTask(queued.taskId());
        assertTrue(tracker.getActiveTasks().isEmpty());
        assertEquals(1, tracker.getCompletedTasks().size());

        TaskRecord completed = tracker.getCompletedTasks().get(0);
        assertEquals(TaskStatus.COMPLETED, completed.status());
        assertNotNull(completed.endTime());
    }

    @Test
    @DisplayName("ExceptionCollector should record, categorize and deduplicate recurring exceptions")
    void testExceptionCollectorDeduplication() {
        RuntimeException ex = new RuntimeException("Test SQL syntax error");

        ErrorRecord r1 = collector.recordException(ex, "SQL_PARSER");
        assertNotNull(r1);
        assertEquals(1, r1.getOccurrenceCount());
        assertEquals(1, collector.getTotalUniqueErrors());

        // Same exception recorded again
        ErrorRecord r2 = collector.recordException(ex, "SQL_PARSER");
        assertSame(r1, r2);
        assertEquals(2, r1.getOccurrenceCount());
        assertEquals(1, collector.getTotalUniqueErrors());

        // Different exception
        IllegalArgumentException diff = new IllegalArgumentException("Invalid port");
        ErrorRecord r3 = collector.recordException(diff, "CONNECTION");
        assertNotSame(r1, r3);
        assertEquals(2, collector.getTotalUniqueErrors());
    }
}

