package app.diagnostics.level;

import app.diagnostics.core.DiagnosticsLevel;

/**
 * Granular diagnostic capabilities mapped to diagnostic levels.
 */
public enum DiagnosticsFeature {
    // Level 1 - Basic
    STARTUP_PROFILING(DiagnosticsLevel.BASIC, "Startup timeline and bottleneck analysis"),
    CPU_MONITORING(DiagnosticsLevel.BASIC, "Process and system CPU tracking"),
    MEMORY_MONITORING(DiagnosticsLevel.BASIC, "Heap and non-heap memory tracking"),
    THREAD_MONITORING(DiagnosticsLevel.BASIC, "Active and peak thread counts"),
    ERROR_MONITORING(DiagnosticsLevel.BASIC, "Uncaught exception collection"),
    LOG_EVENTS(DiagnosticsLevel.BASIC, "Diagnostic event recording"),

    // Level 2 - Balanced
    TASK_TRACKING(DiagnosticsLevel.BALANCED, "Asynchronous task lifecycle tracking"),
    JAVAFX_DIAGNOSTICS(DiagnosticsLevel.BALANCED, "UI thread responsiveness and lag diagnostics"),
    WEBVIEW_DIAGNOSTICS(DiagnosticsLevel.BALANCED, "Monaco WebView load times and JavaScript errors"),
    DATABASE_DIAGNOSTICS(DiagnosticsLevel.BALANCED, "Sanitized connection and query timing diagnostics"),

    // Level 3 - Advanced
    FLIGHT_RECORDER(DiagnosticsLevel.ADVANCED, "JDK Flight Recorder (JFR) profiling sessions"),
    CPU_PROFILING(DiagnosticsLevel.ADVANCED, "Deep stack trace sampling and thread profiling"),
    DEADLOCK_DETECTION(DiagnosticsLevel.ADVANCED, "Active JVM deadlock analysis and lock graph");

    private final DiagnosticsLevel minimumLevel;
    private final String description;

    DiagnosticsFeature(DiagnosticsLevel minimumLevel, String description) {
        this.minimumLevel = minimumLevel;
        this.description = description;
    }

    public DiagnosticsLevel getMinimumLevel() {
        return minimumLevel;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSupportedAt(DiagnosticsLevel currentLevel) {
        if (currentLevel == null || currentLevel == DiagnosticsLevel.OFF) {
            return false;
        }
        return currentLevel.isAtLeast(this.minimumLevel);
    }
}

