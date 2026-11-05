package app.ui.window;

import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class WindowResizeHandler {

    private static final int BORDER_WIDTH = 6;
    private final Stage stage;
    private Cursor cursorEvent = Cursor.DEFAULT;

    private double startX = 0;
    private double startY = 0;
    private double startScreenX = 0;
    private double startScreenY = 0;
    private double startStageWidth = 0;
    private double startStageHeight = 0;

    public WindowResizeHandler(Stage stage) {
        this.stage = stage;
    }

    public void attachTo(Scene scene) {
        if (scene == null || stage == null) return;

        ResizeListener listener = new ResizeListener();
        scene.addEventHandler(MouseEvent.MOUSE_MOVED, listener);
        scene.addEventHandler(MouseEvent.MOUSE_PRESSED, listener);
        scene.addEventHandler(MouseEvent.MOUSE_DRAGGED, listener);
        scene.addEventHandler(MouseEvent.MOUSE_EXITED, listener);
        scene.addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, listener);
    }

    private class ResizeListener implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent mouseEvent) {
            EventType<? extends MouseEvent> type = mouseEvent.getEventType();
            Scene scene = stage.getScene();
            if (scene == null) return;

            if (stage.isMaximized()) {
                if (cursorEvent != Cursor.DEFAULT) {
                    scene.setCursor(Cursor.DEFAULT);
                    cursorEvent = Cursor.DEFAULT;
                }
                return;
            }

            double mouseEventX = mouseEvent.getSceneX();
            double mouseEventY = mouseEvent.getSceneY();
            double sceneWidth = scene.getWidth();
            double sceneHeight = scene.getHeight();

            if (MouseEvent.MOUSE_MOVED.equals(type)) {
                if (mouseEventX < BORDER_WIDTH && mouseEventY < BORDER_WIDTH) {
                    cursorEvent = Cursor.NW_RESIZE;
                } else if (mouseEventX < BORDER_WIDTH && mouseEventY > sceneHeight - BORDER_WIDTH) {
                    cursorEvent = Cursor.SW_RESIZE;
                } else if (mouseEventX > sceneWidth - BORDER_WIDTH && mouseEventY < BORDER_WIDTH) {
                    cursorEvent = Cursor.NE_RESIZE;
                } else if (mouseEventX > sceneWidth - BORDER_WIDTH && mouseEventY > sceneHeight - BORDER_WIDTH) {
                    cursorEvent = Cursor.SE_RESIZE;
                } else if (mouseEventX < BORDER_WIDTH) {
                    cursorEvent = Cursor.W_RESIZE;
                } else if (mouseEventX > sceneWidth - BORDER_WIDTH) {
                    cursorEvent = Cursor.E_RESIZE;
                } else if (mouseEventY < BORDER_WIDTH) {
                    cursorEvent = Cursor.N_RESIZE;
                } else if (mouseEventY > sceneHeight - BORDER_WIDTH) {
                    cursorEvent = Cursor.S_RESIZE;
                } else {
                    cursorEvent = Cursor.DEFAULT;
                }
                scene.setCursor(cursorEvent);
            } else if (MouseEvent.MOUSE_EXITED.equals(type) || MouseEvent.MOUSE_EXITED_TARGET.equals(type)) {
                scene.setCursor(Cursor.DEFAULT);
            } else if (MouseEvent.MOUSE_PRESSED.equals(type)) {
                startX = stage.getX();
                startY = stage.getY();
                startScreenX = mouseEvent.getScreenX();
                startScreenY = mouseEvent.getScreenY();
                startStageWidth = stage.getWidth();
                startStageHeight = stage.getHeight();
            } else if (MouseEvent.MOUSE_DRAGGED.equals(type)) {
                if (cursorEvent == Cursor.DEFAULT) {
                    return;
                }

                double deltaX = mouseEvent.getScreenX() - startScreenX;
                double deltaY = mouseEvent.getScreenY() - startScreenY;

                if (cursorEvent == Cursor.E_RESIZE || cursorEvent == Cursor.NE_RESIZE || cursorEvent == Cursor.SE_RESIZE) {
                    double newWidth = startStageWidth + deltaX;
                    if (newWidth >= stage.getMinWidth()) {
                        stage.setWidth(newWidth);
                    }
                }

                if (cursorEvent == Cursor.S_RESIZE || cursorEvent == Cursor.SW_RESIZE || cursorEvent == Cursor.SE_RESIZE) {
                    double newHeight = startStageHeight + deltaY;
                    if (newHeight >= stage.getMinHeight()) {
                        stage.setHeight(newHeight);
                    }
                }

                if (cursorEvent == Cursor.W_RESIZE || cursorEvent == Cursor.NW_RESIZE || cursorEvent == Cursor.SW_RESIZE) {
                    double newWidth = startStageWidth - deltaX;
                    if (newWidth >= stage.getMinWidth()) {
                        stage.setX(startX + deltaX);
                        stage.setWidth(newWidth);
                    }
                }

                if (cursorEvent == Cursor.N_RESIZE || cursorEvent == Cursor.NW_RESIZE || cursorEvent == Cursor.NE_RESIZE) {
                    double newHeight = startStageHeight - deltaY;
                    if (newHeight >= stage.getMinHeight()) {
                        stage.setY(startY + deltaY);
                        stage.setHeight(newHeight);
                    }
                }
            }
        }
    }
}

