package app.diagnostics.storage;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsManager;
import app.diagnostics.core.DiagnosticsSession;
import app.diagnostics.errors.ErrorRecord;
import app.diagnostics.errors.ExceptionCollector;
import app.diagnostics.integrations.DatabaseDiagnostics;
import app.diagnostics.integrations.JavaFxDiagnostics;
import app.diagnostics.integrations.WebViewDiagnostics;
import app.diagnostics.monitoring.SystemMetricsSnapshot;
import app.diagnostics.monitoring.SystemMonitor;
import app.diagnostics.startup.StartupProfiler;
import app.diagnostics.startup.StartupReport;
import app.diagnostics.startup.StartupTask;
import app.diagnostics.tasks.TaskRecord;
import app.diagnostics.tasks.TaskTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports diagnostic summaries, reports, and full diagnostic bundles (.zip) without external JSON libraries.
 */
public final class DiagnosticsExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticsExporter.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final DiagnosticsStorage storage;

    public DiagnosticsExporter() {
        this(DiagnosticsStorage.getInstance());
    }

    public DiagnosticsExporter(DiagnosticsStorage storage) {
        this.storage = storage;
    }

    /**
     * Generates a plain-text executive report.
     */
    public String generateTextReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("======================================================================\n");
        sb.append("                  LLTOOL - APPLICATION DIAGNOSTICS REPORT             \n");
        sb.append("======================================================================\n");
        sb.append("Generated At : ").append(ISO_FMT.format(Instant.now())).append("\n");
        sb.append("OS           : ").append(System.getProperty("os.name")).append(" (")
                .append(System.getProperty("os.arch")).append(")\n");
        sb.append("Java Runtime : ").append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vendor")).append(")\n");

        DiagnosticsSession session = DiagnosticsManager.getInstance().getCurrentSession();
        if (session != null) {
            sb.append("Session ID   : ").append(session.getSessionId()).append("\n");
            sb.append("Active Level : ").append(session.getActiveLevel()).append("\n");
            sb.append("Event Count  : ").append(session.getRecordedEventCount()).append("\n");
        }

        sb.append("\n----------------------------------------------------------------------\n");
        sb.append("1. STARTUP ANALYSIS\n");
        sb.append("----------------------------------------------------------------------\n");
        StartupReport sr = StartupProfiler.getInstance().generateReport();
        sb.append("Total Startup Duration : ").append(sr.totalStartupDurationMs()).append(" ms\n");
        sb.append("JVM Bootstrap Duration : ").append(sr.jvmBootstrapDurationMs()).append(" ms\n");
        sb.append("Total Startup Tasks    : ").append(sr.tasks().size()).append("\n");

        List<StartupTask> bottlenecks = sr.getBottlenecks();
        if (!bottlenecks.isEmpty()) {
            sb.append("Detected Bottlenecks (>= 250ms):\n");
            for (StartupTask bn : bottlenecks) {
                sb.append(String.format("  - %s: %d ms [%s]\n", bn.name(), bn.durationMs(), bn.status()));
            }
        } else {
            sb.append("Bottlenecks            : None detected\n");
        }

        sb.append("\n----------------------------------------------------------------------\n");
        sb.append("2. SYSTEM & JVM HEALTH\n");
        sb.append("----------------------------------------------------------------------\n");
        SystemMonitor monitor = new SystemMonitor();
        SystemMetricsSnapshot snap = monitor.captureSnapshot();
        sb.append(String.format("Process CPU Usage : %.1f %%\n", snap.getProcessCpuPercentage()));
        sb.append(String.format("System CPU Usage  : %.1f %%\n", snap.getSystemCpuPercentage()));
        sb.append(String.format("Heap Memory       : %.1f MB used / %.1f MB max (%.1f %%)\n",
                snap.getHeapUsedMb(), snap.getHeapMaxMb(), snap.getHeapUsagePercentage()));
        sb.append(String.format("Non-Heap Memory   : %.1f MB used\n", snap.getNonHeapUsedMb()));
        sb.append(String.format("Threads           : %d live, %d daemon, %d peak\n",
                snap.liveThreadCount(), snap.daemonThreadCount(), snap.peakThreadCount()));
        sb.append(String.format("GC Stats          : %d collections, %d ms pause time\n",
                snap.totalGcCount(), snap.totalGcTimeMs()));

        sb.append("\n----------------------------------------------------------------------\n");
        sb.append("3. RUNTIME ERRORS & EXCEPTIONS\n");
        sb.append("----------------------------------------------------------------------\n");
        List<ErrorRecord> errors = ExceptionCollector.getInstance().getErrors();
        sb.append("Unique Error Types : ").append(errors.size()).append("\n");
        for (ErrorRecord err : errors) {
            sb.append(String.format("  - [%dx] %s: %s (Thread: %s)\n",
                    err.getOccurrenceCount(), err.getExceptionClass(), err.getMessage(), err.getThreadName()));
        }

        sb.append("\n----------------------------------------------------------------------\n");
        sb.append("4. TASKS & INTEGRATIONS\n");
        sb.append("----------------------------------------------------------------------\n");
        sb.append("Active Tasks    : ").append(TaskTracker.getInstance().getActiveTasks().size()).append("\n");
        sb.append("Completed Tasks : ").append(TaskTracker.getInstance().getCompletedTasks().size()).append("\n");
        sb.append("UI Freeze Lag   : ").append(JavaFxDiagnostics.getInstance().getLagRecords().size()).append(" events\n");
        sb.append("WebView Errors  : ").append(WebViewDiagnostics.getInstance().getJsErrors().size()).append(" JS errors\n");
        sb.append("DB Connections  : ").append(DatabaseDiagnostics.getInstance().getConnectionMetrics().size()).append(" monitored\n");
        sb.append("DB Queries      : ").append(DatabaseDiagnostics.getInstance().getQueryMetrics().size()).append(" monitored\n");
        sb.append("======================================================================\n");

        return sb.toString();
    }

    /**
     * Generates a structured JSON string.
     */
    public String generateJsonReport() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"os\": {\n");
        json.append("    \"name\": \"").append(escapeJson(System.getProperty("os.name"))).append("\",\n");
        json.append("    \"arch\": \"").append(escapeJson(System.getProperty("os.arch"))).append("\"\n");
        json.append("  },\n");
        json.append("  \"java\": {\n");
        json.append("    \"version\": \"").append(escapeJson(System.getProperty("java.version"))).append("\",\n");
        json.append("    \"vendor\": \"").append(escapeJson(System.getProperty("java.vendor"))).append("\"\n");
        json.append("  },\n");

        StartupReport sr = StartupProfiler.getInstance().generateReport();
        json.append("  \"startup\": {\n");
        json.append("    \"totalDurationMs\": ").append(sr.totalStartupDurationMs()).append(",\n");
        json.append("    \"jvmBootstrapDurationMs\": ").append(sr.jvmBootstrapDurationMs()).append(",\n");
        json.append("    \"taskCount\": ").append(sr.tasks().size()).append("\n");
        json.append("  },\n");

        SystemMetricsSnapshot snap = new SystemMonitor().captureSnapshot();
        json.append("  \"metrics\": {\n");
        json.append("    \"processCpuPct\": ").append(snap.getProcessCpuPercentage()).append(",\n");
        json.append("    \"systemCpuPct\": ").append(snap.getSystemCpuPercentage()).append(",\n");
        json.append("    \"heapUsedMb\": ").append(snap.getHeapUsedMb()).append(",\n");
        json.append("    \"heapMaxMb\": ").append(snap.getHeapMaxMb()).append(",\n");
        json.append("    \"liveThreads\": ").append(snap.liveThreadCount()).append("\n");
        json.append("  },\n");

        List<ErrorRecord> errors = ExceptionCollector.getInstance().getErrors();
        json.append("  \"errors\": [\n");
        for (int i = 0; i < errors.size(); i++) {
            ErrorRecord err = errors.get(i);
            json.append("    {\n");
            json.append("      \"class\": \"").append(escapeJson(err.getExceptionClass())).append("\",\n");
            json.append("      \"message\": \"").append(escapeJson(err.getMessage())).append("\",\n");
            json.append("      \"occurrences\": ").append(err.getOccurrenceCount()).append("\n");
            json.append("    }").append(i < errors.size() - 1 ? "," : "").append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        return json.toString();
    }

    /**
     * Creates a Diagnostics Bundle .zip file containing TXT, JSON, and metadata.
     */
    public Path createDiagnosticsBundle(Path optionalJfrFile) throws IOException {
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now());
        Path zipFile = storage.getExportsDir().resolve("DiagnosticsBundle_" + ts + ".zip");

        String txtReport = generateTextReport();
        String jsonReport = generateJsonReport();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            // Write report.txt
            ZipEntry txtEntry = new ZipEntry("report.txt");
            zos.putNextEntry(txtEntry);
            zos.write(txtReport.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Write report.json
            ZipEntry jsonEntry = new ZipEntry("report.json");
            zos.putNextEntry(jsonEntry);
            zos.write(jsonReport.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Optional JFR file
            if (optionalJfrFile != null && Files.exists(optionalJfrFile)) {
                ZipEntry jfrEntry = new ZipEntry(optionalJfrFile.getFileName().toString());
                zos.putNextEntry(jfrEntry);
                Files.copy(optionalJfrFile, zos);
                zos.closeEntry();
            }
        }

        LOGGER.info("Diagnostics Bundle created at: {}", zipFile.toAbsolutePath());
        return zipFile;
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

