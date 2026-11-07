package app.services;

import app.framework.bootstrap.DesktopBootstrap;
import app.models.IdeSettings;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lltool.infrastructure.configuration.model.AppConfig;
import com.lltool.infrastructure.configuration.model.RootConfig;
import com.lltool.infrastructure.configuration.model.UiConfig;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service managing IDE configuration settings, persistence, and runtime Hot-Apply.
 * Fully compliant with Feature 8 & Section 4 of feature_08_settings_and_themes_analysis.md.
 */
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final ObjectMapper mapper = IdeSettings.jsonMapper(); // fields only (see IdeSettings.jsonMapper)

    private static final SettingsService INSTANCE = new SettingsService();

    private final ObjectProperty<IdeSettings> settingsProperty = new SimpleObjectProperty<>(IdeSettings.createDefault());
    private final List<Consumer<IdeSettings>> settingsListeners = new ArrayList<>();
    private Path settingsFilePath;

    /** Location of settings.json (other config files such as sql-styles.json live next to it). */
    public Path getSettingsFilePath() {
        return settingsFilePath;
    }

    private SettingsService() {
        resolveSettingsPath();
        loadSettings();
    }

    public static SettingsService getInstance() {
        return INSTANCE;
    }

    /**
     * The IDE settings live in their own file. {@code settings.json} in the same folder belongs to the
     * infrastructure {@code ConfigurationService} (RootConfig): it reads that file at start-up, fails on the IDE
     * format, falls back to its defaults and writes them back, so IDE settings stored there were lost on every
     * restart (found 2026-11-06).
     */
    static final String SETTINGS_FILE = "ide-settings.json";

    private void resolveSettingsPath() {
        try {
            AppDirectoryManager dirManager = DesktopBootstrap.getInstance().getDirectoryManager();
            this.settingsFilePath = dirManager.getConfigDir().resolve(SETTINGS_FILE);
        } catch (Exception e) {
            String userHome = System.getProperty("user.home");
            this.settingsFilePath = Path.of(userHome, ".lltool", "config", SETTINGS_FILE);
        }
    }

    public synchronized IdeSettings loadSettings() {
        IdeSettings loaded = null;
        try {
            if (settingsFilePath != null && Files.exists(settingsFilePath)) {
                JsonNode root = mapper.readTree(settingsFilePath.toFile());
                loaded = parseJsonToSettings(root);
                log.info("Loaded IDE settings from: {}", settingsFilePath);
            }
        } catch (Exception e) {
            log.warn("Failed to load settings from {}: {}. Using defaults.", settingsFilePath, e.getMessage());
        }

        if (loaded == null && settingsFilePath != null && !Files.exists(settingsFilePath)) {
            // first start of the Java IDE: take the user's LLTool settings once (same settings.json format)
            loaded = importFromLltool();
            if (loaded != null && !isTestRun()) {
                try {
                    SettingsFiles.writeAtomically(settingsFilePath, mapper.writeValueAsString(loaded));
                } catch (Exception e) {
                    log.warn("Could not save the imported LLTool settings: {}", e.getMessage());
                }
            }
        }

        if (loaded == null) {
            loaded = IdeSettings.createDefault();
        }

        this.settingsProperty.set(loaded);
        return loaded;
    }

    // ------------------------------------------------------------------ LLTool import (S2)

    /** Reads {@code %APPDATA%\LLTool\settings.json}; null when there is none or it cannot be read. */
    private IdeSettings importFromLltool() {
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null) return null;
            Path file = Path.of(appData, "LLTool", "settings.json");
            if (!Files.isRegularFile(file)) return null;
            IdeSettings s = SettingsFiles.fromLltoolJson(mapper.readTree(Files.readString(file)));
            log.info("Imported LLTool settings from {} (theme -> {})", file, s.getTheme());
            return s;
        } catch (Exception e) {
            log.warn("Could not import LLTool settings: {}", e.getMessage());
            return null;
        }
    }

    /** Unit tests run the services without the UI: they must never write the user's real settings. */
    private static boolean isTestRun() {
        return "mock".equalsIgnoreCase(System.getProperty("sqlide.backend"))
                || System.getProperty("surefire.test.class.path") != null;
    }

    public synchronized boolean saveSettings(IdeSettings newSettings) {
        if (newSettings == null) return false;

        try {
            if (settingsFilePath != null && !isTestRun()) {
                SettingsFiles.writeAtomically(settingsFilePath, mapper.writeValueAsString(newSettings));
                log.info("Saved IDE settings successfully to: {}", settingsFilePath);
            }

            // Sync with ConfigurationService if available
            try {
                RootConfig currentRoot = DesktopBootstrap.getInstance().getConfigurationService().getConfig();
                UiConfig newUi = new UiConfig(
                        newSettings.getTheme(),
                        newSettings.getEditorFontSize(),
                        newSettings.getDensity(),
                        newSettings.isShowLineNumbers(),
                        newSettings.isWordWrap()
                );
                AppConfig newApp = new AppConfig(
                        newSettings.getAppName(),
                        newSettings.getEnvironment(),
                        newSettings.isAutoSave(),
                        newSettings.getAutoSaveIntervalSeconds()
                );
                DesktopBootstrap.getInstance().getConfigurationService().updateConfig(
                        new RootConfig(currentRoot.configVersion(), newApp, newUi)
                );
            } catch (Exception ex) {
                log.debug("ConfigurationService sync skipped: {}", ex.getMessage());
            }

            this.settingsProperty.set(newSettings.clone());
            applySettings(newSettings);
            return true;
        } catch (Exception e) {
            log.error("Failed to save settings: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Triggers hot-apply across all registered components.
     */
    public void applySettings(IdeSettings settings) {
        if (settings == null) return;
        LoggingService.apply(settings); // S3: logging.level / enabled / file size / file count

        // 1. Hot-apply theme if changed
        try {
            // T1: id of a built-in or imported theme (old display names still resolve)
            var target = app.services.theme.ThemeRegistry.getInstance().find(settings.getTheme());
            if (!target.id().equals(ThemeService.getInstance().getCurrentDefinition().id())) {
                Platform.runLater(() -> {
                    // with the main scene, so its light/dark stylesheets switch too (null only swapped the base theme)
                    var stage = app.application.AppContext.getInstance().getPrimaryStage();
                    ThemeService.getInstance().apply(target, stage != null ? stage.getScene() : null);
                });
            }
        } catch (Exception e) {
            log.warn("Theme hot-apply error: {}", e.getMessage());
        }

        // 2. Notify registered component listeners
        List<Consumer<IdeSettings>> copy;
        synchronized (settingsListeners) {
            copy = new ArrayList<>(settingsListeners);
        }
        for (Consumer<IdeSettings> listener : copy) {
            try {
                Platform.runLater(() -> listener.accept(settings));
            } catch (Exception e) {
                log.warn("Error notifying settings listener: {}", e.getMessage());
            }
        }
    }

    public void addSettingsListener(Consumer<IdeSettings> listener) {
        if (listener != null) {
            synchronized (settingsListeners) {
                settingsListeners.add(listener);
            }
        }
    }

    public void removeSettingsListener(Consumer<IdeSettings> listener) {
        synchronized (settingsListeners) {
            settingsListeners.remove(listener);
        }
    }

    public IdeSettings getCurrentSettings() {
        return settingsProperty.get();
    }

    public ObjectProperty<IdeSettings> settingsProperty() {
        return settingsProperty;
    }

    private IdeSettings parseJsonToSettings(JsonNode root) {
        IdeSettings s = new IdeSettings();
        if (root == null) return s;

        // Try direct deserialization
        try {
            return mapper.treeToValue(root, IdeSettings.class);
        } catch (Exception e) {
            // Fallback: Parse sectioned schema
            if (root.has("theme")) s.setTheme(root.get("theme").asText(s.getTheme()));

            if (root.has("appearance")) {
                JsonNode a = root.get("appearance");
                if (a.has("theme")) s.setTheme(a.get("theme").asText(s.getTheme()));
                if (a.has("density")) s.setDensity(a.get("density").asText(s.getDensity()));
                if (a.has("panelStyle")) s.getAppearance().setPanelStyle(a.get("panelStyle").asText("rounded"));
                if (a.has("uiFontFamily")) s.setUiFontFamily(a.get("uiFontFamily").asText(s.getUiFontFamily()));
                if (a.has("uiFontSize")) s.setUiFontSize(a.get("uiFontSize").asInt(s.getUiFontSize()));
            }

            if (root.has("editor")) {
                JsonNode ed = root.get("editor");
                if (ed.has("fontFamily")) s.setEditorFontFamily(ed.get("fontFamily").asText(s.getEditorFontFamily()));
                if (ed.has("fontSize")) s.setEditorFontSize(ed.get("fontSize").asInt(s.getEditorFontSize()));
                if (ed.has("tabSize")) s.setTabSize(ed.get("tabSize").asInt(s.getTabSize()));
                if (ed.has("insertSpaces")) s.setInsertSpaces(ed.get("insertSpaces").asBoolean(s.isInsertSpaces()));
                if (ed.has("wordWrap")) s.setWordWrap(ed.get("wordWrap").asBoolean(s.isWordWrap()));
                if (ed.has("showLineNumbers")) s.setShowLineNumbers(ed.get("showLineNumbers").asBoolean(s.isShowLineNumbers()));
                if (ed.has("minimap")) s.setMinimap(ed.get("minimap").asBoolean(s.isMinimap()));
                if (ed.has("intellisense")) s.setIntellisense(ed.get("intellisense").asBoolean(s.isIntellisense()));
                if (ed.has("keywordCase")) s.setKeywordCase(ed.get("keywordCase").asText(s.getKeywordCase()));
            }

            if (root.has("grid")) {
                JsonNode g = root.get("grid");
                if (g.has("maxRows")) s.setMaxRows(g.get("maxRows").asInt(s.getMaxRows()));
                if (g.has("fontSize")) s.setGridFontSize(g.get("fontSize").asInt(s.getGridFontSize()));
                if (g.has("nullDisplayString")) s.setNullDisplayString(g.get("nullDisplayString").asText(s.getNullDisplayString()));
                if (g.has("showRowNumbers")) s.setShowRowNumbers(g.get("showRowNumbers").asBoolean(s.isShowRowNumbers()));
            }

            if (root.has("query")) {
                JsonNode q = root.get("query");
                if (q.has("timeoutSeconds")) s.setQueryTimeoutSeconds(q.get("timeoutSeconds").asInt(s.getQueryTimeoutSeconds()));
                if (q.has("stopOnError")) s.setStopOnError(q.get("stopOnError").asBoolean(s.isStopOnError()));
                if (q.has("autoShowResultsPanel")) s.setAutoShowResultsPanel(q.get("autoShowResultsPanel").asBoolean(s.isAutoShowResultsPanel()));
            }

            return s;
        }
    }
}

