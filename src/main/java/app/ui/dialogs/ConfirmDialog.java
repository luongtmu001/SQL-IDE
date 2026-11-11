package app.ui.dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * SCR-DLG-004 (and other confirmations): message + optional detail, Cancel / action button.
 * Destructive actions use the red button and a warning icon.
 */
public final class ConfirmDialog {

    private ConfirmDialog() {
    }

    public static boolean show(Window owner, String title, String message, String detail,
                               String actionText, boolean destructive) {
        boolean[] result = {false};
        DialogChrome chrome = build(owner, title, message, detail, actionText, destructive, () -> result[0] = true);
        chrome.showAndWait();
        return result[0];
    }

    static DialogChrome build(Window owner, String title, String message, String detail,
                              String actionText, boolean destructive, Runnable onOk) {
        DialogChrome chrome = DialogChrome.create(owner, title, destructive ? "fth-alert-triangle" : "fth-help-circle");
        if (destructive) chrome.iconStyle("danger");

        Label msg = new Label(message);
        msg.getStyleClass().add("dialog-message");
        msg.setWrapText(true);
        VBox box = new VBox(6, msg);
        if (detail != null && !detail.isBlank()) {
            Label det = new Label(detail);
            det.getStyleClass().add("dialog-label");
            det.setWrapText(true);
            box.getChildren().add(det);
        }

        Button cancel = DialogChrome.cancel("Hủy");
        Button ok = destructive ? DialogChrome.danger(actionText) : DialogChrome.primary(actionText);
        cancel.setOnAction(e -> chrome.close());
        ok.setOnAction(e -> {
            if (onOk != null) onOk.run();
            chrome.close();
        });
        chrome.content(box).footer(null, cancel, ok).size(420, -1);
        return chrome;
    }
}
