package com.lltool.infrastructure.filesystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standard Application Directory Manager (Phase 3.4).
 * Resolves standard cross-platform paths for LLTool:
 * - App Root: %APPDATA%/LLTool-Desktop (see {@link #createDefault()})
 * - Config: <root>/config   (settings.json, vault.enc)
 * - Data: <root>/data       (connections/{id}.json)
 * - Logs: <root>/logs
 * - Cache: <root>/cache
 */
public class AppDirectoryManager {

    private static final Logger log = LoggerFactory.getLogger(AppDirectoryManager.class);

    private final Path rootDir;
    private final Path configDir;
    private final Path dataDir;
    private final Path logsDir;
    private final Path cacheDir;

    public AppDirectoryManager(Path rootDir) {
        this.rootDir = rootDir;
        this.configDir = rootDir.resolve("config");
        this.dataDir = rootDir.resolve("data");
        this.logsDir = rootDir.resolve("logs");
        this.cacheDir = rootDir.resolve("cache");
        ensureDirectories();
    }

    /** Folder name under %APPDATA% (Windows) used by SQL-IDE. Never {@code LLTool}: that one belongs to the Python app. */
    public static final String DEFAULT_FOLDER_NAME = "LLTool-Desktop";

    /**
     * Default location of the user's data (connections, vault, settings, logs): %APPDATA%\LLTool-Desktop on Windows,
     * ~/.local/share/LLTool-Desktop elsewhere. Override with -Dsqlide.dataDir=... or the SQLIDE_DATA_DIR variable.
     * On first start the old ~/.lltool folder is copied (not moved) into the new location.
     */
    public static AppDirectoryManager createDefault() {
        Path base = resolveDefaultRoot();
        migrateLegacyIfNeeded(base);
        return new AppDirectoryManager(base);
    }

    static Path resolveDefaultRoot() {
        String override = System.getProperty("sqlide.dataDir");
        if (override == null || override.isBlank()) {
            override = System.getenv("SQLIDE_DATA_DIR");
        }
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Paths.get(appData, DEFAULT_FOLDER_NAME);
        }
        return Paths.get(System.getProperty("user.home", "."), ".local", "share", DEFAULT_FOLDER_NAME);
    }

    /** Copies ~/.lltool/{config,data} to {@code base} once, when {@code base} does not exist yet. */
    static void migrateLegacyIfNeeded(Path base) {
        try {
            if (Files.exists(base)) {
                return;
            }
            Path legacy = Paths.get(System.getProperty("user.home", "."), ".lltool");
            if (!Files.isDirectory(legacy)) {
                return;
            }
            for (String sub : new String[]{"config", "data"}) {
                Path from = legacy.resolve(sub);
                if (Files.isDirectory(from)) {
                    copyTree(from, base.resolve(sub));
                }
            }
            log.info("Copied legacy data from {} to {}", legacy, base);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not copy legacy data from ~/.lltool: {}", e.getMessage());
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    public static AppDirectoryManager createIsolated(Path tempRoot) {
        return new AppDirectoryManager(tempRoot);
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(configDir);
            Files.createDirectories(dataDir);
            Files.createDirectories(logsDir);
            Files.createDirectories(cacheDir);
            log.debug("App directories ensured at root: {}", rootDir);
        } catch (IOException e) {
            log.error("Failed to initialize app directories at {}: {}", rootDir, e.getMessage(), e);
            throw new RuntimeException("Cannot initialize application directories", e);
        }
    }

    public Path getRootDir() {
        return rootDir;
    }

    public Path getConfigDir() {
        return configDir;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getLogsDir() {
        return logsDir;
    }

    public Path getCacheDir() {
        return cacheDir;
    }
}

