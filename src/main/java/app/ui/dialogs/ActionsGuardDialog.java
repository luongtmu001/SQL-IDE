package app.ui.dialogs;

import app.framework.query.SqlProtectionService.DangerousActionIssue;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

/**
 * SCR-DLG-007a Actions Guard (LLTool sql-protection.js "Fatal Actions Guard").
 * Lists the dangerous statements with their line; double-click a row cancels and jumps to that line.
 * Default focus is "Hủy" (safe choice). Gate B 2026-11-05: kept separate from the production dialog.
 */
public final class ActionsGuardDialog {

    /** {@code navigateLine > 0} when the user double-clicked an issue. */
    public record Result(boolean proceed, int navigateLine) {
    }

    private ActionsGuardDialog() {
    }

    public static Result show(Window owner, List<DangerousActionIssue> issues) {
        Result[] result = {new Result(false, 0)};
        DialogChrome chrome = DialogChrome.create(owner, "Actions Guard", "fth-shield").iconStyle("danger")
                .subtitle("Phát hiện lệnh có thể gây mất dữ liệu");

        ListView<DangerousActionIssue> list = new ListView<>();
        list.getItems().setAll(issues);
        list.getStyleClass().add("guard-list");
        list.setPrefHeight(Math.min(220, 34 + issues.size() * 28));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DangerousActionIssue item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label msg = new Label(item.message());
                msg.setMinWidth(0);
                msg.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(msg, Priority.ALWAYS);
                Label line = new Label(String.valueOf(item.line()));
                line.getStyleClass().add("guard-line");
                HBox row = new HBox(8, msg, line);
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
                setTooltip(new Tooltip(item.message()));
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                result[0] = new Result(false, list.getSelectionModel().getSelectedItem().line());
                chrome.close();
            }
        });

        Label head1 = new Label("Vấn đề");
        Label head2 = new Label("Dòng");
        head1.getStyleClass().add("guard-head");
        head2.getStyleClass().addAll("guard-head", "guard-line");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(head1, spacer, head2);
        header.getStyleClass().add("guard-header");

        Label hint = new Label("Nhấp đúp một dòng để hủy chạy và nhảy tới dòng code đó.");
        hint.getStyleClass().add("dialog-hint");
        hint.setWrapText(true);
        Label q = new Label("Vẫn tiếp tục thực thi?");
        q.getStyleClass().add("guard-question");

        Button cancel = DialogChrome.cancel("Hủy (Esc)");
        Button go = DialogChrome.danger("Tiếp tục thực thi");
        go.setDefaultButton(false);
        cancel.setOnAction(e -> chrome.close());
        go.setOnAction(e -> {
            result[0] = new Result(true, 0);
            chrome.close();
        });
        VBox box = new VBox(6, new VBox(header, list), hint);
        chrome.content(box).footer(new javafx.scene.Node[]{q}, cancel, go).size(520, -1);
        Platform.runLater(cancel::requestFocus);
        chrome.showAndWait();
        return result[0];
    }
}
