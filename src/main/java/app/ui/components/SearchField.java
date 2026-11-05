package app.ui.components;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

public class SearchField extends HBox {

    private final FontIcon searchIcon = new FontIcon("fth-search");
    private final TextField inputField = new TextField();
    private final IconButton clearButton = new IconButton("fth-x", "Clear search");

    public SearchField() {
        this("Search...");
    }

    public SearchField(String promptText) {
        super(4);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("custom-search-field");

        searchIcon.getStyleClass().add("search-field-icon");

        inputField.setPromptText(promptText);
        inputField.getStyleClass().add("search-field-input");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        clearButton.getStyleClass().add("search-field-clear-btn");
        clearButton.setVisible(false);
        clearButton.setManaged(false);

        inputField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasText = newVal != null && !newVal.isEmpty();
            clearButton.setVisible(hasText);
            clearButton.setManaged(hasText);
        });

        clearButton.setOnAction(e -> {
            inputField.clear();
            inputField.requestFocus();
        });

        // Clicking container focuses the input
        setOnMouseClicked(e -> inputField.requestFocus());

        getChildren().addAll(searchIcon, inputField, clearButton);
    }

    public StringProperty textProperty() {
        return inputField.textProperty();
    }

    public String getText() {
        return inputField.getText();
    }

    public void setText(String text) {
        inputField.setText(text);
    }

    public StringProperty promptTextProperty() {
        return inputField.promptTextProperty();
    }

    public void setPromptText(String promptText) {
        inputField.setPromptText(promptText);
    }

    public void setOnTextChange(Consumer<String> consumer) {
        inputField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (consumer != null) {
                consumer.accept(newVal);
            }
        });
    }

    public void clear() {
        inputField.clear();
    }

    public TextField getInputField() {
        return inputField;
    }

    public TextField getTextField() {
        return inputField;
    }
}

