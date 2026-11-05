package app;

import app.ui.editor.MonacoEditorHost;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MonacoEditorHostTest {

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testMonacoEditorHostCreationAndModelManagement() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                MonacoEditorHost host = new MonacoEditorHost();
                assertNotNull(host.getWebView(), "Host must contain WebView");

                // Test creating models before webview is even ready (should cache gracefully)
                host.createModel("doc_1", "SELECT 1;", "sql");
                host.createModel("doc_2", "SELECT 2;", "sql");

                // Check content from cache
                assertEquals("SELECT 1;", host.getContent("doc_1"));
                assertEquals("SELECT 2;", host.getContent("doc_2"));

                // Switch model
                host.switchModel("doc_2");

                // Update content
                host.updateContent("doc_1", "SELECT 100;");
                assertEquals("SELECT 100;", host.getContent("doc_1"));

                // Theme and options
                host.setTheme("ide-light");
                host.setFontSize(14);
                host.setReadOnly(true);

                // Dispose model
                host.disposeModel("doc_2");

                success.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(success.get());
    }

    @Test
    void testMonacoEditorHostListeners() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                MonacoEditorHost host = new MonacoEditorHost();

                AtomicBoolean contentChanged = new AtomicBoolean(false);
                host.setOnContentChanged((id, content) -> contentChanged.set(true));

                AtomicBoolean cursorMoved = new AtomicBoolean(false);
                host.setOnCursorPositionChanged((line, col) -> cursorMoved.set(true));

                AtomicBoolean readyCalled = new AtomicBoolean(false);
                host.setOnReady(() -> readyCalled.set(true));

                // Directly invoke bridge callback
                MonacoEditorHost.BridgeCallback callback = host.new BridgeCallback();
                callback.onContentChanged("doc_1", "SELECT 999;");
                callback.onCursorPositionChanged(10, 5);

                Platform.runLater(() -> {
                    assertTrue(contentChanged.get());
                    assertTrue(cursorMoved.get());
                    success.set(true);
                    latch.countDown();
                });
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(success.get());
    }
}

