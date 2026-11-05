package app.ui.dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.prefs.Preferences;

/** SCR-DLG-008 confirm formatting the whole document, with "Không hỏi lại" (Gate B 2026-11-05). */
public final class FormatConfirmDialog {

    private static final Preferences PREFS = Preferences.userNodeForPackage(FormatConfirmDialog.class);
    private static final String KEY_SKIP = "format_document_skip_confirm";

    private FormatConfirmDialog() {
    }

    /** @return true to format. Returns true immediately when the user ticked "Không hỏi lại" before. */
    public static boolean confirm(Window owner) {
        if (PREFS.getBoolean(KEY_SKIP, false)) return true;
        boolean[] ok = {false};
        DialogChrome chrome = DialogChrome.create(owner, "Format toàn bộ tài liệu", "fth-align-left");
        Label msg = new Label("Toàn bộ SQL trong tab sẽ được định dạng lại. Có thể hoàn tác bằng Ctrl+Z.");
        msg.getStyleClass().add("dialog-message");
        msg.setWrapText(true);
        CheckBox skip = new CheckBox("Không hỏi lại");
        Button cancel = DialogChrome.cancel("Hủy");
        Button go = DialogChrome.primary("Format");
        cancel.setOnAction(e -> chrome.close());
        go.setOnAction(e -> {
            ok[0] = true;
            if (skip.isSelected()) PREFS.putBoolean(KEY_SKIP, true);
            chrome.close();
        });
        chrome.content(new VBox(10, msg, skip)).footer(null, cancel, go).size(400, -1);
        chrome.showAndWait();
        return ok[0];
    }

    /** Lets Settings turn the confirmation back on later. */
    public static void resetSkip() {
        PREFS.remove(KEY_SKIP);
    }
}
