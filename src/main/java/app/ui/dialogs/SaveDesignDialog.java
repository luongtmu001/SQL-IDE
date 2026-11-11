package app.ui.dialogs;

import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.application.tabledesigner.model.MigrationStatement;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;

/**
 * Save / Migration Confirmation Dialog for Table Designer (SCR-TD-001 / TD4).
 * Built with DialogChrome, adhering to 02-design-principles and 04-component-rules.
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
        content.setPrefWidth(680);
        content.setPrefHeight(460);

        // 1. Summary Header
        int statementCount = (plan != null && plan.statements() != null) ? plan.statements().size() : 1;
        boolean hasDestructiveChanges = plan != null && plan.statements().stream().anyMatch(MigrationStatement::destructive);

        HBox summaryBox = new HBox(10);
        summaryBox.setAlignment(Pos.CENTER_LEFT);

        Label lblStats = new Label(statementCount + " câu lệnh DDL sẵn sàng áp dụng");
        lblStats.getStyleClass().add("dialog-message");

        summaryBox.getChildren().add(lblStats);

        // 2. Destructive changes warning banner
        if (hasDestructiveChanges) {
            HBox warnBanner = new HBox(8);
            warnBanner.setAlignment(Pos.CENTER_LEFT);
            warnBanner.setPadding(new Insets(8, 12, 8, 12));
            warnBanner.setStyle("-fx-background-color: -color-warning-subtle; -fx-background-radius: 6px; -fx-border-color: -color-warning-emphasis; -fx-border-radius: 6px;");

            FontIcon warnIcon = new FontIcon("fth-alert-triangle");
            warnIcon.setIconSize(16);
            warnIcon.setStyle("-fx-icon-color: -color-warning-fg;");

            Label lblWarn = new Label("Cảnh báo: Kịch bản có chứa thao tác xóa hoặc thay đổi có nguy cơ làm mất dữ liệu!");
            lblWarn.setStyle("-fx-text-fill: -color-warning-fg; -fx-font-weight: bold;");

            warnBanner.getChildren().addAll(warnIcon, lblWarn);
            content.getChildren().add(warnBanner);
        }

        // 3. SQL Preview Area
        Label lblSqlTitle = new Label("Xem trước kịch bản DDL sẽ thực thi:");
        lblSqlTitle.getStyleClass().add("dialog-header");

        TextArea txtSql = new TextArea(fullMigrationSql != null ? fullMigrationSql : "-- Không có câu lệnh DDL");
        txtSql.setEditable(false);
        txtSql.setWrapText(false);
        txtSql.setStyle("-fx-font-family: 'JetBrains Mono', 'Cascadia Code', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(txtSql, Priority.ALWAYS);

        content.getChildren().addAll(summaryBox, lblSqlTitle, txtSql);

        // 4. Action Buttons
        Button btnExecute = hasDestructiveChanges ? DialogChrome.danger("Thực thi ngay") : DialogChrome.primary("Thực thi ngay");
        FontIcon runIcon = new FontIcon("fth-play");
        runIcon.setIconSize(13);
        btnExecute.setGraphic(runIcon);
        btnExecute.setTooltip(new Tooltip("Thực thi kịch bản migration trực tiếp trong một transaction"));
        btnExecute.setOnAction(e -> {
            if (resultConsumer != null) resultConsumer.accept(new SaveDesignResult(Action.EXECUTE, txtSql.getText()));
            chrome.close();
        });

        Button btnOpenSql = DialogChrome.secondary("Mở trong tab SQL");
        FontIcon sqlIcon = new FontIcon("fth-external-link");
        sqlIcon.setIconSize(13);
        btnOpenSql.setGraphic(sqlIcon);
        btnOpenSql.setTooltip(new Tooltip("Mở kịch bản trong tab SQL mới để xem và chỉnh sửa trước"));
        btnOpenSql.setOnAction(e -> {
            if (resultConsumer != null) resultConsumer.accept(new SaveDesignResult(Action.OPEN_IN_SQL_TAB, txtSql.getText()));
            chrome.close();
        });

        Button btnCancel = DialogChrome.cancel("Hủy");
        btnCancel.setOnAction(e -> {
            if (resultConsumer != null) resultConsumer.accept(new SaveDesignResult(Action.CANCEL, null));
            chrome.close();
        });

        chrome.content(content);
        chrome.footer(new javafx.scene.Node[]{btnOpenSql}, btnCancel, btnExecute);

        // By default, if destructive, focus cancel; otherwise focus execute
        if (hasDestructiveChanges) {
            btnCancel.requestFocus();
        } else {
            btnExecute.requestFocus();
        }

        return chrome;
    }
}

