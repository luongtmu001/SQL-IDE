package app.diagnostics.advanced;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects Java thread monitor and synchronizer deadlocks via ThreadMXBean.
 */
public final class DeadlockDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeadlockDetector.class);
    private static final DeadlockDetector INSTANCE = new DeadlockDetector();

    public record DeadlockedThreadInfo(
            long threadId,
            String threadName,
            String threadState,
            String lockName,
            String lockOwnerName,
            long lockOwnerId,
            List<String> stackTrace
    ) {}

    public record DeadlockReport(
            Instant timestamp,
            boolean deadlockDetected,
            List<DeadlockedThreadInfo> deadlockedThreads
    ) {}

    private final ThreadMXBean threadBean;

    private DeadlockDetector() {
        this.threadBean = ManagementFactory.getThreadMXBean();
    }

    public static DeadlockDetector getInstance() {
        return INSTANCE;
    }

    public DeadlockReport detectDeadlocks() {
        try {
            long[] deadlockedIds = threadBean.findDeadlockedThreads();
            if (deadlockedIds == null || deadlockedIds.length == 0) {
                return new DeadlockReport(Instant.now(), false, Collections.emptyList());
            }

            ThreadInfo[] infos = threadBean.getThreadInfo(deadlockedIds, true, true);
            List<DeadlockedThreadInfo> threadList = new ArrayList<>();

            for (ThreadInfo ti : infos) {
                if (ti == null) continue;

                List<String> stack = new ArrayList<>();
                for (StackTraceElement ste : ti.getStackTrace()) {
                    stack.add(ste.toString());
                }

                threadList.add(new DeadlockedThreadInfo(
                        ti.getThreadId(),
                        ti.getThreadName(),
                        ti.getThreadState().name(),
                        ti.getLockName() != null ? ti.getLockName() : "none",
                        ti.getLockOwnerName() != null ? ti.getLockOwnerName() : "none",
                        ti.getLockOwnerId(),
                        stack
                ));
            }

            DeadlockReport report = new DeadlockReport(Instant.now(), true, threadList);
            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.error("DEADLOCK", "DeadlockDetected",
                    "CRITICAL: JVM Deadlock detected involving " + threadList.size() + " threads!", null));

            return report;
        } catch (Throwable t) {
            LOGGER.error("Failed to analyze deadlocks safely: {}", t.getMessage());
            return new DeadlockReport(Instant.now(), false, Collections.emptyList());
        }
    }
}

