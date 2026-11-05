package app.ui.window;

import app.ui.titlebar.TitleBarController;
import app.ui.window.platform.WindowsWindowAdapter;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class WindowController {

    private final Stage stage;
    private final WindowStateManager stateManager;
    private final WindowDragHandler dragHandler;
    private final WindowResizeHandler resizeHandler;

    private Scene scene;
    private Node titleBarDragRegion;
    private TitleBarController titleBarController;
    private WindowsWindowAdapter nativeAdapter;
    private boolean fallbackAttached;

    public WindowController(Stage stage) {
        this.stage = stage;
        this.stateManager = new WindowStateManager(stage);
        this.dragHandler = new WindowDragHandler(stage, stateManager);
        this.resizeHandler = new WindowResizeHandler(stage);
    }

    public void attachTo(Scene scene, Node titleBarDragRegion, TitleBarController titleBarController) {
        this.scene = scene;
        this.titleBarDragRegion = titleBarDragRegion;
        this.titleBarController = titleBarController;
        if (titleBarController != null) {
            titleBarController.bindToStateManager(stateManager);
        }
    }

    /**
     * Must be called once the stage is showing. Tries the native Windows integration first
     * (Aero Snap, Snap Layouts, Win+Arrow, taskbar-aware maximize); otherwise falls back to
     * the pure JavaFX drag &amp; resize handlers.
     */
    public void onShown() {
        if (nativeAdapter == null && titleBarController != null) {
            TitleBarController tbc = titleBarController;
            nativeAdapter = WindowsWindowAdapter.install(
                stage,
                new WindowsWindowAdapter.HitTester() {
                    @Override public boolean isCaption(double x, double y) { return tbc.isCaptionArea(x, y); }
                    @Override public boolean isMaximizeButton(double x, double y) { return tbc.isMaximizeButtonArea(x, y); }
                },
                stateManager::onNativeMaximizedChanged,
                new WindowsWindowAdapter.MaximizeButtonFeedback() {
                    @Override public void setHover(boolean hover) { tbc.setMaximizeButtonNativeHover(hover); }
                    @Override public void setPressed(boolean pressed) { tbc.setMaximizeButtonNativePressed(pressed); }
                    @Override public void fire() { stateManager.maximizeRestore(); }
                },
                t -> System.err.println("[Window] Native title bar integration unavailable: " + t)
            );
        }

        if (nativeAdapter != null) {
            stateManager.setNativeAdapter(nativeAdapter);
            installSystemMenuShortcut();
        } else {
            attachFallbackHandlers();
        }
    }

    /**
     * Alt+Space opens the native system menu. JavaFX may consume the key before Windows
     * turns it into WM_SYSCOMMAND/SC_KEYMENU, so it is also handled here (the adapter debounces
     * duplicates).
     */
    private void installSystemMenuShortcut() {
        if (scene == null || nativeAdapter == null) return;
        WindowsWindowAdapter adapter = nativeAdapter;
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.SPACE && e.isAltDown()
                    && !e.isControlDown() && !e.isShiftDown() && !e.isMetaDown()) {
                e.consume();
                adapter.showSystemMenuAtCaption();
            }
        });
    }

    private void attachFallbackHandlers() {
        if (fallbackAttached) return;
        fallbackAttached = true;
        if (scene != null) {
            resizeHandler.attachTo(scene);
        }
        if (titleBarDragRegion != null) {
            dragHandler.attachTo(titleBarDragRegion);
        }
    }

    public boolean isNativeMode() {
        return nativeAdapter != null;
    }

    public WindowStateManager getStateManager() {
        return stateManager;
    }

    public WindowDragHandler getDragHandler() {
        return dragHandler;
    }

    public WindowResizeHandler getResizeHandler() {
        return resizeHandler;
    }

    public Stage getStage() {
        return stage;
    }
}
