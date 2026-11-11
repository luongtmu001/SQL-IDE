package app.ui.components;

import app.models.IdeSettings;
import app.services.SettingsService;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Global UI metrics manager implementing H-1 and H-2 from 07-ui-consistency-rules.md:
 * - Coordinates base icon size dynamically linked to IdeSettings
 * - Distributes icon scale bindings across SM, MD, LG, XL
 * - Propagates UI typography (-fx-font-family / -fx-font-size) to every open Scene
 */
public final class UiMetrics {

    private static final IntegerProperty baseIconSize = new SimpleIntegerProperty(13);

    private static final IntegerBinding smBinding = Bindings.createIntegerBinding(
            () -> Math.max(8, baseIconSize.get() - 2), baseIconSize);
    private static final IntegerBinding mdBinding = Bindings.createIntegerBinding(
            baseIconSize::get, baseIconSize);
    private static final IntegerBinding lgBinding = Bindings.createIntegerBinding(
            () -> baseIconSize.get() + 4, baseIconSize);
    private static final IntegerBinding xlBinding = Bindings.createIntegerBinding(
            () -> (int) Math.round(baseIconSize.get() * 2.5), baseIconSize);

    static {
        try {
            IdeSettings s = SettingsService.getInstance().getCurrentSettings();
            if (s != null && s.getAppearance() != null) {
                int size = s.getAppearance().getIconSize();
                if (size > 0) baseIconSize.set(size);
            }
            SettingsService.getInstance().addSettingsListener(UiMetrics::onSettingsChanged);
        } catch (Throwable ignored) {
            // In headless/test environments where SettingsService might not be fully initialized
        }
    }

    private UiMetrics() {}

    private static void onSettingsChanged(IdeSettings settings) {
        if (settings == null) return;
        if (settings.getAppearance() != null) {
            int newIconSize = settings.getAppearance().getIconSize();
            if (newIconSize > 0 && newIconSize != baseIconSize.get()) {
                if (Platform.isFxApplicationThread()) {
                    baseIconSize.set(newIconSize);
                } else {
                    Platform.runLater(() -> baseIconSize.set(newIconSize));
                }
            }
        }
        // Reapply typography to all active windows
        reapplyTypographyToAllWindows();
    }

    public static IntegerProperty baseIconSizeProperty() {
        return baseIconSize;
    }

    public static int getBaseIconSize() {
        return baseIconSize.get();
    }

    public static void setBaseIconSize(int size) {
        baseIconSize.set(Math.max(8, size));
    }

    public static IntegerBinding bindingFor(IconScale scale) {
        if (scale == null) return mdBinding;
        return switch (scale) {
            case SM -> smBinding;
            case MD -> mdBinding;
            case LG -> lgBinding;
            case XL -> xlBinding;
        };
    }

    /** Current UI font size in px ("Giao diện/Theme"), 13 when settings are unavailable. */
    public static int uiFontSize() {
        try {
            IdeSettings s = SettingsService.getInstance().getCurrentSettings();
            int size = s != null ? s.getUiFontSize() : 13;
            return (size < 10 || size > 24) ? 13 : size;
        } catch (Throwable t) {
            return 13;
        }
    }

    /**
     * R-UI-12: converts a size given in em to px for the current UI font size. Use it for sizes that
     * have to be set from Java (pane and column widths/heights) instead of writing a px literal.
     */
    public static double em(double em) {
        return Math.round(em * uiFontSize());
    }

    /**
     * Applies UI typography from IdeSettings to the root of the given scene.
     */
    public static void applyUiTypography(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        IdeSettings settings = null;
        try {
            settings = SettingsService.getInstance().getCurrentSettings();
        } catch (Throwable ignored) {}

        String fontFamily = settings != null && settings.getUiFontFamily() != null && !settings.getUiFontFamily().isBlank()
                ? settings.getUiFontFamily() : "Inter";
        int fontSize = settings != null ? settings.getUiFontSize() : 13;
        if (fontSize < 10 || fontSize > 24) fontSize = 13;

        String style = String.format(Locale.ROOT, "-fx-font-family: '%s'; -fx-font-size: %dpx;", fontFamily, fontSize);

        Node root = scene.getRoot();
        String currentStyle = root.getStyle();
        if (currentStyle == null || currentStyle.isBlank()) {
            root.setStyle(style);
        } else {
            String cleaned = currentStyle.replaceAll("-fx-font-(family|size):[^;]+;?", "").trim();
            root.setStyle((cleaned.isEmpty() ? "" : cleaned + " ") + style);
        }
    }

    /**
     * Reapplies UI typography to all open windows (dialogs, secondary windows, main stage).
     */
    public static void reapplyTypographyToAllWindows() {
        Runnable task = () -> {
            try {
                for (Window w : new ArrayList<>(Window.getWindows())) {
                    if (w != null && w.getScene() != null) {
                        applyUiTypography(w.getScene());
                    }
                }
            } catch (Throwable ignored) {}
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }
}
