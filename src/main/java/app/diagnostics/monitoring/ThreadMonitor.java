package app.diagnostics.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Monitors active JVM threads via ThreadMXBean.
 */
public final class ThreadMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadMonitor.class);
    private final ThreadMXBean threadMXBean;

    public ThreadMonitor() {
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    public int getLiveThreadCount() {
        try {
            return threadMXBean.getThreadCount();
        } catch (Throwable t) {
            LOGGER.debug("Failed to retrieve thread count: {}", t.getMessage());
            return 0;
        }
    }

    public int getDaemonThreadCount() {
        try {
            return threadMXBean.getDaemonThreadCount();
        } catch (Throwable t) {
            LOGGER.debug("Failed to retrieve daemon thread count: {}", t.getMessage());
            return 0;
        }
    }

    public int getPeakThreadCount() {
        try {
            return threadMXBean.getPeakThreadCount();
        } catch (Throwable t) {
            LOGGER.debug("Failed to retrieve peak thread count: {}", t.getMessage());
            return 0;
        }
    }

    public long getTotalStartedThreadCount() {
        try {
            return threadMXBean.getTotalStartedThreadCount();
        } catch (Throwable t) {
            LOGGER.debug("Failed to retrieve total started thread count: {}", t.getMessage());
            return 0;
        }
    }
}

