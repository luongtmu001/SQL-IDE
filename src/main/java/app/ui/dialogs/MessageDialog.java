package app.ui.dialogs;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Phase 6: replaces the system {@code Alert}s (info / warning / error / confirm) with
 * {@link DialogChrome} so every message box follows R-UI-01.
 */
public final class MessageDialog {

    public enum Kind { INFO, SUCCESS, WARNING, ERROR, CONFIRM }

    private static final int LONG_TEXT = 260;

    private MessageDialog() {
    }

    public static void show(Window owner, Kind kind, String title, String header, String content) {
        build(owner, kind, title, header, content, null, null, false, null).showAndWait();
    }

    public static void show(Window owner, Kind kind, String title, String header, Node extra) {
        build(owner, kind, title, header, null, extra, null, false, null).showAndWait();
    }

    /** Confirmation with Hủy (default on Esc) and an action button; {@code danger} paints it red. */
    public static boolean confirm(Window owner, String title, String header, String content, String okText, boolean danger) {
        boolean[] ok = {false};
        build(owner, Kind.CONFIRM, title, header, content, null, () -> ok[0] = true, danger, okText).showAndWait();
        return ok[0];
    }

    static DialogChrome build(Window owner, Kind kind, String title, String header, String content,
                                      Node extra, Runnable onOk, boolean danger, String okText) {
        String iconLiteral = switch (kind) {
            case SUCCESS -> "fth-check-circle";
            case WARNING -> "fth-alert-triangle";
            case ERROR -> "fth-alert-circle";
            case CONFIRM -> "fth-help-circle";
            default -> "fth-info";
        };
        String tone = switch (kind) {
            case SUCCESS -> "success";
            case WARNING, CONFIRM -> "warning";
            case ERROR -> "danger";
            default -> "info";
        };
        DialogChrome chrome = DialogChrome.create(owner, title != null ? title : "Thông báo", iconLiteral);
        if (!"info".equals(tone) && !"success".equals(tone)) chrome.iconStyle(tone);

        FontIcon big = new FontIcon(iconLiteral);
        big.getStyleClass().addAll("message-icon", tone);

        VBox text = new VBox(6);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        if (header != null && !header.isBlank()) {
            Label h = new Label(header);
            h.getStyleClass().add("message-header");
            h.setWrapText(true);
            text.getChildren().add(h);
        }
        boolean longText = content != null && (content.length() > LONG_TEXT || content.lines().count() > 6);
        if (content != null && !content.isBlank() && !content.equals(header)) {
            if (longText) {
                TextArea area = new TextArea(content);
                area.setEditable(false);
                area.setWrapText(true);
                area.setPrefRowCount(8);
                area.getStyleClass().add("message-detail");
                text.getChildren().add(area);
            } else {
                Label c = new Label(content);
                c.getStyleClass().add("dialog-message");
                c.setWrapText(true);
                text.getChildren().add(c);
            }
        }
        if (extra != null) text.getChildren().add(extra);

        HBox body = new HBox(14, big, text);
        body.getStyleClass().add("message-body");

        Node[] left = null;
        if (kind == Kind.ERROR && content != null && !content.isBlank()) {
            Hyperlink copy = new Hyperlink("Sao chép");
            copy.getStyleClass().add("dialog-link");
            copy.setOnAction(e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString((header != null ? header + "\n" : "") + content);
                Clipboard.getSystemClipboard().setContent(cc);
                copy.setText("Đã sao chép");
            });
            left = new Node[]{copy};
        }

        chrome.content(body).size(longText || extra != null ? 520 : 420, -1);

        if (onOk != null) {
            Button cancel = DialogChrome.cancel("Hủy");
            String okLabel = okText != null ? okText : "Đồng ý";
            Button ok = danger ? DialogChrome.danger(okLabel) : DialogChrome.primary(okLabel);
            cancel.setOnAction(e -> chrome.close());
            ok.setOnAction(e -> {
                onOk.run();
                chrome.close();
            });
            chrome.footer(left, cancel, ok);
        } else {
            Button ok = DialogChrome.primary("OK");
            ok.setCancelButton(true);
            ok.setOnAction(e -> chrome.close());
            chrome.footer(left, ok);
        }
        return chrome;
    }
}
