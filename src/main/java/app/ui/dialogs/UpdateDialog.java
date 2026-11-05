package app.ui.dialogs;

import app.application.AppInfo;
import app.services.NotificationService;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * DLG-013 Check for updates — MOCK ONLY (no network until Gate D).
 * States follow LLTool update_window.html: checking → latest / available (release notes)
 * → downloading (progress) → downloaded; or network error with "Kiểm tra lại".
 * The footer combo picks the mock scenario so every state can be reviewed.
 */
public final class UpdateDialog {

    private enum State { CHECKING, LATEST, AVAILABLE, DOWNLOADING, DONE, ERROR }

    private static final String[] SCENARIOS = {"Có bản mới", "Đã là bản mới nhất", "Lỗi mạng"};
    private static final String MOCK_LATEST = "1.1.0";
    private static final double MOCK_SIZE_MB = 20.0;
    private static final List<String> MOCK_NOTES = List.of(
            "Lưới kết quả nhanh hơn với bảng lớn",
            "IntelliSense gợi ý cột theo alias",
            "Sửa lỗi lưu phiên làm việc khi đóng ứng dụng");
    private static String scenario = SCENARIOS[0];

    private final DialogChrome chrome;
    private final VBox body = new VBox(12);
    private final HBox actions = new HBox(8);
    private final ComboBox<String> scenarioBox = new ComboBox<>();
    private Timeline download;
    private PauseTransition checking;
    private State state;

    private UpdateDialog(Window owner) {
        chrome = DialogChrome.create(owner, "Kiểm tra cập nhật", "fth-download-cloud").subtitle("dữ liệu mô phỏng");
        body.getStyleClass().add("update-body");
        actions.setAlignment(Pos.CENTER_RIGHT);

        scenarioBox.getItems().setAll(SCENARIOS);
        scenarioBox.setValue(scenario);
        scenarioBox.getStyleClass().add("update-scenario");
        scenarioBox.valueProperty().addListener((o, a, b) -> {
            if (b != null) scenario = b;
        });
        Label scenarioLabel = new Label("Kịch bản:");
        scenarioLabel.getStyleClass().add("dialog-hint");
        HBox left = new HBox(6, scenarioLabel, scenarioBox);
        left.setAlignment(Pos.CENTER_LEFT);

        chrome.content(body).footer(new Node[]{left}, actions).size(460, -1);
        chrome.onCloseRequest(this::canClose);
        chrome.stage().setOnHidden(e -> stopTimers());
    }

    public static void show(Window owner) {
        UpdateDialog d = new UpdateDialog(owner);
        d.check();
        d.chrome.showAndWait();
    }

    // ------------------------------------------------------------------ flow

    private void check() {
        stopTimers();
        render(State.CHECKING);
        checking = new PauseTransition(Duration.millis(1100));
        checking.setOnFinished(e -> render(switch (scenario) {
            case "Đã là bản mới nhất" -> State.LATEST;
            case "Lỗi mạng" -> State.ERROR;
            default -> State.AVAILABLE;
        }));
        checking.play();
    }

    private void startDownload() {
        render(State.DOWNLOADING);
    }

    private boolean canClose() {
        if (state != State.DOWNLOADING) return true;
        boolean stop = MessageDialog.confirm(chrome.stage(), "Hủy tải", "Đang tải bản cập nhật.",
                "Đóng cửa sổ sẽ hủy lượt tải này.", "Hủy tải", true);
        if (stop) stopTimers();
        return stop;
    }

    private void stopTimers() {
        if (download != null) download.stop();
        if (checking != null) checking.stop();
    }

    // ------------------------------------------------------------------ rendering

