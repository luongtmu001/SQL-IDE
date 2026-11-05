package app.ui.window;

import app.ui.window.platform.WindowsWindowAdapter;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.stage.Stage;

public class WindowStateManager {

    private final Stage stage;
    private final BooleanProperty maximized = new SimpleBooleanProperty(false);
    private final BooleanProperty iconified = new SimpleBooleanProperty(false);

    /** Native Windows integration (null on other platforms or when unavailable). */
    private WindowsWindowAdapter nativeAdapter;

    // Un-maximized restore geometry (used only by the pure JavaFX fallback)
    private double restoreX = 100;
    private double restoreY = 100;
    private double restoreWidth = 1280;
    private double restoreHeight = 820;

    public WindowStateManager(Stage stage) {
        this.stage = stage;
        initListeners();
    }

    private void initListeners() {
        if (stage == null) return;

        maximized.set(stage.isMaximized());
        stage.maximizedProperty().addListener((obs, oldVal, newVal) -> maximized.set(newVal));
        iconified.bind(stage.iconifiedProperty());

        // Track normal window bounds before maximizing
        stage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (!isMaximized()) restoreX = newVal.doubleValue();
        });
        stage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (!isMaximized()) restoreY = newVal.doubleValue();
        });
        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (!isMaximized()) restoreWidth = newVal.doubleValue();
        });
        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (!isMaximized()) restoreHeight = newVal.doubleValue();
        });
    }

    /** Enables native window operations (Windows): maximize respects the taskbar, snap, etc. */
    public void setNativeAdapter(WindowsWindowAdapter adapter) {
        this.nativeAdapter = adapter;
        if (adapter != null) {
            maximized.set(adapter.isMaximized());
        }
    }

    public boolean isNativeMode() {
        return nativeAdapter != null;
    }

    /** Called by the native adapter when Windows maximizes / restores the window (snap, Win+Up...). */
    public void onNativeMaximizedChanged(boolean isMaximized) {
        maximized.set(isMaximized);
    }

    public void minimize() {
        if (nativeAdapter != null) {
            nativeAdapter.minimize();
        } else if (stage != null) {
            stage.setIconified(true);
        }
    }

    public void maximizeRestore() {
        if (nativeAdapter != null) {
            nativeAdapter.toggleMaximize();
            return;
        }
        if (stage != null) {
            boolean willMaximize = !stage.isMaximized();
            stage.setMaximized(willMaximize);
            if (!willMaximize) {
                // Ensure proper restored bounds
                if (restoreWidth > 0) stage.setWidth(restoreWidth);
                if (restoreHeight > 0) stage.setHeight(restoreHeight);
                if (restoreX >= 0) stage.setX(restoreX);
                if (restoreY >= 0) stage.setY(restoreY);
            }
        }
    }

    public void close() {
        if (stage != null) {
            // Respect application lifecycle
            stage.close();
            Platform.exit();
        }
    }

    public ReadOnlyBooleanProperty maximizedProperty() {
        return maximized;
    }

    public boolean isMaximized() {
        return maximized.get();
    }

    public BooleanProperty iconifiedProperty() {
        return iconified;
    }

    public boolean isIconified() {
        return iconified.get();
    }

    public Stage getStage() {
        return stage;
    }
}
