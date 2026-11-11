package app;

import app.ui.dialogs.DialogChrome;
import app.ui.dialogs.SettingsDialog;
import app.services.ThemeService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class DialogChromeBackgroundTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Thread.currentThread().setContextClassLoader(cl);
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            Platform.runLater(() -> Thread.currentThread().setContextClassLoader(cl));
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("DialogChrome titlebar and footer must retain non-null solid background after theme re-init")
    void testDialogChromeCssAndBackground() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                DialogChrome chrome = DialogChrome.create(null, "Test Title", "fth-sliders")
                        .content(new Label("Test Content"))
                        .footer(null, new Button("Save"));

                var scene = chrome.stage().getScene();
                assertNotNull(scene);

                StackPane root = (StackPane) scene.getRoot();
                StackPane clipHost = (StackPane) root.getChildren().get(0);
                VBox shell = (VBox) clipHost.getChildren().get(0);
                HBox titleBar = (HBox) shell.getChildren().get(0);
                Node footer = shell.getChildren().get(shell.getChildren().size() - 1);

                // Force CSS layout
                root.applyCss();
                root.layout();

                System.out.println("TEST_DEBUG: shell background: " + shell.getBackground());
                System.out.println("TEST_DEBUG: titleBar background: " + titleBar.getBackground());
                System.out.println("TEST_DEBUG: footer background: " + ((Pane) footer).getBackground());
                System.out.println("TEST_DEBUG: clipHost background: " + clipHost.getBackground());

                assertNotNull(shell.getBackground(), "Shell background must not be null");
                assertNotNull(titleBar.getBackground(), "TitleBar background must not be null");
                assertNotNull(((Pane) footer).getBackground(), "Footer background must not be null");

                // Now simulate reopen / theme re-initialization
                ThemeService.getInstance().initializeTheme(scene);
                chrome.ensureStylesheets();

                root.applyCss();
                root.layout();
                assertNotNull(titleBar.getBackground(), "TitleBar background must stay non-null after re-init");
                assertNotNull(((Pane) footer).getBackground(), "Footer background must stay non-null after re-init");

            } catch (Throwable t) {
                fail(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("SettingsDialog preloading builds cached chrome without blocking and retains styles")
    void testSettingsDialogPreload() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                SettingsDialog.clearCache();
                SettingsDialog.preload(null);

                // Wait a moment for preload to finish on FX thread
                Platform.runLater(() -> {
                    try {
                        long t0 = System.currentTimeMillis();
                        var scene = SettingsDialog.getCachedDialogScene();
                        long elapsed = System.currentTimeMillis() - t0;
                        assertNotNull(scene, "Cached dialog scene must not be null");
                        assertTrue(elapsed < 500, "Cached open check should take < 500ms");

                        StackPane root = (StackPane) scene.getRoot();
                        root.applyCss();
                        root.layout();
                        StackPane clipHost = (StackPane) root.getChildren().get(0);
                        VBox shell = (VBox) clipHost.getChildren().get(0);
                        HBox titleBar = (HBox) shell.getChildren().get(0);
                        Node footer = shell.getChildren().get(shell.getChildren().size() - 1);

                        assertNotNull(shell.getBackground());
                        assertNotNull(titleBar.getBackground());
                        assertNotNull(((Pane) footer).getBackground());
                        assertNotNull(clipHost.getBackground());
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (Throwable t) {
                fail(t);
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("DialogChrome root contains both root and dialog-root style classes for CSS variables")
    void testDialogChromeRootStyleClasses() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                DialogChrome chrome = DialogChrome.create(null, "Test Chrome", "fth-sliders");
                var scene = chrome.stage().getScene();
                assertNotNull(scene);
                var root = scene.getRoot();
                assertTrue(root.getStyleClass().contains("root"), "Scene root must contain 'root' class");
                assertTrue(root.getStyleClass().contains("dialog-root"), "Scene root must contain 'dialog-root' class");
            } catch (Throwable t) {
                fail(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Custom stylesheets such as connection-manager.css are preserved across theme apply")
    void testCustomStylesheetsPreserved() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                DialogChrome chrome = DialogChrome.create(null, "Test Custom CSS", "fth-database");
                var scene = chrome.stage().getScene();
                assertNotNull(scene);
                String customCss = "/css/connection-manager.css";
                var url = getClass().getResource(customCss);
                assertNotNull(url);
                scene.getStylesheets().add(url.toExternalForm());

                // Now call ensureStylesheets or applyThemeToScene
                chrome.ensureStylesheets();
                ThemeService.getInstance().applyThemeToScene(scene);

                assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("connection-manager.css")),
                        "connection-manager.css must NOT be cleared by applyThemeToScene");
            } catch (Throwable t) {
                fail(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
