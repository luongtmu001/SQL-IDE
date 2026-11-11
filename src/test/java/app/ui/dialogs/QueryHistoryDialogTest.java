package app.ui.dialogs;

import app.services.HistoryLoggingService;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class QueryHistoryDialogTest {

    @TempDir
    static Path tempDir;

    private static final AtomicBoolean toolkitInitialized = new AtomicBoolean(false);

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            toolkitInitialized.set(true);
        } catch (IllegalStateException e) {
            latch.countDown();
            toolkitInitialized.set(true);
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX Platform should start within 5s");
        app.framework.bootstrap.DesktopBootstrap.getInstance();

        Path dbFile = tempDir.resolve("test_dialog_history.db");
        HistoryLoggingService.setCustomInstance(new HistoryLoggingService(dbFile));
    }

    @Test
    void testQueryHistoryDialogShowAndClose() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<QueryHistoryDialog> dialogRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                // Pre-populate an execution
                HistoryLoggingService.getInstance().recordExecution(
                        "tab-test-1", "test.sql", "SELECT 100;", "SRV", "DB", null, null, false
                );

                QueryHistoryDialog dlg = QueryHistoryDialog.show(null, sql -> {});
                dialogRef.set(dlg);
            } catch (Throwable t) {
                errorRef.set(t);
                t.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Timeout waiting for QueryHistoryDialog.show()");
        if (errorRef.get() != null) {
            fail("Exception inside JavaFX thread: " + errorRef.get().getMessage(), errorRef.get());
        }

        assertNotNull(dialogRef.get());
        assertNotNull(dialogRef.get().getChrome());

        // Close cleanly on FX thread
        CountDownLatch closeLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            dialogRef.get().close();
            closeLatch.countDown();
        });
        assertTrue(closeLatch.await(3, TimeUnit.SECONDS));
    }
}
