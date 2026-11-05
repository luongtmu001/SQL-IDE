package app.ui.dialogs;

import app.application.AppInfo;
import app.services.SettingsService;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DLG-012 About (LLTool about_window.html), DialogChrome + R-UI-01.
 * No Đóng button: the window × / Esc closes it (same rule as Settings, Gate C 2026-11-05).
 */
public final class AboutDialog {

    private AboutDialog() {
    }

    public static void show(Window owner, Runnable onCheckUpdates) {
        DialogChrome chrome = DialogChrome.create(owner, "Thông tin phần mềm", "fth-info");

        FontIcon logo = new FontIcon("fth-database");
        logo.getStyleClass().add("about-logo");
        Label name = new Label(AppInfo.NAME);
        name.getStyleClass().add("about-name");
        Label tagline = new Label(AppInfo.TAGLINE);
        tagline.getStyleClass().add("dialog-hint");
        Label version = new Label("v" + AppInfo.VERSION);
        version.getStyleClass().addAll("about-badge", "accent");
        Label channel = new Label(AppInfo.CHANNEL);
        channel.getStyleClass().add("about-badge");
        javafx.scene.layout.HBox badges = new javafx.scene.layout.HBox(6, version, channel);
        badges.setAlignment(Pos.CENTER);

        VBox head = new VBox(4, logo, name, tagline, badges);
        head.setAlignment(Pos.CENTER);
        head.getStyleClass().add("about-head");

        Map<String, String> info = info();
        GridPane grid = new GridPane();
        grid.getStyleClass().add("about-grid");
        grid.setHgap(12);
        grid.setVgap(0);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setHalignment(HPos.RIGHT); // R-UI-01: captions right-aligned
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);
        int row = 0;
        for (var e : info.entrySet()) {
            Label k = new Label(e.getKey());
            k.getStyleClass().add("about-key");
            Label v = new Label(e.getValue());
            v.getStyleClass().add("about-value");
            v.setWrapText(true);
            grid.add(k, 0, row);
            grid.add(v, 1, row);
            row++;
        }

        Label copyright = new Label(AppInfo.COPYRIGHT);
        copyright.getStyleClass().add("dialog-hint");
        VBox body = new VBox(14, head, grid, copyright);
        body.setAlignment(Pos.TOP_CENTER);

        Hyperlink copy = new Hyperlink("Sao chép thông tin");
        copy.getStyleClass().add("dialog-link");
        copy.setOnAction(e -> {
            StringBuilder sb = new StringBuilder(AppInfo.NAME).append('\n');
            info.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
            ClipboardContent cc = new ClipboardContent();
            cc.putString(sb.toString().trim());
            Clipboard.getSystemClipboard().setContent(cc);
            copy.setText("Đã sao chép");
        });
        Button check = DialogChrome.primary("Kiểm tra cập nhật");
        check.setGraphic(new FontIcon("fth-refresh-cw"));
        check.setOnAction(e -> {
            chrome.close();
            if (onCheckUpdates != null) onCheckUpdates.run();
        });

        chrome.content(body).footer(new Node[]{copy}, check).size(440, -1);
        chrome.showAndWait();
    }

    private static Map<String, String> info() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Phiên bản", AppInfo.VERSION);
        Path settings = SettingsService.getInstance().getSettingsFilePath();
        m.put("Thư mục dữ liệu", settings != null && settings.getParent() != null ? settings.getParent().toString() : "—");
        m.put("Nền tảng", AppInfo.platform());
        m.put("Runtime", AppInfo.javaRuntime());
        m.put("Giao diện", "AtlantaFX · Monaco Editor");
        return m;
    }
}
