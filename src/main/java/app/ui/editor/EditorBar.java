package app.ui.editor;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The small bar under the SQL editor (like SSMS): zoom % box, session PID, and on the right cursor position,
 * indentation, line ending and encoding. It lives inside {@link MonacoEditorHost}, so it follows the editor and
 * is not shown for tabs without a text editor. The window status bar keeps connection / database / schema / user.
 *
 * <p>Zoom: the box is editable. Pick 20 … 400 from the list or type any whole percentage (clamped to
 * {@value #ZOOM_MIN}–{@value #ZOOM_MAX}); the editor itself steps by one font size per Ctrl+Wheel notch.</p>
 */
public final class EditorBar extends HBox {

    public static final int ZOOM_MIN = 20;
    public static final int ZOOM_MAX = 400;
    public static final List<Integer> ZOOM_LEVELS = List.of(20, 50, 75, 100, 125, 150, 200, 300, 400);

    private final ComboBox<String> zoomBox = new ComboBox<>();
    private final Label issuesLabel = new Label();
    private final Label pidLabel = new Label();
    private final Label cursorLabel = new Label("Ln: 1, Ch: 1");
    private final Label indentLabel = new Label("SPACES: 4");
    private final Label eolLabel = new Label("LF");
    private final Label encodingLabel = new Label("UTF-8");

    private IntConsumer onZoomRequested;
    private Runnable onGoToLine;
    private Runnable onOpenSettings;
    private Runnable onToggleIndent;
    private Runnable onToggleEol;
    private int zoom = 100;
    private boolean updating;
    private String pid;

    public EditorBar() {
        getStyleClass().add("editor-bar");
        setAlignment(Pos.CENTER_LEFT);
        setMinHeight(24);
        setPrefHeight(24);
        setMaxHeight(24);

        for (int level : ZOOM_LEVELS) zoomBox.getItems().add(level + " %");
        zoomBox.setEditable(true);
        zoomBox.getStyleClass().add("editor-bar-zoom");
        zoomBox.setPrefWidth(66);   // just wide enough for "400 %" and the arrow
        zoomBox.setMinWidth(66);
        zoomBox.setMaxWidth(66);
        zoomBox.setVisibleRowCount(ZOOM_LEVELS.size());
        zoomBox.setTooltip(new Tooltip("Zoom editor: chọn hoặc gõ % (20–400). Ctrl + cuộn chuột: mỗi nấc 1 cỡ chữ."));
        // list pick, Enter and leaving the box all end in commitZoom(text)
        zoomBox.valueProperty().addListener((o, a, b) -> commitZoom(b));
        zoomBox.getEditor().setOnAction(e -> commitZoom(zoomBox.getEditor().getText()));
        zoomBox.getEditor().focusedProperty().addListener((o, was, focused) -> {
            if (!focused) commitZoom(zoomBox.getEditor().getText());
        });
        zoomBox.getEditor().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                showZoom(zoom);
                e.consume();
            }
        });
        showZoom(100);

        issuesLabel.getStyleClass().add("editor-bar-text");
        issuesLabel.setTooltip(new Tooltip("Kiểm tra cú pháp của tab"));
        setIssues(null);

        FontIcon pidIcon = new FontIcon("fth-terminal");
        pidIcon.getStyleClass().add("editor-bar-icon");
        pidLabel.setGraphic(pidIcon);
        pidLabel.getStyleClass().addAll("editor-bar-text", "editor-bar-badge");
        pidLabel.setTooltip(new Tooltip("Phiên kết nối của tab (SPID / PID) — bấm để sao chép"));
        pidLabel.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && pid != null) {
                ClipboardContent c = new ClipboardContent();
                c.putString(pid);
                Clipboard.getSystemClipboard().setContent(c);
            }
        });
        setPidVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        for (Label l : List.of(cursorLabel, indentLabel, eolLabel, encodingLabel)) {
            l.getStyleClass().addAll("editor-bar-text", "editor-bar-entry");
        }
        cursorLabel.setTooltip(new Tooltip("Đi tới dòng (Ctrl+G)"));
        cursorLabel.setOnMouseClicked(e -> { if (onGoToLine != null) onGoToLine.run(); });
        indentLabel.setTooltip(new Tooltip("Bấm để đổi giữa TABS và SPACES: 4"));
        indentLabel.setOnMouseClicked(e -> { if (onToggleIndent != null) onToggleIndent.run(); });
        eolLabel.setTooltip(new Tooltip("Bấm để đổi giữa LF và CRLF"));
        eolLabel.setOnMouseClicked(e -> { if (onToggleEol != null) onToggleEol.run(); });

        getChildren().addAll(zoomBox, issuesLabel, pidLabel, spacer, cursorLabel, indentLabel, eolLabel, encodingLabel);
    }

    // ---------------------------------------------------------------- zoom

    public void setOnZoomRequested(IntConsumer c) { this.onZoomRequested = c; }

    /** Shows {@code percent} without notifying (the editor / status bar already know it). */
    public void setZoom(int percent) {
        zoom = clamp(percent);
        showZoom(zoom);
    }

    private void showZoom(int percent) {
        updating = true;
        try {
            String text = percent + " %";
            zoomBox.setValue(text);
            zoomBox.getEditor().setText(text);
        } finally {
            updating = false;
        }
    }

    private void commitZoom(String text) {
        if (updating) return;
        Integer p = parse(text);
        if (p == null) {
            showZoom(zoom);
            return;
        }
        p = clamp(p);
        showZoom(p);
        if (p != zoom) {
            zoom = p;
            if (onZoomRequested != null) onZoomRequested.accept(p);
        }
    }

    /** "150", "150%", "150 %" → 150; anything without digits → null. */
    static Integer parse(String text) {
        if (text == null) return null;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 5) return null;
        return Integer.parseInt(digits);
    }

    static int clamp(int percent) {
        return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, percent));
    }

    // ---------------------------------------------------------------- info

    /**
     * Syntax-check result of the tab: {@code null} = not checked (hidden), 0 = "No issues found",
     * n = "n issue(s) found".
     */
    public void setIssues(Integer errors) {
        boolean has = errors != null;
        issuesLabel.setVisible(has);
        issuesLabel.setManaged(has);
        if (!has) return;
        boolean ok = errors == 0;
        FontIcon icon = new FontIcon(ok ? "fth-check-circle" : "fth-x-circle");
        icon.getStyleClass().add(ok ? "editor-bar-icon-ok" : "editor-bar-icon-error");
        issuesLabel.setGraphic(icon);
        issuesLabel.setText(ok ? "No issues found" : errors + (errors == 1 ? " issue found" : " issues found"));
        issuesLabel.getStyleClass().removeAll("editor-bar-ok", "editor-bar-error");
        issuesLabel.getStyleClass().add(ok ? "editor-bar-ok" : "editor-bar-error");
    }

    public void setPid(String pid) {
        this.pid = (pid == null || pid.isBlank()) ? null : pid;
        pidLabel.setText(this.pid == null ? "" : "PID: " + this.pid);
        setPidVisible(this.pid != null);
    }

    private void setPidVisible(boolean v) {
        pidLabel.setVisible(v);
        pidLabel.setManaged(v);
    }

    public void setCursor(int line, int col) {
        cursorLabel.setText("Ln: " + line + ", Ch: " + col);
    }

    public void setIndentation(boolean insertSpaces, int size) {
        indentLabel.setText(insertSpaces ? "SPACES: " + Math.max(1, size) : "TABS");
    }

    public void setLineSeparator(String s) { eolLabel.setText(s); }

    public void setEncoding(String s) { encodingLabel.setText(s); }

    public void setOnGoToLine(Runnable r) { this.onGoToLine = r; }

    public void setOnOpenSettings(Runnable r) { this.onOpenSettings = r; }

    /** TABS ⇄ SPACES: 4 */
    public void setOnToggleIndent(Runnable r) { this.onToggleIndent = r; }

    /** LF ⇄ CRLF of the active document. */
    public void setOnToggleEol(Runnable r) { this.onToggleEol = r; }
}
