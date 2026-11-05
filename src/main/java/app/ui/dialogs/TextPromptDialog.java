package app.ui.dialogs;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/** Single text input (e.g. rename tab), DialogChrome + R-UI-01. */
public final class TextPromptDialog {

    private TextPromptDialog() {
    }

    public static Optional<String> show(Window owner, String title, String icon, String label, String initial, String actionText) {
        String[] result = {null};
        DialogChrome chrome = DialogChrome.create(owner, title, icon);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("dialog-label");
        TextField field = new TextField(initial != null ? initial : "");
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        error.setVisible(false);
        error.setManaged(false);
        VBox box = new VBox(6, lbl, field, error);

        Button cancel = DialogChrome.cancel("Hủy");
        Button ok = DialogChrome.primary(actionText);
        cancel.setOnAction(e -> chrome.close());
        ok.setOnAction(e -> {
            String v = field.getText() != null ? field.getText().trim() : "";
            if (v.isEmpty()) {
                error.setText("Nhập tên trước khi lưu.");
                error.setVisible(true);
                error.setManaged(true);
                field.getStyleClass().add("invalid");
                return;
            }
            result[0] = v;
            chrome.close();
        });
        field.textProperty().addListener((o, a, b) -> {
            error.setVisible(false);
            error.setManaged(false);
            field.getStyleClass().remove("invalid");
        });
        chrome.content(box).footer(null, cancel, ok).size(380, -1);
        Platform.runLater(() -> {
            field.requestFocus();
            field.selectAll();
        });
        chrome.showAndWait();
        return Optional.ofNullable(result[0]);
    }
}
