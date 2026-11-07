package app.diagnostics.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Monitors JVM Heap and Non-Heap memory usage via MemoryMXBean.
 */
public final class MemoryMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMonitor.class);
    private final MemoryMXBean memoryMXBean;

    public MemoryMonitor() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
    }

    public MemoryUsage getHeapUsage() {
        try {
            return memoryMXBean.getHeapMemoryUsage();
        } catch (Throwable t) {
            LOGGER.debug("Failed to retrieve heap usage: {}", t.getMessage());
            return new MemoryUsage(0, 0, 0, 0);
        }
    }

    public MemoryUsage getNonHeapUsage() {
        try {
            return memoryMXBean.getNonHeapMemoryUsage();
        } catch (Throwable t) {
            LOGGER.debug("Failed to retrieve non-heap usage: {}", t.getMessage());
            return new MemoryUsage(0, 0, 0, 0);
        }
    }

    public long getHeapUsedBytes() {
        return getHeapUsage().getUsed();
    }

    public long getHeapCommittedBytes() {
        return getHeapUsage().getCommitted();
    }

    public long getHeapMaxBytes() {
        return getHeapUsage().getMax();
    }

    public long getNonHeapUsedBytes() {
        return getNonHeapUsage().getUsed();
    }

    public long getNonHeapCommittedBytes() {
        return getNonHeapUsage().getCommitted();
    }
}

