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
 * - App Root: ~/.lltool or %APPDATA%/LLTool
 * - Config: ~/.lltool/config
 * - Data: ~/.lltool/data
 * - Logs: ~/.lltool/logs
 * - Cache: ~/.lltool/cache
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

    public static AppDirectoryManager createDefault() {
        String userHome = System.getProperty("user.home", ".");
        Path base = Paths.get(userHome, ".lltool");
        return new AppDirectoryManager(base);
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

