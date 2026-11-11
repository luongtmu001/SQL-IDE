package app.ui.designer;

import app.ui.editor.EmbeddedMonaco;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.UUID;

/**
 * Embedded read-only Live DDL Preview pane (SCR-TD-001 / TD3).
 * Uses {@link EmbeddedMonaco} to display real-time generated DDL with syntax highlighting.
 */
public class DdlPreviewPane extends VBox {

    private final EmbeddedMonaco monaco;
    private final String modelId = "td_ddl_preview_" + UUID.randomUUID().toString().substring(0, 8);

    public DdlPreviewPane() {
        getStyleClass().add("td-ddl-preview-pane");
        setPrefHeight(160);
        setMinHeight(60);

        HBox header = buildHeader();
        monaco = new EmbeddedMonaco(modelId, "sql", "-- Đang tải DDL...");
        monaco.setReadOnly(true);

        VBox.setVgrow(monaco.node(), Priority.ALWAYS);
        getChildren().addAll(header, monaco.node());
    }

    private HBox buildHeader() {
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(2, 8, 2, 8));
        bar.getStyleClass().add("td-ddl-preview-header");

        FontIcon icon = new FontIcon("fth-code");
        icon.setIconSize(13);

        Label title = new Label("Xem trước DDL");
        title.getStyleClass().add("td-ddl-preview-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnCopy = new Button();
        btnCopy.getStyleClass().addAll("button", "small");
        FontIcon copyIcon = new FontIcon("fth-copy");
        copyIcon.setIconSize(13);
        btnCopy.setGraphic(copyIcon);
        btnCopy.setTooltip(new Tooltip("Sao chép DDL vào Clipboard"));
        btnCopy.setOnAction(e -> {
            String text = monaco.getText();
            if (text != null && !text.isBlank()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                Clipboard.getSystemClipboard().setContent(content);
            }
        });

        Button btnFormat = new Button();
        btnFormat.getStyleClass().addAll("button", "small");
        FontIcon formatIcon = new FontIcon("fth-align-left");
        formatIcon.setIconSize(13);
        btnFormat.setGraphic(formatIcon);
        btnFormat.setTooltip(new Tooltip("Định dạng SQL"));
        btnFormat.setOnAction(e -> monaco.format());

        bar.getChildren().addAll(icon, title, spacer, btnCopy, btnFormat);
        return bar;
    }

    public void setSql(String sql) {
        Platform.runLater(() -> monaco.setText(sql != null ? sql : ""));
    }

    public String getSql() {
        return monaco.getText();
    }
}

