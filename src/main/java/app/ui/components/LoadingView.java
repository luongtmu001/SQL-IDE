package app.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

public class LoadingView extends VBox {

    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Label textLabel = new Label("Loading...");

    public LoadingView() {
        this("Executing query...");
    }

    public LoadingView(String message) {
        super(10);
        setAlignment(Pos.CENTER);
        getStyleClass().add("loading-view");

        spinner.setMaxSize(32, 32);
        spinner.getStyleClass().add("loading-spinner");

        textLabel.setText(message);
        textLabel.getStyleClass().add("loading-text");

        getChildren().addAll(spinner, textLabel);
    }

    public void setMessage(String message) {
        textLabel.setText(message);
    }

    public String getMessage() {
        return textLabel.getText();
    }
}

