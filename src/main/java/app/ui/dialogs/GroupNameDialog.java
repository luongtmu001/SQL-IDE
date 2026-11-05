package app.ui.dialogs;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Function;

/**
 * SCR-DLG-003: create / rename a connection group with inline validation
 * (empty name, '/', duplicates). {@code apply} returns an error message or null on success.
 */
public final class GroupNameDialog {

    private GroupNameDialog() {
    }

    public static Optional<String> show(Window owner, boolean rename, String parentPath, String initial,
                                        Function<String, String> apply) {
        String[] result = {null};
        DialogChrome chrome = DialogChrome.create(owner, rename ? "Đổi tên nhóm" : "Nhóm mới",
                rename ? "fth-edit-2" : "fth-folder-plus");

        Label where = new Label(parentPath == null || parentPath.isBlank()
                ? "Trong: (gốc)" : "Trong: " + parentPath);
        where.getStyleClass().add("dialog-label");
        Label nameLabel = new Label("Tên nhóm");
        nameLabel.getStyleClass().add("dialog-label");
        TextField field = new TextField(initial != null ? initial : "");
        field.setPromptText("Ví dụ: POSTGRE");
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);
        Label hint = new Label("Dùng nhóm con để phân cấp, ví dụ BRAVO-SERVER ▸ POSTGRE.");
        hint.getStyleClass().add("dialog-hint");
        hint.setWrapText(true);

        VBox box = new VBox(6, where, nameLabel, field, error, hint);

        Button cancel = DialogChrome.cancel("Hủy");
        Button ok = DialogChrome.primary(rename ? "Đổi tên" : "Tạo nhóm");
        cancel.setOnAction(e -> chrome.close());
        ok.setOnAction(e -> {
            String name = field.getText() != null ? field.getText().trim() : "";
            String err = apply.apply(name);
            if (err == null) {
                result[0] = name;
                chrome.close();
            } else {
                error.setText(err);
                error.setVisible(true);
                error.setManaged(true);
                field.getStyleClass().add("invalid");
                field.requestFocus();
                field.selectAll();
                chrome.stage().sizeToScene();
            }
        });
        field.textProperty().addListener((o, a, b) -> {
            field.getStyleClass().remove("invalid");
            error.setVisible(false);
            error.setManaged(false);
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
