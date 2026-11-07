package app.diagnostics.core;

/**
 * Operating and debug levels for the Diagnostics Center.
 */
public enum DiagnosticsLevel {
    /**
     * Diagnostics disabled or baseline background monitoring only.
     */
    OFF(0, "Off", "Background baseline only"),

    /**
     * Level 1: Basic application diagnostics (Uptime, CPU, Memory, Threads, Logs).
     */
    BASIC(1, "Basic", "Basic application diagnostics (Uptime, CPU, Memory, Threads, Logs)"),

    /**
     * Level 2: Balanced diagnostics (Tasks, JavaFX responsiveness, WebView, Database).
     */
    BALANCED(2, "Balanced", "Task, JavaFX, WebView, Database diagnostics"),

    /**
     * Level 3: Advanced profiling (Java Flight Recorder, CPU profiling, Deadlock detection).
     */
    ADVANCED(3, "Advanced", "JFR, profiling, deadlock analysis");

    private final int code;
    private final String displayName;
    private final String description;

    DiagnosticsLevel(int code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this level is at least as high as the required level.
     */
    public boolean isAtLeast(DiagnosticsLevel required) {
        if (required == null) {
            return false;
        }
        return this.code >= required.code;
    }

    /**
     * Parse code safely with a default fallback to BASIC.
     */
    public static DiagnosticsLevel fromCode(int code) {
        for (DiagnosticsLevel level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        return BASIC;
    }
}

