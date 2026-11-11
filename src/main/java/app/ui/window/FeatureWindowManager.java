package app.ui.window;

import app.application.AppContext;
import app.controllers.EditorController;
import app.services.NotificationService;
import app.services.ThemeService;
import app.ui.components.IconButton;
import app.ui.editor.SqlEditorTab;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý chế độ hiển thị Window (Cửa sổ riêng) và Tab cho các tính năng:
 * - So sánh schema (SchemaCompareTab)
 * - Tìm kiếm object (ObjectContentSearchTab)
 * - Thiết kế bảng / New Table (TableDesignerTab)
 * - Cây phụ thuộc (DependencyGraphTab)
 * - SQL Visualizer (SqlVisualizerTab)
 *
 * Hỗ trợ:
 * 1. Mở từ Object Explorer => mở dạng tab, có nút toggle để chuyển thành dạng cửa sổ.
 * 2. Mở từ Menu => mở trực tiếp thành cửa sổ mới, có nút toggle để gắn lại vào tab.
 */
public class FeatureWindowManager {

    private static final FeatureWindowManager INSTANCE = new FeatureWindowManager();

    public static FeatureWindowManager getInstance() {
        return INSTANCE;
    }

    private final Map<String, Stage> openStages = new ConcurrentHashMap<>();
    private final Map<String, SqlEditorTab> windowTabs = new ConcurrentHashMap<>();
    private final Map<String, Node> contentNodes = new ConcurrentHashMap<>();
    private final Map<String, IconButton> toggleButtons = new ConcurrentHashMap<>();
    private final Map<String, javafx.scene.control.TabPane> parentTabPanes = new ConcurrentHashMap<>();

    private FeatureWindowManager() {}

    /**
     * Kiểm tra xem tab có đang mở dưới dạng Window không.
     */
    public boolean isWindowMode(SqlEditorTab tab) {
        if (tab == null || tab.getCustomKey() == null) return false;
        return openStages.containsKey(tab.getCustomKey());
    }

    public boolean isWindowMode(String key) {
        if (key == null) return false;
        return openStages.containsKey(key);
    }

    /**
     * Lấy Stage của tab nếu đang mở dạng Window.
     */
    public Stage getStage(SqlEditorTab tab) {
        if (tab == null || tab.getCustomKey() == null) return null;
        return openStages.get(tab.getCustomKey());
    }

    public Stage getStageByKey(String key) {
        if (key == null) return null;
        return openStages.get(key);
    }

    public SqlEditorTab getTabByKey(String key) {
        if (key == null) return null;
        return windowTabs.get(key);
    }

    /**
     * Chuyển đổi qua lại giữa Tab và Window.
     */
    public void toggleWindow(SqlEditorTab tab, Node contentNode, IconButton toggleBtn, String title, String iconLiteral) {
        if (tab == null) return;
        if (isWindowMode(tab)) {
            dockToTab(tab, contentNode, toggleBtn);
        } else {
            detachToWindow(tab, contentNode, toggleBtn, title, iconLiteral);
        }
    }

