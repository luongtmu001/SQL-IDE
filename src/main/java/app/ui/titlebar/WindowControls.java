package app.ui.titlebar;

import app.ui.window.WindowStateManager;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

public class WindowControls extends HBox {

    private final Button minimizeBtn = new Button();
    private final Button maxRestoreBtn = new Button();
    private final Button closeBtn = new Button();

    private final FontIcon maxRestoreIcon = new FontIcon("fth-square");
    private WindowStateManager stateManager;

    public WindowControls() {
        super(2);
        setAlignment(Pos.CENTER_RIGHT);
        getStyleClass().add("window-controls");

        initButtons();
    }

    public WindowControls(WindowStateManager stateManager) {
        this();
        setStateManager(stateManager);
    }

    private void initButtons() {
        // Minimize Button
        minimizeBtn.getStyleClass().addAll("window-control-button", "minimize");
        minimizeBtn.setGraphic(new FontIcon("fth-minus"));
        minimizeBtn.setTooltip(new Tooltip("Minimize"));
        minimizeBtn.setFocusTraversable(false);
        minimizeBtn.setOnAction(e -> {
            if (stateManager != null) stateManager.minimize();
        });

        // Maximize / Restore Button
        maxRestoreBtn.getStyleClass().addAll("window-control-button", "maximize");
        maxRestoreBtn.setGraphic(maxRestoreIcon);
        maxRestoreBtn.setTooltip(new Tooltip("Maximize / Restore"));
        maxRestoreBtn.setFocusTraversable(false);
        maxRestoreBtn.setOnAction(e -> {
            if (stateManager != null) stateManager.maximizeRestore();
        });

        // Close Button
        closeBtn.getStyleClass().addAll("window-control-button", "close");
        closeBtn.setGraphic(new FontIcon("fth-x"));
        closeBtn.setTooltip(new Tooltip("Close"));
        closeBtn.setFocusTraversable(false);
        closeBtn.setOnAction(e -> {
            if (stateManager != null) stateManager.close();
        });

        getChildren().addAll(minimizeBtn, maxRestoreBtn, closeBtn);
    }

    public void setStateManager(WindowStateManager stateManager) {
        this.stateManager = stateManager;
        if (stateManager != null) {
            stateManager.maximizedProperty().addListener((obs, oldVal, newVal) -> {
                updateMaxRestoreIcon(newVal);
            });
            updateMaxRestoreIcon(stateManager.isMaximized());
        }
    }

    private void updateMaxRestoreIcon(boolean isMaximized) {
        maxRestoreIcon.setIconLiteral(isMaximized ? "fth-copy" : "fth-square");
        maxRestoreBtn.setTooltip(new Tooltip(isMaximized ? "Restore Down" : "Maximize"));
    }

    public Button getMinimizeButton() {
        return minimizeBtn;
    }

    public Button getMaxRestoreButton() {
        return maxRestoreBtn;
    }

    public Button getCloseButton() {
        return closeBtn;
    }
}

