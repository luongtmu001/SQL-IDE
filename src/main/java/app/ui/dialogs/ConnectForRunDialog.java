package app.ui.dialogs;

import javafx.geometry.HPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Run on a tab whose session is not open (never opened, closed by an Explorer disconnect, or lost): the user
 * confirms where the statement will run before the session is opened (user decision 2026-11-06).
 */
public final class ConnectForRunDialog {

    private ConnectForRunDialog() {
    }

    /** @return true = connect and run. */
    public static boolean show(Window owner, boolean lost, String connection, String server, String database, String schema) {
        boolean[] result = {false};
        DialogChrome chrome = DialogChrome.create(owner, lost ? "Mất kết nối" : "Kết nối để chạy",
                lost ? "fth-alert-triangle" : "fth-zap");
        if (lost) chrome.iconStyle("danger");

        Label msg = new Label(lost
                ? "Phiên của tab tới \"" + connection + "\" đã bị ngắt. Kết nối lại rồi chạy câu lệnh?"
                : "Tab chưa kết nối tới \"" + connection + "\". Kết nối rồi chạy câu lệnh?");
        msg.getStyleClass().add("dialog-message");
        msg.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        ColumnConstraints keys = new ColumnConstraints();
        keys.setHalignment(HPos.LEFT);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keys, values);
        row(grid, 0, "Server", server);
        row(grid, 1, "Database", database);
        row(grid, 2, "Schema", schema);

        VBox box = new VBox(10, msg, grid);
        if (lost) {
            Label note = new Label("Phiên mới không còn bảng tạm, transaction hay thiết lập SET của phiên cũ.");
            note.getStyleClass().add("dialog-label");
            note.setWrapText(true);
            box.getChildren().add(note);
        }

        Button cancel = DialogChrome.cancel("Hủy");
        Button ok = DialogChrome.primary(lost ? "Kết nối lại và chạy" : "Kết nối và chạy");
        cancel.setOnAction(e -> chrome.close());
        ok.setOnAction(e -> {
            result[0] = true;
            chrome.close();
        });
        chrome.content(box).footer(null, cancel, ok).size(440, -1);
        ok.setDefaultButton(true);
        chrome.showAndWait();
        return result[0];
    }

    private static void row(GridPane grid, int r, String key, String value) {
        Label k = new Label(key + ":");
        k.getStyleClass().add("dialog-label");
        Label v = new Label(value != null && !value.isBlank() ? value : "(mặc định)");
        v.getStyleClass().add("dialog-message");
        v.setWrapText(true);
        grid.add(k, 0, r);
        grid.add(v, 1, r);
    }
}
