package app.ui.dialogs;

import app.services.ThemeService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ModalDialogsTransparencyTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("MessageDialog must have solid opaque backgrounds and correct child order: titlebar -> body -> footer")
    void testMessageDialogSolidBackgroundAndOrder() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                DialogChrome msgChrome = MessageDialog.build(null, MessageDialog.Kind.INFO, "Thông báo", "Tiêu đề thông báo", "Nội dung chi tiết thông báo", null, null, false, null);
                var scene = msgChrome.stage().getScene();
                StackPane root = (StackPane) scene.getRoot();
                root.applyCss();
                root.layout();

                StackPane clipHost = (StackPane) root.getChildren().get(0);
                VBox shell = (VBox) clipHost.getChildren().get(0);

                // ClipHost and Shell must have solid background
                assertNotNull(clipHost.getBackground(), "clipHost must have solid background");
                assertNotNull(shell.getBackground(), "shell must have solid background");

                // Correct child order
                assertEquals(3, shell.getChildren().size());
                assertTrue(shell.getChildren().get(0).getStyleClass().contains("dialog-titlebar"), "Child[0] must be titlebar");
                assertTrue(shell.getChildren().get(1).getStyleClass().contains("dialog-body"), "Child[1] must be dialog-body");
                assertTrue(shell.getChildren().get(2).getStyleClass().contains("dialog-footer"), "Child[2] must be dialog-footer");

                // Children must have solid backgrounds
                Pane titleBar = (Pane) shell.getChildren().get(0);
                Pane body = (Pane) shell.getChildren().get(1);
                Pane footer = (Pane) shell.getChildren().get(2);

                assertNotNull(titleBar.getBackground(), "Titlebar must have non-null background");
                assertNotNull(body.getBackground(), "Body must have non-null background");
                assertNotNull(footer.getBackground(), "Footer must have non-null background");

            } catch (Throwable t) {
                fail(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("ConfirmDialog must have solid opaque backgrounds across titlebar, body, and footer")
    void testConfirmDialogSolidBackground() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                DialogChrome confirmChrome = ConfirmDialog.build(null, "Xác nhận xóa", "Bạn có chắc chắn muốn xóa không?", "Thao tác này không thể hoàn tác.", "Xóa", true, null);
                var scene = confirmChrome.stage().getScene();
                StackPane root = (StackPane) scene.getRoot();
                root.applyCss();
                root.layout();

                StackPane clipHost = (StackPane) root.getChildren().get(0);
                VBox shell = (VBox) clipHost.getChildren().get(0);

                assertNotNull(clipHost.getBackground(), "clipHost must have solid background");
                assertNotNull(shell.getBackground(), "shell must have solid background");

                Pane titleBar = (Pane) shell.getChildren().get(0);
                Pane body = (Pane) shell.getChildren().get(1);
                Pane footer = (Pane) shell.getChildren().get(2);

                assertNotNull(titleBar.getBackground(), "Titlebar must have non-null background");
                assertNotNull(body.getBackground(), "Body must have non-null background");
                assertNotNull(footer.getBackground(), "Footer must have non-null background");

            } catch (Throwable t) {
                fail(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
