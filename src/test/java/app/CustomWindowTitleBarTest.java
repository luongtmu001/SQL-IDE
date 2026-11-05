package app;

import app.ui.titlebar.TitleBarController;
import app.ui.titlebar.WindowControls;
import app.ui.titlebar.WindowTitleModel;
import app.ui.window.CustomWindow;
import app.ui.window.WindowController;
import app.ui.window.WindowStateManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CustomWindowTitleBarTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {
        }
    }

    @Test
    void testWindowTitleModel() {
        WindowTitleModel model = WindowTitleModel.getInstance();
        assertNotNull(model);

        model.setAppName("SQL IDE Test");
        model.setDocumentTitle("query.sql");
        model.setThemeName("Primer Dark");

        assertEquals("SQL IDE Test", model.getAppName());
        assertEquals("query.sql", model.getDocumentTitle());
        assertEquals("Primer Dark", model.getThemeName());
        assertTrue(model.getFullTitle().contains("SQL IDE Test"));
        assertTrue(model.getFullTitle().contains("query.sql"));
        assertTrue(model.getFullTitle().contains("[Primer Dark]"));
    }

    @Test
    void testWindowStateManagerAndControls() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<WindowStateManager> managerRef = new AtomicReference<>();
        AtomicReference<WindowControls> controlsRef = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            stageRef.set(stage);
            WindowStateManager manager = new WindowStateManager(stage);
            managerRef.set(manager);
            WindowControls controls = new WindowControls(manager);
            controlsRef.set(controls);

            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        WindowStateManager manager = managerRef.get();
        WindowControls controls = controlsRef.get();

        assertNotNull(manager);
        assertNotNull(controls);
        assertNotNull(controls.getMinimizeButton());
        assertNotNull(controls.getMaxRestoreButton());
        assertNotNull(controls.getCloseButton());

        assertFalse(manager.isMaximized());
        assertFalse(manager.isIconified());
    }

    @Test
    void testTitleBarFXMLLoads() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TitleBarController> controllerRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/titlebar.fxml"));
                Parent root = loader.load();
                rootRef.set(root);
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "titlebar.fxml should load within 5s");
        TitleBarController controller = controllerRef.get();
        assertNotNull(controller, "TitleBarController should not be null");
        assertNotNull(controller.getTitleBarRoot(), "Title bar root node should not be null");
        assertNotNull(controller.getHamburgerMenuButton(), "Hamburger button should be instantiated");
        assertNotNull(controller.getSearchArea(), "Search area should not be null");

        CountDownLatch respLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            javafx.scene.control.MenuBar bar = new javafx.scene.control.MenuBar(
                new javafx.scene.control.Menu("File"),
                new javafx.scene.control.Menu("Edit")
            );
            controller.setMenuBar(bar);
            assertEquals(2, bar.getMenus().size());

            // Collapse to compact hamburger menu when width is small (<950)
            controller.checkMenuResponsiveness(700);
            assertTrue(controller.getHamburgerMenuButton().isVisible());
            assertEquals(2, controller.getHamburgerMenuButton().getItems().size());
            assertEquals(0, bar.getMenus().size());

            // Restore inline menu when width is large (>=950)
            controller.checkMenuResponsiveness(1200);
            assertFalse(controller.getHamburgerMenuButton().isVisible());
            assertEquals(2, bar.getMenus().size());

            respLatch.countDown();
        });
        assertTrue(respLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testCustomWindowCreation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CustomWindow> windowRef = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            CustomWindow window = new CustomWindow(stage);
            window.setSize(1000, 700, 800, 500);

            Scene scene = new Scene(new Pane(), 1000, 700);
            HBox dummyTitleBar = new HBox();
            TitleBarController dummyController = new TitleBarController();

            window.setScene(scene, dummyTitleBar, dummyController);
            windowRef.set(window);

            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        CustomWindow window = windowRef.get();
        assertNotNull(window);
        assertNotNull(window.getStage());
        assertNotNull(window.getWindowController());
        assertNotNull(window.getStateManager());
    }
}