    private void render(State s) {
        state = s;
        body.getChildren().clear();
        actions.getChildren().clear();
        scenarioBox.setDisable(s == State.CHECKING || s == State.DOWNLOADING);
        switch (s) {
            case CHECKING -> {
                ProgressIndicator spin = new ProgressIndicator();
                spin.getStyleClass().add("update-spinner");
                body.getChildren().addAll(
                        header(spin, "Đang kiểm tra bản cập nhật…", "Đang kết nối tới máy chủ phát hành để so sánh phiên bản."),
                        versions("—"));
            }
            case LATEST -> {
                body.getChildren().addAll(
                        header(icon("fth-check-circle", "success"), "Bạn đang dùng phiên bản mới nhất",
                                "Không có bản cập nhật mới trên máy chủ."),
                        versions(AppInfo.VERSION));
                actions.getChildren().add(primary("Kiểm tra lại", "fth-refresh-cw", this::check));
            }
            case AVAILABLE -> {
                VBox notes = new VBox(4);
                notes.getStyleClass().add("update-notes");
                Label nh = new Label("Ghi chú phát hành");
                nh.getStyleClass().add("dialog-section");
                notes.getChildren().add(nh);
                for (String n : MOCK_NOTES) {
                    Label l = new Label("•  " + n);
                    l.getStyleClass().add("dialog-message");
                    l.setWrapText(true);
                    notes.getChildren().add(l);
                }
                Hyperlink browser = new Hyperlink("Tải qua trình duyệt");
                browser.getStyleClass().add("dialog-link");
                browser.setOnAction(e -> NotificationService.getInstance().showInfo("Kiểm tra cập nhật",
                        "Mô phỏng: sẽ mở trang phát hành trong trình duyệt."));
                body.getChildren().addAll(
                        header(icon("fth-gift", "accent"), "Đã có bản cập nhật mới",
                                "Phiên bản " + MOCK_LATEST + " · " + (int) MOCK_SIZE_MB + " MB"),
                        versions(MOCK_LATEST), notes, browser);
                actions.getChildren().addAll(secondary("Kiểm tra lại", this::check),
                        primary("Cập nhật", "fth-download", this::runDownload));
            }
            case DOWNLOADING -> {
                ProgressBar bar = new ProgressBar(0);
                bar.getStyleClass().add("update-progress");
                bar.setMaxWidth(Double.MAX_VALUE);
                Label amount = new Label();
                amount.getStyleClass().add("dialog-hint");
                body.getChildren().addAll(
                        header(icon("fth-download", "accent"), "Đang tải bản " + MOCK_LATEST + "…",
                                "Có thể tiếp tục làm việc trong lúc tải."),
                        bar, amount);
                actions.getChildren().add(secondary("Hủy tải", () -> {
                    stopTimers();
                    render(State.AVAILABLE);
                }));
                final double[] mb = {0};
                download = new Timeline(new KeyFrame(Duration.millis(100), e -> {
                    mb[0] = Math.min(MOCK_SIZE_MB, mb[0] + 0.45);
                    bar.setProgress(mb[0] / MOCK_SIZE_MB);
                    amount.setText(String.format("%.1f / %.0f MB", mb[0], MOCK_SIZE_MB));
                    if (mb[0] >= MOCK_SIZE_MB) {
                        download.stop();
                        Platform.runLater(() -> render(State.DONE));
                    }
                }));
                download.setCycleCount(Timeline.INDEFINITE);
                download.play();
            }
            case DONE -> {
                body.getChildren().add(header(icon("fth-check-circle", "success"), "Đã tải xong bản " + MOCK_LATEST,
                        "Khởi động lại ứng dụng để áp dụng bản cập nhật."));
                actions.getChildren().addAll(
                        secondary("Mở thư mục", () -> NotificationService.getInstance().showInfo("Kiểm tra cập nhật",
                                "Mô phỏng: sẽ mở thư mục chứa gói cập nhật.")),
                        primary("Khởi động lại", "fth-rotate-cw", () -> {
                            chrome.close();
                            NotificationService.getInstance().showInfo("Kiểm tra cập nhật",
                                    "Mô phỏng: ứng dụng sẽ khởi động lại để cài bản " + MOCK_LATEST + ".");
                        }));
            }
            case ERROR -> {
                body.getChildren().addAll(
                        header(icon("fth-wifi-off", "danger"), "Không thể kiểm tra cập nhật",
                                "Lỗi mạng hoặc không kết nối được tới máy chủ cập nhật."),
                        versions("—"));
                actions.getChildren().add(primary("Kiểm tra lại", "fth-refresh-cw", this::check));
            }
        }
        if (chrome.stage().isShowing()) {
            Platform.runLater(() -> chrome.stage().sizeToScene());
        }
    }

    private void runDownload() {
        startDownload();
    }

    private Node header(Node icon, String title, String message) {
        Label t = new Label(title);
        t.getStyleClass().add("message-header");
        t.setWrapText(true);
        Label m = new Label(message);
        m.getStyleClass().add("dialog-hint");
        m.setWrapText(true);
        VBox text = new VBox(3, t, m);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox h = new HBox(12, icon, text);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private Node versions(String latest) {
        GridPane g = new GridPane();
        g.getStyleClass().add("about-grid");
        g.setHgap(12);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setHalignment(HPos.RIGHT);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c0, c1);
        Label k1 = new Label("Phiên bản hiện tại");
        k1.getStyleClass().add("about-key");
        Label v1 = new Label(AppInfo.VERSION);
        v1.getStyleClass().add("about-value");
        Label k2 = new Label("Phiên bản mới nhất");
        k2.getStyleClass().add("about-key");
        Label v2 = new Label(latest);
        v2.getStyleClass().addAll("about-value", "accent");
        g.addRow(0, k1, v1);
        g.addRow(1, k2, v2);
        return g;
    }

    private static FontIcon icon(String literal, String tone) {
        FontIcon i = new FontIcon(literal);
        i.getStyleClass().addAll("message-icon", tone);
        return i;
    }

    private static Button primary(String text, String icon, Runnable r) {
        Button b = DialogChrome.primary(text);
        if (icon != null) b.setGraphic(new FontIcon(icon));
        b.setOnAction(e -> r.run());
        return b;
    }

    private static Button secondary(String text, Runnable r) {
        Button b = DialogChrome.secondary(text);
        b.setOnAction(e -> r.run());
        return b;
    }
}
