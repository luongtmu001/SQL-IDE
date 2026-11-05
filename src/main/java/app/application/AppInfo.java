package app.application;

/** Product identity shown in About / update check (Phase 6). Mock values until Gate D. */
public final class AppInfo {

    public static final String NAME = "SQL IDE";
    public static final String TAGLINE = "Modern SQL database IDE & developer suite";
    public static final String VERSION = "1.0.0-SNAPSHOT";
    public static final String CHANNEL = "Preview";
    public static final String COPYRIGHT = "© 2026 luonght. All rights reserved.";

    private AppInfo() {
    }

    public static String javaRuntime() {
        return "Java " + System.getProperty("java.version", "?") + " · JavaFX " + System.getProperty("javafx.version", "?");
    }

    public static String platform() {
        return System.getProperty("os.name", "?") + " " + System.getProperty("os.arch", "");
    }
}
