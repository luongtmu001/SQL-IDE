package app.diagnostics.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

/**
 * Monitors JVM runtime uptime, specifications, and Garbage Collection statistics.
 */
public final class JvmMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JvmMonitor.class);
    private final RuntimeMXBean runtimeMXBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    public JvmMonitor() {
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    }

    public long getUptimeMs() {
        try {
            return runtimeMXBean.getUptime();
        } catch (Throwable t) {
            LOGGER.debug("Failed to get JVM uptime: {}", t.getMessage());
            return 0L;
        }
    }

    public long getStartTimeMs() {
        try {
            return runtimeMXBean.getStartTime();
        } catch (Throwable t) {
            LOGGER.debug("Failed to get JVM start time: {}", t.getMessage());
            return 0L;
        }
    }

    public String getVmName() {
        return runtimeMXBean.getVmName();
    }

    public String getVmVendor() {
        return runtimeMXBean.getVmVendor();
    }

    public String getVmVersion() {
        return runtimeMXBean.getVmVersion();
    }

    /**
     * Aggregated collection count across all GC algorithms.
     */
    public long getTotalGcCount() {
        long count = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            long c = gc.getCollectionCount();
            if (c > 0) {
                count += c;
            }
        }
        return count;
    }

    /**
     * Aggregated elapsed GC pause time in milliseconds.
     */
    public long getTotalGcTimeMs() {
        long time = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            long t = gc.getCollectionTime();
            if (t > 0) {
                time += t;
            }
        }
        return time;
    }
}

