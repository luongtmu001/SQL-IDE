package app.ui.window;

import app.ui.titlebar.TitleBarController;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class CustomWindow {

    private final Stage stage;
    private final WindowController windowController;

    public CustomWindow(Stage stage) {
        this.stage = stage;
        this.stage.initStyle(StageStyle.UNDECORATED);
        this.windowController = new WindowController(stage);
    }

    public void setScene(Scene scene, Node titleBarDragRegion, TitleBarController titleBarController) {
        stage.setScene(scene);
        windowController.attachTo(scene, titleBarDragRegion, titleBarController);
    }

    public void setSize(double width, double height, double minWidth, double minHeight) {
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
    }

    public void show() {
        stage.show();
        windowController.onShown();
    }

    public Stage getStage() {
        return stage;
    }

    public WindowController getWindowController() {
        return windowController;
    }

    public WindowStateManager getStateManager() {
        return windowController.getStateManager();
    }
}

