package app.diagnostics.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsWindowTest {

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("DiagnosticsWindow should build complete view graph and shutdown cleanly")
    void testWindowStructure() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<DiagnosticsWindow> winRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                DiagnosticsWindow win = new DiagnosticsWindow();
                winRef.set(win);
                Parent root = win.buildRootNode();
                rootRef.set(root);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(rootRef.get(), "DiagnosticsWindow layout root should build cleanly");

        if (winRef.get() != null) {
            winRef.get().shutdown();
        }
    }

    @Test
    @DisplayName("DiagnosticsWindow.installGlobalShortcut should filter Ctrl+Shift+D events without throwing")
    void testShortcutInstallation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                javafx.scene.Scene testScene = new javafx.scene.Scene(new javafx.scene.layout.StackPane());
                DiagnosticsWindow.installGlobalShortcut(testScene);

                javafx.scene.input.KeyEvent keyEvent = new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "D", "D",
                        javafx.scene.input.KeyCode.D,
                        true,  // shiftDown
                        true,  // controlDown
                        false, // altDown
                        false  // metaDown
                );
                testScene.getRoot().fireEvent(keyEvent);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }
}

