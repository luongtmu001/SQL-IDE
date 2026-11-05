package app.controllers;

import app.services.ConnectionColorService;
import app.services.ConnectionColorService.ConnectionColor;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * SCR-MAIN-005 — VS Code-style status bar (approved 2026-11-05).
 *
 * <p>Left: connection (dot + name, coloured bar), PID, problems, READ ONLY / TX OPEN flags,
 * last run / transient message. Right: cursor, indentation, encoding, EOL, language, zoom,
 * notifications. The bar background is the colour chosen for the active connection
 * (default blue). Prototype: all values come from UI state / mock data, never from a DB call.</p>
 */
public class StatusBarController implements Initializable {

    /** UI metadata of a connection, resolved by the main controller from its profile list. */
    public record ConnectionMeta(String key, boolean readOnly, String environment) {
    }

    public static final List<Integer> ZOOM_LEVELS = List.of(50, 75, 90, 100, 110, 125, 150, 200);

    @FXML private HBox statusBarRoot;
    @FXML private HBox connectionItem;
    @FXML private Region statusDot;
    @FXML private Label connectionLabel;
    @FXML private Label pidLabel;
    @FXML private HBox problemsItem;
    @FXML private Label errorsLabel;
    @FXML private Label warningsLabel;
    @FXML private Label readOnlyLabel;
    @FXML private Label transactionLabel;
    @FXML private HBox lastRunItem;
    @FXML private FontIcon lastRunIcon;
    @FXML private Label statusLabel;
    @FXML private Label cursorPositionLabel;
    @FXML private Label indentLabel;
    @FXML private Label encodingLabel;
    @FXML private Label lineSeparatorLabel;
    @FXML private Label languageModeLabel;
    @FXML private Label zoomLabel;
    @FXML private Button notificationButton;
    @FXML private FontIcon notificationIcon;
    @FXML private Tooltip notificationsTooltip;

    private final Tooltip connectionTooltip = new Tooltip();
    private final Tooltip problemsTooltip = new Tooltip("Không có lỗi cú pháp");
    private final PauseTransition messageTimer = new PauseTransition(Duration.seconds(5));

    private String connectionName;
    private ConnectionMeta connectionMeta;
    private boolean connected;
    private String tabInfo = "";
    private int zoomPercent = 100;

    private String lastRunText = "Sẵn sàng";
    private String lastRunState = "idle";

    private Function<String, ConnectionMeta> connectionMetaResolver = name -> null;
    private Runnable onManageConnections;
    private Runnable onShowProblems;
    private Runnable onShowResults;
    private Runnable onGoToLine;
    private Runnable onOpenSettings;
    private IntConsumer onZoomChanged;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Tooltip.install(connectionItem, connectionTooltip);
        Tooltip.install(problemsItem, problemsTooltip);

