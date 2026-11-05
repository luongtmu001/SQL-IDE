package app.ui.window;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class WindowDragHandler {

    private final Stage stage;
    private final WindowStateManager stateManager;
    private double xOffset = 0;
    private double yOffset = 0;

    public WindowDragHandler(Stage stage, WindowStateManager stateManager) {
        this.stage = stage;
        this.stateManager = stateManager;
    }

    public void attachTo(Node dragRegion) {
        if (dragRegion == null || stage == null) return;

        dragRegion.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            if (isInteractiveNode(event.getTarget())) return;

            if (event.getClickCount() == 2) {
                if (stateManager != null) {
                    stateManager.maximizeRestore();
                }
                event.consume();
                return;
            }

            xOffset = event.getScreenX() - stage.getX();
            yOffset = event.getScreenY() - stage.getY();
        });

        dragRegion.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            if (isInteractiveNode(event.getTarget())) return;

            if (stage.isMaximized()) {
                // If user drags while maximized, un-maximize and center title under cursor
                double widthRatio = event.getX() / stage.getWidth();
                stateManager.maximizeRestore();
                stage.setX(event.getScreenX() - (stage.getWidth() * widthRatio));
                stage.setY(event.getScreenY() - 15);
                xOffset = event.getScreenX() - stage.getX();
                yOffset = event.getScreenY() - stage.getY();
                return;
            }

            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    private boolean isInteractiveNode(Object target) {
        if (target instanceof Node node) {
            // Traverse up to check if click is inside an interactive control
            Node curr = node;
            while (curr != null) {
                if (curr instanceof ButtonBase ||
                    curr instanceof TextInputControl ||
                    curr.getStyleClass().contains("window-control-button") ||
                    curr.getStyleClass().contains("quick-search-box") ||
                    curr.getStyleClass().contains("menu-bar")) {
                    return true;
                }
                curr = curr.getParent();
            }
        }
        return false;
    }
}

