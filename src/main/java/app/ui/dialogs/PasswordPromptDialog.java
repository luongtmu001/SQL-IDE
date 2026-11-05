package app.ui.dialogs;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;

/**
 * SCR-DLG-002: password prompt when a connection has no saved password or the saved one is
 * older than 30 days (Gate B 2026-11-05, Q3 = a).
 */
public final class PasswordPromptDialog {

    /** Entered password and whether it should be saved (encrypted, 30 days). */
    public record Result(String password, boolean save) {
    }

    private PasswordPromptDialog() {
    }

    public static Optional<Result> show(Window owner, String connectionName, String target, boolean expired) {
        Result[] result = {null};
        DialogChrome chrome = DialogChrome.create(owner, "Kết nối \"" + connectionName + "\"", "fth-lock");

        Label info = new Label(target != null ? target : "");
        info.getStyleClass().add("dialog-label");
        Label reason = new Label(expired
                ? "Mật khẩu đã lưu quá 30 ngày. Nhập lại để tiếp tục."
                : "Kết nối này chưa lưu mật khẩu.");
        reason.getStyleClass().add(expired ? "dialog-error" : "dialog-hint");
        reason.setWrapText(true);

        PasswordField hidden = new PasswordField();
        hidden.setPromptText("Mật khẩu");
        TextField shown = new TextField();
        shown.setPromptText("Mật khẩu");
        shown.textProperty().bindBidirectional(hidden.textProperty());
        shown.setVisible(false);
        StackPane fieldStack = new StackPane(hidden, shown);
        HBox.setHgrow(fieldStack, Priority.ALWAYS);
        ToggleButton eye = new ToggleButton();
        eye.setGraphic(new FontIcon("fth-eye"));
        eye.getStyleClass().add("flat");
        eye.setFocusTraversable(false);
        eye.selectedProperty().addListener((o, a, on) -> {
            shown.setVisible(on);
            hidden.setVisible(!on);
            ((FontIcon) eye.getGraphic()).setIconLiteral(on ? "fth-eye-off" : "fth-eye");
        });
        HBox passRow = new HBox(6, fieldStack, eye);

        CheckBox save = new CheckBox("Lưu mật khẩu (mã hóa, 30 ngày)");
        save.setSelected(true);
        Label hint = new Label("Không lưu: mật khẩu chỉ giữ trong bộ nhớ đến khi đóng ứng dụng.");
        hint.getStyleClass().add("dialog-hint");
        hint.setWrapText(true);

        VBox box = new VBox(8, info, reason, passRow, save, hint);

        Button cancel = DialogChrome.cancel("Hủy");
        Button ok = DialogChrome.primary("Kết nối");
        ok.disableProperty().bind(hidden.textProperty().isEmpty());
        cancel.setOnAction(e -> chrome.close());
        ok.setOnAction(e -> {
            result[0] = new Result(hidden.getText(), save.isSelected());
            chrome.close();
        });

        chrome.content(box).footer(null, cancel, ok).size(400, -1);
        Platform.runLater(hidden::requestFocus);
        chrome.showAndWait();
        return Optional.ofNullable(result[0]);
    }
}