    /**
     * Chuyển một tab đang mở thành Cửa sổ riêng biệt.
     */
    public Stage detachToWindow(SqlEditorTab tab, Node contentNode, IconButton toggleBtn, String title, String iconLiteral) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> detachToWindow(tab, contentNode, toggleBtn, title, iconLiteral));
            return null;
        }

        String key = tab.getCustomKey();
        if (key == null) return null;

        // Nếu đã có stage mở cho key này, mang lên trước
        Stage existingStage = openStages.get(key);
        if (existingStage != null && existingStage.isShowing()) {
            existingStage.toFront();
            existingStage.requestFocus();
            return existingStage;
        }

        EditorController ec = getEditorController();
        javafx.scene.control.TabPane targetPane = tab.getTabPane();
        if (targetPane == null && ec != null) {
            targetPane = ec.getPrimaryTabPane();
        }
        if (targetPane != null) {
            parentTabPanes.put(key, targetPane);
            targetPane.getTabs().remove(tab);
        }
        if (ec != null) {
            ec.updateEmptyState();
            ec.refreshTabDecorations();
        }

        // Gỡ content khỏi parent cũ nếu có
        if (contentNode.getParent() instanceof Pane parentPane) {
            parentPane.getChildren().remove(contentNode);
        }

        Stage stage = new Stage();
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        String winTitle = (title != null && !title.isBlank() ? title : tab.getTabTitle()) + " — SQL IDE";
        stage.setTitle(winTitle);
        stage.setWidth(1100);
        stage.setHeight(720);
        stage.setMinWidth(720);
        stage.setMinHeight(460);

        if (AppContext.getInstance().getPrimaryStage() != null) {
            stage.getIcons().addAll(AppContext.getInstance().getPrimaryStage().getIcons());
        }

        // Title Bar với màu sắc đồng bộ 100% theo Theme
        HBox titleBar = buildTitleBar(stage, tab, contentNode, toggleBtn, winTitle, iconLiteral);

        BorderPane windowRoot = new BorderPane();
        windowRoot.getStyleClass().addAll("root", "custom-window-root");
        windowRoot.setStyle("-fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-background-color: -color-bg-default;");
        windowRoot.setTop(titleBar);
        windowRoot.setCenter(contentNode);

        Scene scene = new Scene(windowRoot);
        ThemeService.getInstance().initializeTheme(scene);
        stage.setScene(scene);

        // Hỗ trợ resize cửa sổ không viền ở 4 cạnh và 4 góc
        WindowResizeHandler resizeHandler = new WindowResizeHandler(stage);
        resizeHandler.attachTo(scene);

        // Cập nhật trạng thái nút toggle
        if (toggleBtn != null) {
            toggleBtn.setIcon("fth-layout");
            toggleBtn.setTooltip(new Tooltip("Gắn lại vào tab (Dock to Tab)"));
        }

        openStages.put(key, stage);
        windowTabs.put(key, tab);
        contentNodes.put(key, contentNode);
        if (toggleBtn != null) {
            toggleButtons.put(key, toggleBtn);
        }

        stage.setOnCloseRequest(e -> {
            openStages.remove(key);
            windowTabs.remove(key);
            contentNodes.remove(key);
            toggleButtons.remove(key);

            if (tab.getOnClosed() != null) {
                tab.getOnClosed().handle(null);
            }
            if (ec != null) {
                ec.updateEmptyState();
                ec.refreshTabDecorations();
            }
        });

        stage.show();
        stage.toFront();

        NotificationService.getInstance().showInfo(
                "Cửa sổ độc lập",
                "Đã mở [" + winTitle + "] trong cửa sổ riêng. Bấm 'Gắn lại vào tab' để quay lại."
        );

        return stage;
    }

    /**
     * Gắn cửa sổ trở lại thành Tab trong màn hình chính.
     */
    public void dockToTab(SqlEditorTab tab, Node contentNode, IconButton toggleBtn) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> dockToTab(tab, contentNode, toggleBtn));
            return;
        }

        String key = tab.getCustomKey();
        if (key == null) return;

        Stage stage = openStages.remove(key);
        windowTabs.remove(key);
        contentNodes.remove(key);
        toggleButtons.remove(key);

        if (stage != null) {
            if (stage.getScene() != null) {
                stage.getScene().setRoot(new Region());
            }
            stage.close();
        }

        if (toggleBtn != null) {
            toggleBtn.setIcon("fth-external-link");
            toggleBtn.setTooltip(new Tooltip("Mở dạng cửa sổ riêng (Pop out to window)"));
        }

        tab.setCustomContent(key, contentNode);

        EditorController ec = getEditorController();
        javafx.scene.control.TabPane targetPane = parentTabPanes.remove(key);
        if (targetPane == null && ec != null) {
            targetPane = ec.getPrimaryTabPane();
        }
        if (targetPane != null) {
            if (!targetPane.getTabs().contains(tab)) {
                targetPane.getTabs().add(tab);
            }
            targetPane.getSelectionModel().select(tab);
        }
        if (ec != null) {
            ec.updateEmptyState();
            ec.refreshTabDecorations();
        }

        NotificationService.getInstance().showInfo(
                "Gắn vào Tab",
                "Đã chuyển [" + tab.getTabTitle() + "] trở lại giao diện tab."
        );
    }

    /**
     * Mở tính năng trực tiếp thành Cửa sổ mới (dùng khi kích hoạt từ Menu).
     */
    public Stage openInWindow(SqlEditorTab tab, Node contentNode, IconButton toggleBtn, String title, String iconLiteral) {
        String key = tab.getCustomKey();
        if (key != null) {
            Stage existingStage = openStages.get(key);
            if (existingStage != null && existingStage.isShowing()) {
                existingStage.toFront();
                existingStage.requestFocus();
                return existingStage;
            }

            // Nếu đang mở dạng tab thì chuyển sang window
            EditorController ec = getEditorController();
            if (ec != null && ec.getPrimaryTabPane() != null) {
                for (Tab t : ec.getPrimaryTabPane().getTabs()) {
                    if (t instanceof SqlEditorTab st && key.equals(st.getCustomKey())) {
                        return detachToWindow(st, contentNode, toggleBtn, title, iconLiteral);
                    }
                }
            }
        }

        return detachToWindow(tab, contentNode, toggleBtn, title, iconLiteral);
    }

    /**
     * Nếu tính năng đang mở dạng Cửa sổ, mang cửa sổ ra trước; nếu dạng Tab, chọn tab đó.
     */
    public boolean bringToFrontIfOpen(String key) {
        if (key == null) return false;
        Stage stage = openStages.get(key);
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            stage.requestFocus();
            return true;
        }

        EditorController ec = getEditorController();
        if (ec != null && ec.getPrimaryTabPane() != null) {
            for (Tab t : ec.getPrimaryTabPane().getTabs()) {
                if (t instanceof SqlEditorTab st && key.equals(st.getCustomKey())) {
                    ec.getPrimaryTabPane().getSelectionModel().select(t);
                    return true;
                }
            }
        }
        return false;
    }

    private EditorController getEditorController() {
        if (AppContext.getInstance().getMainController() != null) {
            return AppContext.getInstance().getMainController().getEditorController();
        }
        return null;
    }

    private HBox buildTitleBar(Stage stage, SqlEditorTab tab, Node contentNode, IconButton toggleBtn, String winTitle, String iconLiteral) {
        HBox titleBar = new HBox(8);
        titleBar.getStyleClass().add("window-title-bar");
        titleBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        titleBar.setPrefHeight(34);
        titleBar.setMinHeight(34);
        titleBar.setMaxHeight(34);
        titleBar.setStyle("-fx-background-color: -ide-titlebar-bg; -fx-padding: 0 0 0 12; -fx-border-color: transparent transparent -color-border-subtle transparent; -fx-border-width: 0 0 1 0;");

        // Inactive window tracking (dims/tones titlebar when window loses focus)
        stage.focusedProperty().addListener((obs, was, focused) -> {
            titleBar.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("window-inactive"), !focused);
        });

        // Icon & Title
        org.kordamp.ikonli.javafx.FontIcon titleIcon = new org.kordamp.ikonli.javafx.FontIcon(iconLiteral != null ? iconLiteral : "fth-square");
        titleIcon.getStyleClass().add("titlebar-icon");
        titleIcon.setIconSize(14);
        titleIcon.setStyle("-fx-icon-color: -color-accent-fg;");

        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label(winTitle);
        titleLabel.getStyleClass().add("window-title-label");
        titleLabel.setStyle("-fx-text-fill: -ide-titlebar-fg; -fx-font-weight: bold; -fx-font-size: 12px;");

        HBox leftArea = new HBox(8, titleIcon, titleLabel);
        leftArea.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Region dragSpacer = new Region();
        HBox.setHgrow(dragSpacer, javafx.scene.layout.Priority.ALWAYS);

        // Nút dock lại vào tab trên thanh tiêu đề
        javafx.scene.control.Button btnDock = new javafx.scene.control.Button("Gắn vào Tab");
        btnDock.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fth-layout"));
        btnDock.getStyleClass().addAll("button", "small");
        btnDock.setTooltip(new Tooltip("Gắn cửa sổ này trở lại thanh tab trong cửa sổ chính"));
        btnDock.setOnAction(e -> dockToTab(tab, contentNode, toggleBtn));

        // Window controls (Minimize, Maximize / Restore, Close)
        javafx.scene.control.Button btnMin = new javafx.scene.control.Button();
        btnMin.getStyleClass().addAll("window-control-button", "minimize");
        btnMin.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fth-minus"));
        btnMin.setFocusTraversable(false);
        btnMin.setMaxHeight(Double.MAX_VALUE);
        btnMin.setOnAction(e -> stage.setIconified(true));

        javafx.scene.control.Button btnMax = new javafx.scene.control.Button();
        btnMax.getStyleClass().addAll("window-control-button", "maximize");
        org.kordamp.ikonli.javafx.FontIcon maxIcon = new org.kordamp.ikonli.javafx.FontIcon("fth-square");
        btnMax.setGraphic(maxIcon);
        btnMax.setFocusTraversable(false);
        btnMax.setMaxHeight(Double.MAX_VALUE);
        btnMax.setOnAction(e -> {
            boolean max = !stage.isMaximized();
            stage.setMaximized(max);
        });

        stage.maximizedProperty().addListener((obs, was, isMax) -> {
            maxIcon.setIconLiteral(isMax ? "fth-copy" : "fth-square");
        });

        javafx.scene.control.Button btnClose = new javafx.scene.control.Button();
        btnClose.getStyleClass().addAll("window-control-button", "close");
        btnClose.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fth-x"));
        btnClose.setFocusTraversable(false);
        btnClose.setMaxHeight(Double.MAX_VALUE);
        btnClose.setOnAction(e -> stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));

        HBox controls = new HBox(0, btnMin, btnMax, btnClose);
        controls.getStyleClass().add("window-controls");
        controls.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        controls.setFillHeight(true);

        titleBar.getChildren().addAll(leftArea, dragSpacer, btnDock, controls);

        // Drag window by title bar
        final double[] dragDelta = new double[2];
        titleBar.setOnMousePressed(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && !isInteractiveControl(e.getTarget())) {
                dragDelta[0] = stage.getX() - e.getScreenX();
                dragDelta[1] = stage.getY() - e.getScreenY();
            }
        });
        titleBar.setOnMouseDragged(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && !stage.isMaximized() && !isInteractiveControl(e.getTarget())) {
                stage.setX(e.getScreenX() + dragDelta[0]);
                stage.setY(e.getScreenY() + dragDelta[1]);
            }
        });
        titleBar.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY && !isInteractiveControl(e.getTarget())) {
                stage.setMaximized(!stage.isMaximized());
            }
        });

        return titleBar;
    }

    private boolean isInteractiveControl(Object target) {
        if (target instanceof Node node) {
            Node curr = node;
            while (curr != null) {
                if (curr instanceof javafx.scene.control.ButtonBase) {
                    return true;
                }
                curr = curr.getParent();
            }
        }
        return false;
    }
}
