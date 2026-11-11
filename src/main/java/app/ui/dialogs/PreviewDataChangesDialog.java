package app.ui.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;

/**
 * Dialog to preview generated DML statements before executing them in Table Data Editor.
 */
public final class PreviewDataChangesDialog {

    public enum Action {
        EXECUTE,
        OPEN_IN_SQL_TAB,
        CANCEL
    }

    public record PreviewResult(
            Action action,
            String script
    ) {}

    private PreviewDataChangesDialog() {}

    public static Optional<PreviewResult> show(
            Window owner,
            String schema,
            String table,
            int insertCount,
            int updateCount,
            int deleteCount,
            String script) {

        final PreviewResult[] resultHolder = new PreviewResult[1];
        DialogChrome chrome = createDialog(owner, schema, table, insertCount, updateCount, deleteCount, script,
                res -> resultHolder[0] = res);
        chrome.showAndWait();

        if (resultHolder[0] == null || resultHolder[0].action() == Action.CANCEL) {
            return Optional.empty();
        }
        return Optional.of(resultHolder[0]);
    }

    public static DialogChrome createDialog(
            Window owner,
            String schema,
            String table,
            int insertCount,
            int updateCount,
            int deleteCount,
            String script,
            java.util.function.Consumer<PreviewResult> resultConsumer) {

        DialogChrome chrome = DialogChrome.create(owner, "Xem trước thay đổi dữ liệu", "fth-database");
        chrome.subtitle("[" + (schema != null ? schema : "dbo") + "].[" + (table != null ? table : "Table") + "]");

        VBox content = new VBox(10);
        content.setPadding(new Insets(12, 16, 12, 16));
        content.setPrefWidth(680);
        content.setPrefHeight(440);

        HBox summaryBox = new HBox(8);
        summaryBox.setAlignment(Pos.CENTER_LEFT);

        Label lblStats = new Label(String.format("Kịch bản DML gồm: %d Thêm mới, %d Cập nhật, %d Xóa",
                insertCount, updateCount, deleteCount));
        lblStats.getStyleClass().add("dialog-message");

        summaryBox.getChildren().add(lblStats);

        Label lblSqlTitle = new Label("Kịch bản SQL thực thi trong 1 transaction:");
        lblSqlTitle.getStyleClass().add("dialog-header");

        TextArea txtSql = new TextArea(script != null ? script : "-- Không có câu lệnh thay đổi nào");
        txtSql.setEditable(false);
        txtSql.setWrapText(false);
        txtSql.setStyle("-fx-font-family: 'JetBrains Mono', 'Cascadia Code', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(txtSql, Priority.ALWAYS);

        content.getChildren().addAll(summaryBox, lblSqlTitle, txtSql);

        Button btnExecute = DialogChrome.primary("Thực thi ngay");
        FontIcon runIcon = new FontIcon("fth-play");
        runIcon.setIconSize(13);
        btnExecute.setGraphic(runIcon);
        btnExecute.setOnAction(e -> {
            if (resultConsumer != null) resultConsumer.accept(new PreviewResult(Action.EXECUTE, txtSql.getText()));
            chrome.close();
        });

        Button btnOpenSql = DialogChrome.secondary("Mở trong tab SQL");
        FontIcon sqlIcon = new FontIcon("fth-external-link");
        sqlIcon.setIconSize(13);
        btnOpenSql.setGraphic(sqlIcon);
        btnOpenSql.setOnAction(e -> {
            if (resultConsumer != null) resultConsumer.accept(new PreviewResult(Action.OPEN_IN_SQL_TAB, txtSql.getText()));
            chrome.close();
        });

        Button btnCancel = DialogChrome.cancel("Hủy");
        btnCancel.setOnAction(e -> {
            if (resultConsumer != null) resultConsumer.accept(new PreviewResult(Action.CANCEL, null));
            chrome.close();
        });

        chrome.content(content);
        chrome.footer(new javafx.scene.Node[]{btnOpenSql}, btnCancel, btnExecute);

        btnExecute.requestFocus();
        return chrome;
    }
}

