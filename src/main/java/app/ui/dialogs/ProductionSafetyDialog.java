package app.ui.dialogs;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * SCR-DLG-007b production safety (LLTool sql-protection.js "CẢNH BÁO MÔI TRƯỜNG PRODUCTION").
 * Shown before DML / DDL runs on a connection whose environment is PROD. Default focus: "Hủy".
 */
public final class ProductionSafetyDialog {

    private ProductionSafetyDialog() {
    }

    public static boolean show(Window owner, String queryType, String connection, String database) {
        boolean[] ok = {false};
        DialogChrome chrome = DialogChrome.create(owner, "Cảnh báo môi trường PRODUCTION", "fth-alert-octagon").iconStyle("danger");

        Label prod = new Label("PRODUCTION");
        prod.getStyleClass().addAll("env-badge-chip", "prod");
        Label type = new Label(queryType);
        type.getStyleClass().add("env-badge-chip");
        HBox badges = new HBox(6, prod, type);
        badges.setAlignment(Pos.CENTER_LEFT);

        Label msg = new Label("Câu lệnh này sẽ thay đổi dữ liệu hoặc cấu trúc trên môi trường production.");
        msg.getStyleClass().add("dialog-message");
        msg.setWrapText(true);

        GridPane info = new GridPane();
        info.setHgap(8);
        info.setVgap(2);
        info.getStyleClass().add("prod-info");
        Label l1 = new Label("Kết nối");
        Label l2 = new Label("Database");
        l1.getStyleClass().add("dialog-label");
        l2.getStyleClass().add("dialog-label");
        info.addRow(0, l1, new Label(connection != null ? connection : "—"));
        info.addRow(1, l2, new Label(database != null && !database.isBlank() ? database : "—"));

        Label q = new Label("Bạn có chắc muốn tiếp tục thực thi?");
        q.getStyleClass().add("guard-question");

        Button cancel = DialogChrome.cancel("Hủy (Esc)");
        Button go = DialogChrome.danger("Tôi hiểu rủi ro, thực thi");
        go.setDefaultButton(false);
        cancel.setOnAction(e -> chrome.close());
        go.setOnAction(e -> {
            ok[0] = true;
            chrome.close();
        });
        chrome.content(new VBox(8, badges, msg, info, q)).footer(null, cancel, go).size(480, -1);
        Platform.runLater(cancel::requestFocus);
        chrome.showAndWait();
        return ok[0];
    }
}