        connectionItem.setOnMouseClicked(e -> showConnectionMenu());
        problemsItem.setOnMouseClicked(e -> run(onShowProblems));
        lastRunItem.setOnMouseClicked(e -> run(onShowResults));
        cursorPositionLabel.setOnMouseClicked(e -> run(onGoToLine));
        indentLabel.setOnMouseClicked(e -> run(onOpenSettings));
        zoomLabel.setOnMouseClicked(e -> showZoomMenu());
        pidLabel.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && pidLabel.getUserData() != null) {
                ClipboardContent c = new ClipboardContent();
                c.putString(String.valueOf(pidLabel.getUserData()));
                Clipboard.getSystemClipboard().setContent(c);
                flash("Đã sao chép PID " + pidLabel.getUserData());
            }
        });

        messageTimer.setOnFinished(e -> renderLastRun());
        ConnectionColorService.getInstance().revisionProperty().addListener((o, a, b) -> applyColor());

        setConnection(null);
        setCursorPosition(1, 1);
        setZoom(100);
        renderLastRun();
    }

    // ---------------------------------------------------------------- wiring

    public void setConnectionMetaResolver(Function<String, ConnectionMeta> resolver) {
        this.connectionMetaResolver = resolver != null ? resolver : name -> null;
        setConnection(connectionName);
    }

    public void setOnManageConnections(Runnable r) { this.onManageConnections = r; }
    public void setOnShowProblems(Runnable r) { this.onShowProblems = r; }
    public void setOnShowResults(Runnable r) { this.onShowResults = r; }
    public void setOnGoToLine(Runnable r) { this.onGoToLine = r; }
    public void setOnOpenSettings(Runnable r) { this.onOpenSettings = r; }
    public void setOnZoomChanged(IntConsumer c) { this.onZoomChanged = c; }

    // ---------------------------------------------------------------- connection

    public void setConnection(String name) {
        connectionName = (name == null || name.isBlank()) ? null : name;
        connectionMeta = connectionName != null ? connectionMetaResolver.apply(connectionName) : null;
        connectionLabel.setText(connectionName != null ? connectionName : "Chưa kết nối");
        boolean ro = connectionMeta != null && connectionMeta.readOnly();
        show(readOnlyLabel, ro);
        updateConnectionTooltip();
        applyColor();
        applyDot();
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        applyDot();
        updateConnectionTooltip();
    }

    public void setPid(String pid) {
        boolean has = pid != null && !pid.isBlank();
        pidLabel.setUserData(has ? pid : null);
        pidLabel.setText(has ? "PID " + pid : "");
        show(pidLabel, has);
    }

    public void setTransactionOpen(boolean open) {
        show(transactionLabel, open);
    }

    public void setProblems(int errors, int warnings) {
        errorsLabel.setText(String.valueOf(Math.max(0, errors)));
        warningsLabel.setText(String.valueOf(Math.max(0, warnings)));
        problemsTooltip.setText(errors == 0 && warnings == 0
                ? "Không có lỗi cú pháp"
                : errors + " lỗi, " + warnings + " cảnh báo — bấm để xem");
    }

    // ---------------------------------------------------------------- run status

    /** Persistent summary of the last execution (state: online | busy | offline | warning | idle). */
    public void setLastRun(String text, String state) {
        lastRunText = text != null ? text : "";
        lastRunState = state != null ? state : "idle";
        messageTimer.stop();
        renderLastRun();
        applyDot();
    }

    /**
     * Legacy API used across the app for short notices. Query lifecycle messages
     * (busy / error) stay; plain notices are shown for 5 s then the last-run summary returns.
     */
    public void setStatus(String message, String state) {
        if ("busy".equals(state)) {
            setLastRun(message, state);
            return;
        }
        showMessage(message, state);
    }

    public void flash(String message) {
        showMessage(message, "online");
    }

    private void showMessage(String message, String state) {
        statusLabel.setText(message != null ? message : "");
        lastRunIcon.setIconLiteral(iconFor(state));
        messageTimer.playFromStart();
    }

    private void renderLastRun() {
        statusLabel.setText(lastRunText);
        lastRunIcon.setIconLiteral(iconFor(lastRunState));
    }

    private static String iconFor(String state) {
        return switch (state == null ? "idle" : state) {
            case "busy" -> "fth-loader";
            case "offline", "error" -> "fth-x-circle";
            case "warning" -> "fth-alert-triangle";
            case "online", "success" -> "fth-check";
            default -> "fth-check";
        };
    }

    // ---------------------------------------------------------------- editor info

    public void setTabInfo(String tabName) {
        this.tabInfo = tabName != null ? tabName : "";
        updateConnectionTooltip();
    }

    public void setCursorPosition(int line, int col) {
        cursorPositionLabel.setText(String.format("Ln %d, Col %d", line, col));
    }

    public void setEditorInfoVisible(boolean visible) {
        for (Node n : List.of(cursorPositionLabel, indentLabel, encodingLabel, lineSeparatorLabel, languageModeLabel, zoomLabel)) {
            show(n, visible);
        }
    }

    public void setIndentation(boolean insertSpaces, int size) {
        indentLabel.setText((insertSpaces ? "Spaces: " : "Tab Size: ") + Math.max(1, size));
    }

    public void setEncoding(String encoding) {
        encodingLabel.setText(encoding);
    }

    public void setLineSeparator(String separator) {
        lineSeparatorLabel.setText(separator);
    }

    public void setLanguageMode(String language) {
        languageModeLabel.setText(language != null && !language.isBlank() ? language : "SQL");
    }

    public Label getLanguageModeLabel() {
        return languageModeLabel;
    }

    public int getZoom() {
        return zoomPercent;
    }

    public void setZoom(int percent) {
        zoomPercent = Math.max(ZOOM_LEVELS.get(0), Math.min(ZOOM_LEVELS.get(ZOOM_LEVELS.size() - 1), percent));
        zoomLabel.setText(zoomPercent + "%");
    }

    /** Step to the next/previous preset zoom level (Ctrl+= / Ctrl+-). */
    public void stepZoom(int direction) {
        int idx = ZOOM_LEVELS.indexOf(zoomPercent);
        if (idx < 0) idx = ZOOM_LEVELS.indexOf(100);
        int next = Math.max(0, Math.min(ZOOM_LEVELS.size() - 1, idx + Integer.signum(direction)));
        changeZoom(ZOOM_LEVELS.get(next));
    }

    public void changeZoom(int percent) {
        setZoom(percent);
        if (onZoomChanged != null) onZoomChanged.accept(zoomPercent);
    }

    public void setNotificationCount(int count) {
        notificationButton.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("unread"), count > 0);
        notificationsTooltip.setText(count > 0 ? count + " thông báo mới" : "Không có thông báo mới");
    }

    // ---------------------------------------------------------------- menus

    private void showZoomMenu() {
        ContextMenu menu = new ContextMenu();
        ToggleGroup group = new ToggleGroup();
        for (int i = ZOOM_LEVELS.size() - 1; i >= 0; i--) {
            int level = ZOOM_LEVELS.get(i);
            RadioMenuItem item = new RadioMenuItem(level + "%");
            item.setToggleGroup(group);
            item.setSelected(level == zoomPercent);
            item.setOnAction(e -> changeZoom(level));
            menu.getItems().add(item);
        }
        MenuItem reset = new MenuItem("Đặt lại (100%)");
        reset.setOnAction(e -> changeZoom(100));
        menu.getItems().addAll(new SeparatorMenuItem(), reset);
        menu.show(zoomLabel, Side.TOP, 0, 0);
    }

    private void showConnectionMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem manage = new MenuItem("Quản lý kết nối...");
        manage.setGraphic(new FontIcon("fth-database"));
        manage.setOnAction(e -> run(onManageConnections));
        menu.getItems().add(manage);

        String key = colorKey();
        if (key != null) {
            Menu colors = new Menu("Màu thanh trạng thái");
            colors.setGraphic(new FontIcon("fth-droplet"));
            ToggleGroup group = new ToggleGroup();
            ConnectionColor current = ConnectionColorService.getInstance().colorFor(key);
            for (ConnectionColor c : ConnectionColorService.PALETTE) {
                RadioMenuItem item = new RadioMenuItem(c.label());
                item.setGraphic(new Circle(5, Color.web(c.hex())));
                item.setToggleGroup(group);
                item.setSelected(c.equals(current));
                item.setOnAction(e -> ConnectionColorService.getInstance().setColor(key, c));
                colors.getItems().add(item);
            }
            menu.getItems().addAll(new SeparatorMenuItem(), colors);
        }
        menu.show(connectionItem, Side.TOP, 0, 0);
    }

    // ---------------------------------------------------------------- rendering

    private String colorKey() {
        if (connectionName == null) return null;
        return connectionMeta != null && connectionMeta.key() != null ? connectionMeta.key() : connectionName;
    }

    private void applyColor() {
        if (statusBarRoot == null) return;
        ConnectionColor c = ConnectionColorService.getInstance().colorFor(colorKey());
        statusBarRoot.setStyle("-status-bar-bg: " + c.hex() + ";");
    }

    private void applyDot() {
        if (statusDot == null) return;
        statusDot.getStyleClass().removeAll("online", "offline", "busy");
        String cls;
        if ("busy".equals(lastRunState)) cls = "busy";
        else cls = connected ? "online" : "offline";
        statusDot.getStyleClass().add(cls);
    }

    private void updateConnectionTooltip() {
        StringBuilder sb = new StringBuilder();
        if (connectionName == null) {
            sb.append("Tab chưa chọn kết nối");
        } else {
            sb.append(connectionName).append(connected ? " — đã kết nối" : " — chưa kết nối");
            if (connectionMeta != null && connectionMeta.environment() != null && !connectionMeta.environment().isBlank()) {
                sb.append("\nMôi trường: ").append(connectionMeta.environment());
            }
        }
        if (!tabInfo.isBlank()) sb.append("\nTab: ").append(tabInfo);
        sb.append("\nBấm để quản lý kết nối hoặc đổi màu thanh trạng thái");
        connectionTooltip.setText(sb.toString());
    }

    private static void show(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private static void run(Runnable r) {
        if (r != null) Platform.runLater(r);
    }
}
