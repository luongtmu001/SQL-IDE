package app.ui.dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Close a tab with unsaved changes (LLTool tabs.js showCloseConfirmModal, Phase 3). */
public final class CloseTabDialog {

    public enum Choice { CANCEL, SAVE_AND_CLOSE, CLOSE_WITHOUT_SAVING }

    private CloseTabDialog() {
    }

    public static Choice show(Window owner, String tabTitle) {
        Choice[] result = {Choice.CANCEL};
        DialogChrome chrome = DialogChrome.create(owner, "Đóng tab", "fth-alert-triangle").iconStyle("warning");
        Label msg = new Label("Tab \"" + tabTitle + "\" có thay đổi chưa lưu.");
        msg.getStyleClass().add("dialog-message");
        msg.setWrapText(true);
        Label det = new Label("Đóng mà không lưu thì các thay đổi này sẽ mất.");
        det.getStyleClass().add("dialog-label");
        det.setWrapText(true);

        Button cancel = DialogChrome.cancel("Hủy");
        Button discard = DialogChrome.danger("Đóng không lưu");
        discard.setDefaultButton(false);
        Button save = DialogChrome.primary("Lưu và đóng");
        cancel.setOnAction(e -> chrome.close());
        discard.setOnAction(e -> {
            result[0] = Choice.CLOSE_WITHOUT_SAVING;
            chrome.close();
        });
        save.setOnAction(e -> {
            result[0] = Choice.SAVE_AND_CLOSE;
            chrome.close();
        });
        chrome.content(new VBox(6, msg, det)).footer(null, cancel, discard, save).size(440, -1);
        chrome.showAndWait();
        return result[0];
    }
}
