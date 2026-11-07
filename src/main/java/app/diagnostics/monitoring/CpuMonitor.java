package app.diagnostics.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * Monitors Process and System CPU utilization via OperatingSystemMXBean.
 */
public final class CpuMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CpuMonitor.class);
    private final OperatingSystemMXBean baseOsBean;
    private final com.sun.management.OperatingSystemMXBean sunOsBean;

    public CpuMonitor() {
        this.baseOsBean = ManagementFactory.getOperatingSystemMXBean();
        if (baseOsBean instanceof com.sun.management.OperatingSystemMXBean sun) {
            this.sunOsBean = sun;
        } else {
            this.sunOsBean = null;
            LOGGER.warn("com.sun.management.OperatingSystemMXBean not available, CPU usage readings may be limited.");
        }
    }

    /**
     * Returns the recent CPU usage for the Java Virtual Machine process [0.0, 1.0].
     * Returns 0.0 if not yet available or unsupported.
     */
    public double getProcessCpuLoad() {
        if (sunOsBean != null) {
            try {
                double load = sunOsBean.getProcessCpuLoad();
                return load >= 0.0 ? load : 0.0;
            } catch (Throwable t) {
                LOGGER.debug("Failed to read process CPU load: {}", t.getMessage());
            }
        }
        return 0.0;
    }

    /**
     * Returns the recent CPU usage for the whole system [0.0, 1.0].
     * Returns 0.0 if not yet available or unsupported.
     */
    public double getSystemCpuLoad() {
        if (sunOsBean != null) {
            try {
                double load = sunOsBean.getCpuLoad();
                return load >= 0.0 ? load : 0.0;
            } catch (Throwable t) {
                LOGGER.debug("Failed to read system CPU load: {}", t.getMessage());
            }
        }
        return 0.0;
    }

    /**
     * Returns the number of available processors/cores.
     */
    public int getAvailableProcessors() {
        return baseOsBean.getAvailableProcessors();
    }

    /**
     * Returns the operating system architecture (e.g. amd64).
     */
    public String getArch() {
        return baseOsBean.getArch();
    }

    /**
     * Returns the operating system name (e.g. Windows 11).
     */
    public String getOsName() {
        return baseOsBean.getName();
    }
}

