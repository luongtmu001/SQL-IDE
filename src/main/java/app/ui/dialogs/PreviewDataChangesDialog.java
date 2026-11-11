package app.ui.dialogs;

import app.ui.components.Badge;
import app.ui.editor.EmbeddedMonaco;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;
import java.util.UUID;

/**
 * Dialog to preview generated DML statements before executing them in Table Data Editor.
 * Standardized with 3 counting badges, EmbeddedMonaco preview, and normalized action order (G-DL-2).
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
        return show(owner, schema, table, insertCount, updateCount, deleteCount, script, true);
    }

    public static Optional<PreviewResult> show(
            Window owner,
            String schema,
            String table,
            int insertCount,
            int updateCount,
            int deleteCount,
            String script,
            boolean allowExecute) {

        final PreviewResult[] resultHolder = new PreviewResult[1];
        DialogChrome chrome = createDialog(owner, schema, table, insertCount, updateCount, deleteCount, script,
                allowExecute, res -> resultHolder[0] = res);
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
        return createDialog(owner, schema, table, insertCount, updateCount, deleteCount, script, true, resultConsumer);
    }

    public static DialogChrome createDialog(
            Window owner,
            String schema,
            String table,
            int insertCount,
            int updateCount,
            int deleteCount,
            String script,
            boolean allowExecute,
            java.util.function.Consumer<PreviewResult> resultConsumer) {

        DialogChrome chrome = DialogChrome.create(owner, "Xem trước thay đổi dữ liệu", "fth-database");
        chrome.subtitle("[" + (schema != null ? schema : "dbo") + "].[" + (table != null ? table : "Table") + "]");

        VBox content = new VBox(10);
        content.setPadding(new Insets(12, 16, 12, 16));
        content.setPrefWidth(700);
        content.setPrefHeight(460);

        // 1. Top Badges Row (G-DL-2: 3 count badges)
        HBox summaryBox = new HBox(8);
        summaryBox.setAlignment(Pos.CENTER_LEFT);

        Label lblSummary = new Label("Thay đổi DML:");
        lblSummary.getStyleClass().add("dialog-message");

        Badge badgeIns = new Badge("+" + insertCount + " thêm", Badge.Variant.SUCCESS);
        Badge badgeUpd = new Badge("~" + updateCount + " sửa", Badge.Variant.WARNING);
        Badge badgeDel = new Badge("-" + deleteCount + " xóa", Badge.Variant.DANGER);

        summaryBox.getChildren().addAll(lblSummary, badgeIns, badgeUpd, badgeDel);

        // 2. SQL Preview with EmbeddedMonaco (G-DL-2)
        Label lblSqlTitle = new Label("Kịch bản SQL thực thi trong 1 transaction:");
        lblSqlTitle.getStyleClass().add("dialog-header");

        String modelId = "preview_data_" + UUID.randomUUID();
        EmbeddedMonaco monaco = new EmbeddedMonaco(modelId, "sql", script != null ? script : "-- Không có câu lệnh thay đổi nào");
        monaco.setReadOnly(true);
        monaco.setShowStatusBar(false);
        VBox.setVgrow(monaco.node(), Priority.ALWAYS);

        content.getChildren().addAll(summaryBox, lblSqlTitle, monaco.node());

        // 3. Action Buttons (G-DL-2)
        Button btnOpenSql = DialogChrome.secondary("Mở trong tab SQL");
        FontIcon sqlIcon = new FontIcon("fth-external-link");
        sqlIcon.setIconSize(13);
        btnOpenSql.setGraphic(sqlIcon);
        btnOpenSql.setOnAction(e -> {
            String s = monaco.getText() != null ? monaco.getText() : script;
            if (resultConsumer != null) resultConsumer.accept(new PreviewResult(Action.OPEN_IN_SQL_TAB, s));
            chrome.close();
        });

        if (allowExecute) {
            Button btnCancel = DialogChrome.cancel("Hủy");
            btnCancel.setOnAction(e -> {
                if (resultConsumer != null) resultConsumer.accept(new PreviewResult(Action.CANCEL, null));
                chrome.close();
            });

            Button btnExecute = DialogChrome.primary("Thực thi");
            FontIcon runIcon = new FontIcon("fth-play");
            runIcon.setIconSize(13);
            btnExecute.setGraphic(runIcon);
            btnExecute.setOnAction(e -> {
                String s = monaco.getText() != null ? monaco.getText() : script;
                if (resultConsumer != null) resultConsumer.accept(new PreviewResult(Action.EXECUTE, s));
                chrome.close();
            });

            // Order: Huỷ │ Mở trong tab SQL │ Thực thi
            chrome.footer(null, btnCancel, btnOpenSql, btnExecute);
            btnExecute.requestFocus();
        } else {
            // Read-only preview (Xem DML): Đóng │ Mở trong tab SQL
            Button btnClose = DialogChrome.cancel("Đóng");
            btnClose.setOnAction(e -> {
                if (resultConsumer != null) resultConsumer.accept(new PreviewResult(Action.CANCEL, null));
                chrome.close();
            });

            chrome.footer(null, btnClose, btnOpenSql);
            btnClose.requestFocus();
        }

        chrome.content(content);
        return chrome;
    }
}
