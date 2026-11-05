package app.ui.dialogs;

import app.framework.query.DebugExtensionDetector.Occurrence;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

/**
 * SCR-DLG-010 IDE debug extensions bs.* found when running in normal mode (LLTool index.html
 * ide-modal-debug-extension-warning). Gate B 2026-11-05: "Run Debug" shown disabled until debug mode exists.
 */
public final class DebugExtensionDialog {

    public enum Choice { CANCEL, COMMENT_AND_RUN, RUN_DEBUG }

    private DebugExtensionDialog() {
    }

    public static Choice show(Window owner, List<Occurrence> occurrences) {
        Choice[] result = {Choice.CANCEL};
        DialogChrome chrome = DialogChrome.create(owner, "Có hàm debug bs.* trong code", "fth-alert-triangle").iconStyle("warning");
        Label msg = new Label("Các hàm bs.* chỉ chạy ở chế độ Run Debug, không chạy được ở chế độ Run thường.");
        msg.getStyleClass().add("dialog-message");
        msg.setWrapText(true);

        ListView<Occurrence> list = new ListView<>();
        list.getItems().setAll(occurrences);
        list.getStyleClass().add("guard-list");
        list.setPrefHeight(Math.min(200, 10 + occurrences.size() * 26));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Occurrence item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "Dòng " + item.line() + " · " + item.text());
                getStyleClass().remove("mono-cell");
                if (!empty) getStyleClass().add("mono-cell");
            }
        });

        Button cancel = DialogChrome.cancel("Hủy");
        Button comment = DialogChrome.secondary("Comment hết bs.* và chạy");
        Button debug = DialogChrome.primary("Run Debug (chưa hỗ trợ)");
        debug.setDisable(true);
        debug.setDefaultButton(false);
        cancel.setOnAction(e -> chrome.close());
        comment.setOnAction(e -> {
            result[0] = Choice.COMMENT_AND_RUN;
            chrome.close();
        });
        chrome.content(new VBox(8, msg, list)).footer(new javafx.scene.Node[]{cancel}, comment, debug).size(520, -1);
        chrome.showAndWait();
        return result[0];
    }
}
