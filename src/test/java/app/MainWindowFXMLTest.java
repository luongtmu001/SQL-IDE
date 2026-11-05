package app;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MainWindowFXMLTest {

    private static final AtomicBoolean toolkitInitialized = new AtomicBoolean(false);

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            toolkitInitialized.set(true);
        } catch (IllegalStateException e) {
            // Already initialized
            latch.countDown();
            toolkitInitialized.set(true);
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX Platform should start within 5s");
        app.framework.bootstrap.DesktopBootstrap.getInstance();
    }

    @Test
    void testMainWindowFXMLLoadsSuccessfully() throws Exception {
        URL fxmlUrl = getClass().getResource("/fxml/main-window.fxml");
        assertNotNull(fxmlUrl, "main-window.fxml must be located on the classpath");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean loadSuccess = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                assertNotNull(root, "Loaded root node must not be null");
                assertNotNull(loader.getController(), "Controller must be instantiated");
                loadSuccess.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(8, TimeUnit.SECONDS), "FXML loading should finish within 8s");
        assertTrue(loadSuccess.get(), "FXML should load without any exception");
    }
}

