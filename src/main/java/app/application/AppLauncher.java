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
        app.framework.error.DesktopErrorHandler.getInstance().install();
        app.framework.bootstrap.DesktopBootstrap.getInstance();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        AppContext.getInstance().setPrimaryStage(primaryStage);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-window.fxml"));
        Parent root = loader.load();
        app.controllers.MainController mainController = loader.getController();

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // Initialize Theme via ThemeService
        ThemeService.getInstance().initializeTheme(scene);

        primaryStage.setTitle(APP_TITLE);
        addStageIcons(primaryStage);

        // Initialize Custom Window with decoupled TitleBar & WindowController architecture
        app.ui.window.CustomWindow customWindow = new app.ui.window.CustomWindow(primaryStage);
        customWindow.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT, 960, 620);
        customWindow.setScene(scene, mainController.getTitleBarNode(), mainController.getTitleBarController());
        customWindow.show();
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
