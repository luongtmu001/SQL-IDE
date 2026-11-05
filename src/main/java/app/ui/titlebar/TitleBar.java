package app.ui.titlebar;

import app.ui.window.WindowStateManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;

import java.io.IOException;

public class TitleBar extends HBox {

    private TitleBarController controller;

    public TitleBar() {
        loadFXML();
    }

    public TitleBar(WindowStateManager stateManager) {
        this();
        if (controller != null && stateManager != null) {
            controller.bindToStateManager(stateManager);
        }
    }

    private void loadFXML() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/titlebar.fxml"));
        loader.setRoot(this);
        try {
            loader.load();
            this.controller = loader.getController();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load titlebar.fxml", e);
        }
    }

    public TitleBarController getController() {
        return controller;
    }
}

