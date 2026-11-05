package app.ui.titlebar;

import app.ui.window.WindowStateManager;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class TitleBarController implements Initializable {

    public static final String INTERACTIVE_STYLE_CLASS = "titlebar-interactive";

    private static final PseudoClass NC_HOVER = PseudoClass.getPseudoClass("nc-hover");
    private static final PseudoClass NC_PRESSED = PseudoClass.getPseudoClass("nc-pressed");
    /** Set on the title bar root while the window is not focused (VS Code: titleBar.inactive*). */
    public static final PseudoClass WINDOW_INACTIVE = PseudoClass.getPseudoClass("window-inactive");

    /** Lower bound kept for compatibility: never show inline menus below this width. */
    private static final double MIN_INLINE_MENU_WIDTH = 950;
    /** Minimum empty (draggable) caption space that must remain on each side of the title. */
    private static final double MIN_CAPTION_GAP = 24;
    /** Hysteresis to avoid flickering between inline and hamburger mode. */
    private static final double MENU_HYSTERESIS = 16;
    private static final String LOGO_RESOURCE = "/static/icons/app-logo-32.png";

    @FXML private HBox titleBarRoot;
    @FXML private ImageView appIcon;
    @FXML private MenuButton hamburgerMenuButton;
    @FXML private Label windowTitleLabel;
    @FXML private HBox menuArea;
    @FXML private HBox searchArea;
    @FXML private HBox customArea;
    @FXML private HBox windowControls;
    @FXML private HBox leftArea;
    @FXML private HBox centerArea;
    @FXML private HBox statusArea;
    @FXML private Label environmentBadge;
    @FXML private Button updateButton;

    @FXML private Button minimizeButton;
    @FXML private Button maxRestoreButton;
    @FXML private FontIcon maxRestoreIcon;
    @FXML private Button closeButton;

    private WindowStateManager stateManager;
    private MenuBar storedMenuBar;
    private final java.util.List<javafx.scene.control.Menu> originalMenus = new java.util.ArrayList<>();
    private boolean isCompactMenu = false;
    private double expandedMenuWidth = -1;
    private Runnable onUpdateRequested;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        bindToModel(WindowTitleModel.getInstance());
        loadLogo();
        setupInactiveTracking();
        setEnvironment(null);
        setUpdateAvailable(null);
    }

    private void loadLogo() {
        if (appIcon == null) return;
        try {
            var url = getClass().getResource(LOGO_RESOURCE);
            if (url != null) appIcon.setImage(new Image(url.toExternalForm(), 32, 32, true, true));
        } catch (Exception ignored) {
            // Logo is cosmetic.
        }
    }

    /** Dims the title bar while the window is inactive, like native captions. */
    private void setupInactiveTracking() {
        if (titleBarRoot == null) return;
        javafx.beans.value.ChangeListener<Boolean> focusListener =
            (obs, was, focused) -> titleBarRoot.pseudoClassStateChanged(WINDOW_INACTIVE, !focused);
        javafx.beans.value.ChangeListener<javafx.stage.Window> windowListener = (o2, oldWin, win) -> {
            if (oldWin != null) oldWin.focusedProperty().removeListener(focusListener);
            if (win != null) {
                win.focusedProperty().addListener(focusListener);
                titleBarRoot.pseudoClassStateChanged(WINDOW_INACTIVE, !win.isFocused());
            }
        };
        titleBarRoot.sceneProperty().addListener((o, oldScene, scene) -> {
            if (oldScene != null) oldScene.windowProperty().removeListener(windowListener);
            if (scene == null) return;
            scene.windowProperty().addListener(windowListener);
            if (scene.getWindow() != null) windowListener.changed(scene.windowProperty(), null, scene.getWindow());
        });
    }

    // ------------------------------------------------------------------ status slots

    /**
     * Shows the environment badge of the active connection (DEV / TEST / STAGING / UAT / PROD).
     * Hidden when {@code env} is null or blank.
     */
    public void setEnvironment(String env) {
        applyEnvironmentBadge(environmentBadge, env);
    }

    /**
     * Shared styling for environment badges (title bar and editor context bar).
     * Hides the label when {@code env} is null or blank.
     */
    public static void applyEnvironmentBadge(Label badge, String env) {
        if (badge == null) return;
        String value = env == null ? "" : env.trim().toUpperCase();
        badge.getStyleClass().removeIf(c -> c.startsWith("env-"));
        boolean show = !value.isEmpty();
        badge.setVisible(show);
        badge.setManaged(show);
        if (!show) return;
        badge.setText(value);
        String variant = switch (value) {
            case "PROD", "PRODUCTION", "LIVE" -> "env-prod";
            case "STAGING", "UAT", "PRE-PROD", "PREPROD" -> "env-staging";
            case "TEST", "QA" -> "env-test";
            default -> "env-dev";
        };
        badge.getStyleClass().add(variant);
    }

    /** Shows the "update available" button with the given version, or hides it when null. */
    public void setUpdateAvailable(String version) {
        if (updateButton == null) return;
        boolean show = version != null && !version.isBlank();
        updateButton.setVisible(show);
        updateButton.setManaged(show);
        if (show) {
            updateButton.setText("Cập nhật " + version);
        }
    }

    public void setOnUpdateRequested(Runnable handler) {
        this.onUpdateRequested = handler;
    }

    @FXML
    private void handleUpdateRequested() {
        if (onUpdateRequested != null) onUpdateRequested.run();
    }

    public void bindToModel(WindowTitleModel model) {
        if (model == null || windowTitleLabel == null) return;

        windowTitleLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            String doc = model.getDocumentTitle();
            String app = model.getAppName();
            return (doc != null && !doc.isBlank()) ? doc + " — " + app : app;
        }, model.documentTitleProperty(), model.appNameProperty()));
    }

    public void bindToStateManager(WindowStateManager stateManager) {
        this.stateManager = stateManager;
        if (stateManager != null) {
            stateManager.maximizedProperty().addListener((obs, oldVal, newVal) -> updateMaxRestoreIcon(newVal));
            updateMaxRestoreIcon(stateManager.isMaximized());
        }
    }

    private void updateMaxRestoreIcon(boolean isMaximized) {
        if (maxRestoreIcon != null) {
            maxRestoreIcon.setIconLiteral(isMaximized ? "fth-copy" : "fth-square");
        }
    }

    // ------------------------------------------------------------------ content slots

    /** Embeds the application MenuBar into the title bar. */
    public void setMenuBar(MenuBar menuBar) {
        if (menuBar == null || menuArea == null) return;
        this.storedMenuBar = menuBar;
        this.originalMenus.clear();
        this.originalMenus.addAll(menuBar.getMenus());

        menuBar.getStyleClass().add("titlebar-menu-bar");
        menuBar.setUseSystemMenuBar(false);
        menuArea.getChildren().setAll(menuBar);

        setupResponsiveMenuListener();
    }

    private void setupResponsiveMenuListener() {
        if (titleBarRoot == null) return;
        // Menu titles must never be ellipsized: give every menu button its preferred width.
        storedMenuBar.setMinWidth(Region.USE_PREF_SIZE);
        storedMenuBar.skinProperty().addListener((obs, o, n) -> lockMenuButtonWidths());
        lockMenuButtonWidths();

        titleBarRoot.widthProperty().addListener((obs, oldW, newW) -> checkMenuResponsiveness(newW.doubleValue()));
        if (centerArea != null) centerArea.widthProperty().addListener((obs, o, n) -> recheckLater());
        if (customArea != null) customArea.widthProperty().addListener((obs, o, n) -> recheckLater());
        recheckLater();
    }

    private void lockMenuButtonWidths() {
        if (storedMenuBar == null) return;
        javafx.application.Platform.runLater(() -> {
            for (Node n : storedMenuBar.lookupAll(".menu-button")) {
                if (n instanceof Region r) r.setMinWidth(Region.USE_PREF_SIZE);
            }
        });
    }

    private void recheckLater() {
        javafx.application.Platform.runLater(() -> {
            if (titleBarRoot != null) checkMenuResponsiveness(titleBarRoot.getWidth());
        });
    }

    /**
     * Switches between inline menus and the hamburger button. The switch happens when the inline
     * menus plus the other title bar areas (and a minimum draggable caption gap) no longer fit,
     * so menu titles are never truncated. A lower bound of 950 px is kept for compatibility.
     */
    public void checkMenuResponsiveness(double titleBarWidth) {
        if (storedMenuBar == null || originalMenus.isEmpty()) return;
        if (titleBarWidth <= 0) return;

        if (!isCompactMenu) {
            double measured = storedMenuBar.prefWidth(-1);
            if (measured > 0) expandedMenuWidth = Math.max(expandedMenuWidth, measured);
        }
        double required = MIN_INLINE_MENU_WIDTH;
        if (expandedMenuWidth > 0) {
            double others = prefWidthOf(appIcon) + prefWidthOf(centerArea) + prefWidthOf(customArea)
                + prefWidthOf(statusArea) + prefWidthOf(windowControls)
                + titleBarRoot.getPadding().getLeft() + titleBarRoot.getPadding().getRight()
                + titleBarRoot.getSpacing() * 6 + 2 * MIN_CAPTION_GAP;
            required = Math.max(required, expandedMenuWidth + others);
        }
        if (isCompactMenu) required += MENU_HYSTERESIS;

        boolean shouldBeCompact = titleBarWidth < required;
        if (shouldBeCompact == isCompactMenu) return;
        isCompactMenu = shouldBeCompact;

        if (isCompactMenu) {
            storedMenuBar.getMenus().clear();
            if (hamburgerMenuButton != null) {
                hamburgerMenuButton.getItems().setAll(originalMenus);
                hamburgerMenuButton.setVisible(true);
                hamburgerMenuButton.setManaged(true);
            }
            menuArea.setVisible(false);
            menuArea.setManaged(false);
        } else {
            if (hamburgerMenuButton != null) {
                hamburgerMenuButton.getItems().clear();
                hamburgerMenuButton.setVisible(false);
                hamburgerMenuButton.setManaged(false);
            }
            storedMenuBar.getMenus().setAll(originalMenus);
            menuArea.setVisible(true);
            menuArea.setManaged(true);
            lockMenuButtonWidths();
        }
    }

    private static double prefWidthOf(Node n) {
        if (n == null || !n.isVisible() || !n.isManaged()) return 0;
        return Math.max(0, n.prefWidth(-1));
    }

    public boolean isCompactMenu() {
        return isCompactMenu;
    }

    /**
     * Shows or hides the inline menu bar (VS Code "menuBarVisibility: toggle").
     * Ignored while the hamburger menu is active.
     */
    public void setMenuBarHidden(boolean hidden) {
        if (menuArea == null || isCompactMenu) return;
        menuArea.setVisible(!hidden);
        menuArea.setManaged(!hidden);
    }

    public boolean isMenuBarHidden() {
        return menuArea != null && !menuArea.isVisible();
    }

    /** Embeds the search box into the center of the title bar. */
    public void setSearchBox(Node node) {
        if (node == null || searchArea == null) return;
        searchArea.getChildren().setAll(node);
    }

    /** Embeds layout toggles into the right of the title bar. */
    public void setCustomActions(Node node) {
        if (node == null || customArea == null) return;
        customArea.getChildren().setAll(node);
    }

    /** Backwards-compatible alias for setCustomActions. */
    public void addCustomNode(Node node) {
        if (node == null || customArea == null) return;
        customArea.getChildren().add(node);
    }

    // ------------------------------------------------------------------ native hit testing

    /**
     * @return true when the scene point is on an empty part of the title bar
     *         (used by Windows as caption: drag, Aero Snap, double-click maximize).
     */
    public boolean isCaptionArea(double sceneX, double sceneY) {
        if (titleBarRoot == null || titleBarRoot.getScene() == null) return false;
        Bounds b = titleBarRoot.localToScene(titleBarRoot.getLayoutBounds());
        if (b == null || !b.contains(sceneX, sceneY)) return false;
        return !hitsInteractive(titleBarRoot, sceneX, sceneY);
    }

    /** @return true when the scene point is on the maximize / restore button. */
    public boolean isMaximizeButtonArea(double sceneX, double sceneY) {
        if (maxRestoreButton == null || !maxRestoreButton.isVisible() || maxRestoreButton.getScene() == null) {
            return false;
        }
        Bounds b = maxRestoreButton.localToScene(maxRestoreButton.getLayoutBounds());
        return b != null && b.contains(sceneX, sceneY);
    }

    private boolean hitsInteractive(Node node, double sceneX, double sceneY) {
        if (!node.isVisible() || node.isMouseTransparent()) return false;
        Point2D local = node.sceneToLocal(sceneX, sceneY);
        if (local == null || !node.getLayoutBounds().contains(local)) return false;

        if (isInteractive(node)) return true;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (hitsInteractive(child, sceneX, sceneY)) return true;
            }
        }
        return false;
    }

    private boolean isInteractive(Node node) {
        return node instanceof ButtonBase
            || node instanceof TextInputControl
            || node instanceof ComboBoxBase<?>
            || node.getStyleClass().contains(INTERACTIVE_STYLE_CLASS)
            || node.getStyleClass().contains("titlebar-search-box")
            || node.getStyleClass().contains("quick-search-box");
    }

    public void setMaximizeButtonNativeHover(boolean hover) {
        if (maxRestoreButton != null) maxRestoreButton.pseudoClassStateChanged(NC_HOVER, hover);
    }

    public void setMaximizeButtonNativePressed(boolean pressed) {
        if (maxRestoreButton != null) maxRestoreButton.pseudoClassStateChanged(NC_PRESSED, pressed);
    }

    // ------------------------------------------------------------------ actions

    @FXML
    private void handleMinimize() {
        if (stateManager != null) {
            stateManager.minimize();
        }
    }

    @FXML
    public void handleMaximizeRestore() {
        if (stateManager != null) {
            stateManager.maximizeRestore();
        }
    }

    @FXML
    public void handleClose() {
        if (stateManager != null) {
            stateManager.close();
        }
    }

    public void close() {
        handleClose();
    }

    public Button getMinimizeButton() {
        return minimizeButton;
    }

    public Button getMaxRestoreButton() {
        return maxRestoreButton;
    }

    public Button getCloseButton() {
        return closeButton;
    }

    public HBox getTitleBarRoot() {
        return titleBarRoot;
    }

    public HBox getCustomArea() {
        return customArea;
    }

    public HBox getMenuArea() {
        return menuArea;
    }

    public HBox getSearchArea() {
        return searchArea;
    }

    public MenuButton getHamburgerMenuButton() {
        return hamburgerMenuButton;
    }

    public Label getEnvironmentBadge() {
        return environmentBadge;
    }

    public Button getUpdateButton() {
        return updateButton;
    }
}
