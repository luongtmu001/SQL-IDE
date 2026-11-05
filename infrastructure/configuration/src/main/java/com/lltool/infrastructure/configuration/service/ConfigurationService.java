package com.lltool.infrastructure.configuration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lltool.infrastructure.configuration.model.RootConfig;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import com.lltool.infrastructure.filesystem.AtomicFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration Service (Phase 3.2).
 * Manages loading, caching, and atomic updating of persistent JSON configuration.
 */
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);
    private static final String CONFIG_FILENAME = "settings.json";

    private final Path configFile;
    private final ObjectMapper mapper;
    private RootConfig currentConfig;

    public ConfigurationService(AppDirectoryManager directoryManager) {
        Objects.requireNonNull(directoryManager, "directoryManager cannot be null");
        this.configFile = directoryManager.getConfigDir().resolve(CONFIG_FILENAME);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    public synchronized RootConfig getConfig() {
        if (currentConfig == null) {
            load();
        }
        return currentConfig;
    }

    public synchronized void updateConfig(RootConfig newConfig) {
        this.currentConfig = Objects.requireNonNull(newConfig, "newConfig cannot be null");
        save();
    }

    public synchronized void reload() {
        load();
    }

    private void load() {
        if (Files.exists(configFile)) {
            try {
                this.currentConfig = mapper.readValue(configFile.toFile(), RootConfig.class);
                log.info("Loaded configuration from {}", configFile);
                return;
            } catch (IOException e) {
                log.warn("Failed to parse configuration at {}, falling back to defaults: {}", configFile, e.getMessage());
            }
        }
        this.currentConfig = RootConfig.defaultRootConfig();
        save();
    }

    private void save() {
        try {
            String json = mapper.writeValueAsString(currentConfig);
            AtomicFileWriter.writeString(configFile, json);
            log.debug("Saved configuration to {}", configFile);
        } catch (IOException e) {
            log.error("Failed to save configuration to {}: {}", configFile, e.getMessage(), e);
        }
    }
}

