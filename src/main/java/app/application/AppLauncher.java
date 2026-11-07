package app.application;

import app.services.ThemeService;
import app.ui.splash.SplashWindow;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppLauncher extends Application {

    private static final String APP_TITLE = "SQL IDE";
    private static final double DEFAULT_WIDTH = 1280;
    private static final double DEFAULT_HEIGHT = 820;

    @Override
    public void init() throws Exception {
        super.init();
        var profiler = app.diagnostics.startup.StartupProfiler.getInstance();
        profiler.markAppStarted();
        try (var task = profiler.beginTask("DiagnosticsManager_Init")) {
            app.diagnostics.core.DiagnosticsManager.getInstance().initialize(null);
        }
        app.framework.error.DesktopErrorHandler.getInstance().install();
    }

    @Override
    public void start(Stage primaryStage) {
        // Never the main window's title: WindowsWindowAdapter finds that window by its title.
        // Closed from the taskbar while loading: the user is leaving, nothing else keeps the app from exiting.
        SplashWindow splash = new SplashWindow(savedThemeIsDark(), AppInfo.NAME + " - đang khởi động", AppInfo.NAME,
                AppInfo.VERSION, javafx.application.Platform::exit);
        new StartupSequence(splash, this::loadBackend, () -> buildMainWindow(primaryStage), this::openLogsFolder).begin();
    }

    /** Background thread (same as {@code init()} before the splash existed). */
    private void loadBackend(java.util.function.IntConsumer onStep) {
        var profiler = app.diagnostics.startup.StartupProfiler.getInstance();
        app.framework.bootstrap.DesktopBootstrap.setProgressListener(onStep);
        try (var task = profiler.beginTask("DesktopBootstrap")) {
            app.framework.bootstrap.DesktopBootstrap.getInstance();
        } finally {
            app.framework.bootstrap.DesktopBootstrap.setProgressListener(null);
        }
        // S3: log level + rolling log file from Settings (logging.*) before anything else logs much
        try (var task = profiler.beginTask("Logging_Apply")) {
            app.services.LoggingService.apply(app.services.SettingsService.getInstance().getCurrentSettings());
        }
    }

    /**
     * Reads only the theme key of the settings file: loading SettingsService or ThemeRegistry would run the whole
     * bootstrap on the FX thread before the splash is on screen. An imported theme is judged by its name.
     */
    private static boolean savedThemeIsDark() {
        try {
            Path file = AppDirectoryManager.createDefault().getConfigDir().resolve("ide-settings.json");
            if (!Files.isRegularFile(file)) return true;
            Matcher m = Pattern.compile("\"theme\"\\s*:\\s*\"([^\"]+)\"").matcher(Files.readString(file));
            if (!m.find()) return true;
            String saved = m.group(1);
            for (ThemeService.AppTheme t : ThemeService.AppTheme.values()) {
                if (saved.equalsIgnoreCase(t.name()) || saved.equalsIgnoreCase(t.getDisplayName())) return t.isDark();
            }
            return !saved.toLowerCase(Locale.ROOT).contains("light");
        } catch (Exception e) {
            return true;
        }
    }

    private void openLogsFolder() {
        Thread opener = new Thread(() -> {
            try {
                java.awt.Desktop.getDesktop().open(AppDirectoryManager.createDefault().getLogsDir().toFile());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AppLauncher.class).warn("Could not open the logs folder: {}", e.getMessage());
            }
        }, "sqlide-open-logs");
        opener.setDaemon(true);
        opener.start();
    }

    /** Builds the IDE window without showing it. */
    private StartupSequence.MainWindow buildMainWindow(Stage primaryStage) throws Exception {
        AppContext.getInstance().setPrimaryStage(primaryStage);
        // the saved theme is the theme from the first frame (was always Primer Dark until Settings were saved)
        try {
            String saved = app.services.SettingsService.getInstance().getCurrentSettings().getTheme();
            ThemeService.getInstance().apply(app.services.theme.ThemeRegistry.getInstance().find(saved), null);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AppLauncher.class).warn("Saved theme not applied: {}", e.getMessage());
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-window.fxml"));
        Parent root;
        try (var task = app.diagnostics.startup.StartupProfiler.getInstance().beginTask("FXML_MainWindow_Load")) {
            root = loader.load();
        }
        app.controllers.MainController mainController = loader.getController();

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // Initialize Theme via ThemeService
        ThemeService.getInstance().initializeTheme(scene);

        // Global shortcut to open Application Diagnostics & Debug Center (Ctrl+Shift+D / Ctrl+Alt+D)
        app.diagnostics.ui.DiagnosticsWindow.installGlobalShortcut(scene);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.getIcons().setAll(stageIcons());

        // Initialize Custom Window with decoupled TitleBar & WindowController architecture
        app.ui.window.CustomWindow customWindow = new app.ui.window.CustomWindow(primaryStage);
        customWindow.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT, 960, 620);
        customWindow.setScene(scene, mainController.getTitleBarNode(), mainController.getTitleBarController());

        // Laid out at its real size while hidden, so the editor is not created at 0x0 and the first frame is complete
        root.applyCss();
        root.layout();

        return new StartupSequence.MainWindow() {
            @Override
            public void whenReady(Runnable callback) {
                mainController.whenEditorReady(callback);
            }

            @Override
            public void show(boolean minimized) {
                primaryStage.setIconified(minimized); // the user hid the splash: do not jump in front of their work
                customWindow.show();
                app.diagnostics.startup.StartupProfiler.getInstance().markAppReady();
            }
        };
    }

    /** Product logo for the taskbar, Alt+Tab and the window system menu. */
    private List<Image> stageIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] {16, 32, 48, 128, 256}) {
            var url = getClass().getResource("/static/icons/app-logo-" + size + ".png");
            if (url != null) {
                icons.add(new Image(url.toExternalForm()));
            }
        }
        return icons;
    }

    @Override
    public void stop() throws Exception {
        try {
            try {
                app.diagnostics.core.DiagnosticsManager.getInstance().shutdown();
            } catch (Throwable ignored) {}
            app.framework.lifecycle.DesktopLifecycleManager.getInstance().initiateShutdown();
            try {
                app.framework.connection.ConnectionSessionManager.getInstance().disconnect();
                app.framework.session.TabSessionManager.getInstance().closeAll();
                app.framework.metadata.DesktopMetadataService.getInstance().closeAllConnections();
            } catch (Throwable ignored) {
                // Best effort: never block shutdown on connection cleanup.
            }
        } finally {
            super.stop();
            startExitWatchdog();
        }
    }

    /**
     * Phase 0 / T2: the JVM sometimes stayed alive after the window closed (leaving the
     * run-ui.cmd console open). Give normal shutdown a few seconds, then report the
     * non-daemon threads still running and force the process to exit.
     */
    private static void startExitWatchdog() {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            StringBuilder alive = new StringBuilder();
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.isAlive() && !t.isDaemon() && t != Thread.currentThread()) {
                    alive.append(t.getName()).append("; ");
                }
            }
            System.err.println("[Shutdown] Forcing exit. Non-daemon threads still alive: " + alive);
            Runtime.getRuntime().halt(0);
        }, "sqlide-exit-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
