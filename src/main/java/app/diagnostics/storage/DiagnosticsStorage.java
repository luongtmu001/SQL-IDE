package app.diagnostics.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages directories for diagnostic sessions, reports, and exported bundles.
 */
public final class DiagnosticsStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticsStorage.class);
    private static final DiagnosticsStorage INSTANCE = new DiagnosticsStorage();

    private final Path rootDir;
    private final Path sessionsDir;
    private final Path reportsDir;
    private final Path exportsDir;

    private DiagnosticsStorage() {
        this(Paths.get(System.getProperty("user.home"), ".sqlide", "diagnostics"));
    }

    public DiagnosticsStorage(Path rootDir) {
        this.rootDir = rootDir;
        this.sessionsDir = rootDir.resolve("sessions");
        this.reportsDir = rootDir.resolve("reports");
        this.exportsDir = rootDir.resolve("exports");
        ensureDirectories();
    }

    public static DiagnosticsStorage getInstance() {
        return INSTANCE;
    }

    public void ensureDirectories() {
        try {
            Files.createDirectories(rootDir);
            Files.createDirectories(sessionsDir);
            Files.createDirectories(reportsDir);
            Files.createDirectories(exportsDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create diagnostics directories: {}", e.getMessage());
        }
    }

    public Path getRootDir() { return rootDir; }
    public Path getSessionsDir() { return sessionsDir; }
    public Path getReportsDir() { return reportsDir; }
    public Path getExportsDir() { return exportsDir; }
}

