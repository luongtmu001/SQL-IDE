package app.ui.dialogs;

import app.ui.editor.EmbeddedMonaco;
import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.application.tabledesigner.model.MigrationStatement;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;
import java.util.UUID;

/**
 * Save / Migration Confirmation Dialog for Table Designer (SCR-TD-001 / TD4).
 * Built with DialogChrome, adhering to design tokens and component standards (G-DL-1).
 */
public final class SaveDesignDialog {

    public enum Action {
        EXECUTE,
        OPEN_IN_SQL_TAB,
        CANCEL
    }

    public record SaveDesignResult(
            Action action,
            String finalSql
    ) {}

    private SaveDesignDialog() {}

    public static Optional<SaveDesignResult> show(
            Window owner,
            String schema,
            String table,
            MigrationPlan plan,
            String fullMigrationSql) {

        final SaveDesignResult[] resultHolder = new SaveDesignResult[1];
        DialogChrome chrome = createDialog(owner, schema, table, plan, fullMigrationSql, res -> resultHolder[0] = res);
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
            MigrationPlan plan,
            String fullMigrationSql,
            java.util.function.Consumer<SaveDesignResult> resultConsumer) {

        DialogChrome chrome = DialogChrome.create(owner, "Lưu thay đổi thiết kế bảng", "fth-save");
        chrome.subtitle("[" + (schema != null ? schema : "dbo") + "].[" + (table != null ? table : "Table") + "]");

        VBox content = new VBox(10);
        content.setPadding(new Insets(12, 16, 12, 16));
        content.setPrefWidth(700);
        content.setPrefHeight(480);

        // 1. Summary Header
        int statementCount = (plan != null && plan.statements() != null) ? plan.statements().size() : 1;
        boolean hasDestructiveChanges = plan != null && plan.statements().stream().anyMatch(MigrationStatement::destructive);

        HBox summaryBox = new HBox(10);
        summaryBox.setAlignment(Pos.CENTER_LEFT);

        Label lblStats = new Label(statementCount + " câu lệnh DDL sẵn sàng áp dụng");
        lblStats.getStyleClass().add("dialog-message");

        summaryBox.getChildren().add(lblStats);

        // 2. Destructive changes warning banner (G-DL-1)
        if (hasDestructiveChanges) {
            HBox warnBanner = new HBox(8);
            warnBanner.setAlignment(Pos.CENTER_LEFT);
            warnBanner.getStyleClass().addAll("dialog-chrome-banner", "danger");

            FontIcon warnIcon = new FontIcon("fth-alert-triangle");
            warnIcon.setIconSize(16);

            Label lblWarn = new Label("Cảnh báo: Kịch bản có chứa thao tác xóa hoặc thay đổi có nguy cơ làm mất dữ liệu!");
            lblWarn.setWrapText(true);

            warnBanner.getChildren().addAll(warnIcon, lblWarn);
            content.getChildren().add(warnBanner);
        }

        // 3. SQL Preview Area with EmbeddedMonaco (G-DL-1)
        Label lblSqlTitle = new Label("Xem trước kịch bản DDL sẽ thực thi:");
        lblSqlTitle.getStyleClass().add("dialog-header");

        String modelId = "save_design_" + UUID.randomUUID();
        EmbeddedMonaco monaco = new EmbeddedMonaco(modelId, "sql", fullMigrationSql != null ? fullMigrationSql : "-- Không có câu lệnh DDL");
        monaco.setReadOnly(true);
        monaco.setShowStatusBar(false);
        VBox.setVgrow(monaco.node(), Priority.ALWAYS);

        content.getChildren().addAll(summaryBox, lblSqlTitle, monaco.node());

        // 4. Action Buttons (G-DL-1: Huỷ │ Mở trong tab SQL │ Thực thi)
        Button btnCancel = DialogChrome.cancel("Hủy");
        btnCancel.setOnAction(e -> {
            if (resultConsumer != null) resultConsumer.accept(new SaveDesignResult(Action.CANCEL, null));
            chrome.close();
        });

        Button btnOpenSql = DialogChrome.secondary("Mở trong tab SQL");
        FontIcon sqlIcon = new FontIcon("fth-external-link");
        sqlIcon.setIconSize(13);
        btnOpenSql.setGraphic(sqlIcon);
        btnOpenSql.setTooltip(new Tooltip("Mở kịch bản trong tab SQL mới để xem và chỉnh sửa trước"));
        btnOpenSql.setOnAction(e -> {
            String sql = monaco.getText() != null ? monaco.getText() : fullMigrationSql;
            if (resultConsumer != null) resultConsumer.accept(new SaveDesignResult(Action.OPEN_IN_SQL_TAB, sql));
            chrome.close();
        });

        Button btnExecute = hasDestructiveChanges ? DialogChrome.danger("Thực thi") : DialogChrome.primary("Thực thi");
        FontIcon runIcon = new FontIcon("fth-play");
        runIcon.setIconSize(13);
        btnExecute.setGraphic(runIcon);
        btnExecute.setTooltip(new Tooltip("Thực thi kịch bản migration trực tiếp trong một transaction"));
        btnExecute.setOnAction(e -> {
            String sql = monaco.getText() != null ? monaco.getText() : fullMigrationSql;
            if (resultConsumer != null) resultConsumer.accept(new SaveDesignResult(Action.EXECUTE, sql));
            chrome.close();
        });

        chrome.content(content);
        // Order: Huỷ | Mở trong tab SQL | Thực thi
        chrome.footer(null, btnCancel, btnOpenSql, btnExecute);

        // By default, if destructive, focus cancel; otherwise focus execute
        if (hasDestructiveChanges) {
            btnCancel.requestFocus();
        } else {
            btnExecute.requestFocus();
        }

        return chrome;
    }
}
