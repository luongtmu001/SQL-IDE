package com.lltool.infrastructure.configuration.service;

import com.lltool.infrastructure.configuration.model.AppConfig;
import com.lltool.infrastructure.configuration.model.RootConfig;
import com.lltool.infrastructure.configuration.model.UiConfig;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationServiceTest {

    @Test
    @DisplayName("ConfigurationService saves, reloads, and retrieves typed configuration")
    void testSaveAndLoadConfig(@TempDir Path tempDir) {
        AppDirectoryManager dirManager = AppDirectoryManager.createIsolated(tempDir);
        ConfigurationService service = new ConfigurationService(dirManager);

        RootConfig initial = service.getConfig();
        assertEquals("Primer Dark", initial.ui().theme());

        RootConfig updated = new RootConfig(
                2,
                new AppConfig("SQL IDE Pro", "staging", false, 60),
                new UiConfig("Dracula", 14.0, "normal", false, true)
        );
        service.updateConfig(updated);

        // Reload to test persistence
        service.reload();
        RootConfig reloaded = service.getConfig();
        assertEquals(2, reloaded.configVersion());
        assertEquals("SQL IDE Pro", reloaded.app().appName());
        assertEquals("Dracula", reloaded.ui().theme());
        assertEquals(14.0, reloaded.ui().editorFontSize());
    }
}

