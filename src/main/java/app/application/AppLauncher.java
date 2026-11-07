package app.application;

import app.services.ThemeService;
import app.utils.WindowResizeHelper;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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
        try (var task = profiler.beginTask("DesktopBootstrap")) {
            app.framework.bootstrap.DesktopBootstrap.getInstance();
        }
        // S3: log level + rolling log file from Settings (logging.*) before anything else logs much
        try (var task = profiler.beginTask("Logging_Apply")) {
            app.services.LoggingService.apply(app.services.SettingsService.getInstance().getCurrentSettings());
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
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
        addStageIcons(primaryStage);

        // Initialize Custom Window with decoupled TitleBar & WindowController architecture
        app.ui.window.CustomWindow customWindow = new app.ui.window.CustomWindow(primaryStage);
        customWindow.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT, 960, 620);
        customWindow.setScene(scene, mainController.getTitleBarNode(), mainController.getTitleBarController());
        customWindow.show();
        app.diagnostics.startup.StartupProfiler.getInstance().markAppReady();
    }

    /** Product logo for the taskbar, Alt+Tab and the window system menu. */
    private void addStageIcons(Stage stage) {
        for (int size : new int[] {16, 32, 48, 128, 256}) {
            var url = getClass().getResource("/static/icons/app-logo-" + size + ".png");
            if (url != null) {
                stage.getIcons().add(new javafx.scene.image.Image(url.toExternalForm()));
            }
        }
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
