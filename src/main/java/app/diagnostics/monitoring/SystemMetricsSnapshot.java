package app.diagnostics.monitoring;

import java.time.Instant;

/**
 * Immutable snapshot of system, JVM, memory, CPU, and thread metrics at a given point in time.
 */
public record SystemMetricsSnapshot(
        Instant timestamp,
        double processCpuLoad,
        double systemCpuLoad,
        long heapUsedBytes,
        long heapCommittedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        long nonHeapCommittedBytes,
        int liveThreadCount,
        int daemonThreadCount,
        int peakThreadCount,
        long jvmUptimeMs,
        long totalGcCount,
        long totalGcTimeMs
) {

    public double getHeapUsagePercentage() {
        if (heapMaxBytes <= 0) {
            return 0.0;
        }
        return ((double) heapUsedBytes / (double) heapMaxBytes) * 100.0;
    }

    public double getProcessCpuPercentage() {
        return Math.max(0.0, processCpuLoad * 100.0);
    }

    public double getSystemCpuPercentage() {
        return Math.max(0.0, systemCpuLoad * 100.0);
    }

    public double getHeapUsedMb() {
        return heapUsedBytes / (1024.0 * 1024.0);
    }

    public double getHeapMaxMb() {
        return heapMaxBytes / (1024.0 * 1024.0);
    }

    public double getNonHeapUsedMb() {
        return nonHeapUsedBytes / (1024.0 * 1024.0);
    }
}

