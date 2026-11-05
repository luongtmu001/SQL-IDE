package app.services;

import atlantafx.base.theme.*;
import javafx.application.Application;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;

import java.util.Objects;
import java.util.function.Consumer;

public class ThemeService {

    public enum AppTheme {
        PRIMER_DARK("Primer Dark", true),
        PRIMER_LIGHT("Primer Light", false),
        DRACULA("Dracula", true),
        NORD_DARK("Nord Dark", true),
        NORD_LIGHT("Nord Light", false),
        CUPERTINO_DARK("Cupertino Dark", true),
        CUPERTINO_LIGHT("Cupertino Light", false);

        private final String displayName;
        private final boolean isDark;

        AppTheme(String displayName, boolean isDark) {
            this.displayName = displayName;
            this.isDark = isDark;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isDark() {
            return isDark;
        }

        public static AppTheme fromDisplayName(String displayName) {
            if (displayName == null) return PRIMER_DARK;
            for (AppTheme t : values()) {
                if (t.getDisplayName().equalsIgnoreCase(displayName) || t.name().equalsIgnoreCase(displayName)) {
                    return t;
                }
            }
            return PRIMER_DARK;
        }
    }

    private static final ThemeService INSTANCE = new ThemeService();
    private final ObjectProperty<AppTheme> currentTheme = new SimpleObjectProperty<>(AppTheme.PRIMER_DARK);

    private ThemeService() {
    }

    public static ThemeService getInstance() {
        return INSTANCE;
    }

    public ObjectProperty<AppTheme> currentThemeProperty() {
        return currentTheme;
    }

    public AppTheme getCurrentTheme() {
        return currentTheme.get();
    }

    public boolean isDarkMode() {
        return currentTheme.get().isDark();
    }

    public void initializeTheme(Scene scene) {
        applyTheme(currentTheme.get(), scene);
    }

    public void toggleDarkLight(Scene scene) {
        if (isDarkMode()) {
            setTheme(AppTheme.PRIMER_LIGHT, scene);
        } else {
            setTheme(AppTheme.PRIMER_DARK, scene);
        }
    }

    public void setTheme(AppTheme theme, Scene scene) {
        this.currentTheme.set(theme);
        applyTheme(theme, scene);
    }

    private void applyTheme(AppTheme theme, Scene scene) {
        Theme atlantafxTheme = switch (theme) {
            case PRIMER_DARK -> new PrimerDark();
            case PRIMER_LIGHT -> new PrimerLight();
            case DRACULA -> new Dracula();
            case NORD_DARK -> new NordDark();
            case NORD_LIGHT -> new NordLight();
            case CUPERTINO_DARK -> new CupertinoDark();
            case CUPERTINO_LIGHT -> new CupertinoLight();
        };

        Application.setUserAgentStylesheet(atlantafxTheme.getUserAgentStylesheet());

        if (scene != null) {
            scene.getStylesheets().clear();
            addStylesheet(scene, "/css/base.css");
            addStylesheet(scene, "/css/layout.css");
            addStylesheet(scene, "/css/components.css");
            addStylesheet(scene, "/css/editor.css");
            addStylesheet(scene, "/css/titlebar/titlebar.css");
            addStylesheet(scene, "/css/titlebar/window-controls.css");

            if (theme.isDark()) {
                addStylesheet(scene, "/css/dark-theme.css");
            } else {
                addStylesheet(scene, "/css/light-theme.css");
            }
            applyShape(scene);
        }
    }

    // ------------------------------------------------------------------ R-UI-01

    private static final String SHAPE_CSS = "/css/shape.css";
    private static final String SHAPE_FLAT_CSS = "/css/shape-flat.css";

    /** R-UI-01: the UI always follows {@code appearance.panelStyle} (rounded / flat). */
    public static boolean isFlat() {
        try {
            var settings = SettingsService.getInstance().getCurrentSettings();
            return settings != null && settings.isFlatPanels();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Adds the shared shape stylesheets last: shape.css always, shape-flat.css only in flat mode.
     * Dialog-specific stylesheets added after this call must not hard-code corner radii.
     */
    public void applyShape(Scene scene) {
        if (scene == null) return;
        String shape = url(SHAPE_CSS);
        String flat = url(SHAPE_FLAT_CSS);
        if (shape != null) scene.getStylesheets().remove(shape);
        if (flat != null) scene.getStylesheets().remove(flat);
        if (shape != null) scene.getStylesheets().add(shape);
        if (flat != null && isFlat()) scene.getStylesheets().add(flat);
    }

    /** Re-applies R-UI-01 to every open window (called when the panel style setting changes). */
    public void applyShapeToAllWindows() {
        for (javafx.stage.Window w : new java.util.ArrayList<>(javafx.stage.Window.getWindows())) {
            if (w instanceof javafx.stage.PopupWindow) continue;
            Scene sc = w.getScene();
            if (sc == null) continue;
            String flat = url(SHAPE_FLAT_CSS);
            // move the flat sheet to the end (or drop it) without touching the other sheets
            if (flat != null) sc.getStylesheets().remove(flat);
            if (flat != null && isFlat()) sc.getStylesheets().add(flat);
            var root = sc.getRoot();
            if (root != null && root.getStyleClass().contains("settings-window-root")) {
                root.getStyleClass().remove("flat");
                if (isFlat()) root.getStyleClass().add("flat");
            }
        }
    }

    private String url(String path) {
        var r = getClass().getResource(path);
        return r != null ? r.toExternalForm() : null;
    }

    private void addStylesheet(Scene scene, String resourcePath) {
        var resource = getClass().getResource(resourcePath);
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
        }
    }
}
