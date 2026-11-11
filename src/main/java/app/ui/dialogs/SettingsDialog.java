package app.ui.dialogs;

import app.services.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog launcher for the Settings Center (Feature 8).
 * Pre-warmed and cached singleton dialog instance for instant zero-lag opening (< 20ms).
 */
public final class SettingsDialog {

    private static final Logger log = LoggerFactory.getLogger(SettingsDialog.class);

    private static DialogChrome cachedChrome;
    private static app.controllers.SettingsDialogController cachedController;
    private static Window cachedOwner;

    private SettingsDialog() {}

    /**
     * Pre-loads the settings dialog in the background during idle time,
     * eliminating the 1-2s delay on first open.
     */
    public static void preload(Window owner) {
        if (cachedChrome != null) return;
        Platform.runLater(() -> {
            try {
                if (cachedChrome == null) {
                    buildDialog(owner);
                }
            } catch (Exception e) {
                log.warn("[settings] Preload failed (will build on first open): {}", e.getMessage());
            }
        });
    }

    public static void show(Stage owner) {
        try {
            if (cachedChrome == null) {
                buildDialog(owner);
            }

            if (cachedChrome != null) {
                if (cachedController != null) {
                    cachedController.prepareForShow();
                }

                // Ensure stylesheets and theme styling remain 100% solid and synchronized with active theme
                var scene = cachedChrome.stage().getScene();
                if (scene != null) {
                    ThemeService.getInstance().applyThemeToScene(scene);
                    var sCss = SettingsDialog.class.getResource("/css/settings.css");
                    if (sCss != null) {
                        String sUrl = sCss.toExternalForm();
                        if (!scene.getStylesheets().contains(sUrl)) {
                            scene.getStylesheets().add(sUrl);
                        }
                    }
                    ThemeService.getInstance().applyShape(scene);
                }

                cachedChrome.showAndWait();
            }
        } catch (Exception e) {
            log.error("Failed to open Settings Dialog: {}", e.getMessage(), e);
        }
    }

    public static javafx.scene.Scene getCachedDialogScene() {
        return cachedChrome != null && cachedChrome.stage() != null ? cachedChrome.stage().getScene() : null;
    }

    private static synchronized void buildDialog(Window owner) {
        if (cachedChrome != null) return;
        long t0 = System.currentTimeMillis();
        try {
            FXMLLoader loader = new FXMLLoader(SettingsDialog.class.getResource("/fxml/settings-dialog.fxml"));
            loader.setClassLoader(SettingsDialog.class.getClassLoader());
            Parent root = loader.load();
            app.controllers.SettingsDialogController controller = loader.getController();

            // Phase 5 (Gate B 2026-11-05): DialogChrome window (R-UI-01); the old title bar keeps only the search box
            if (root.lookup("#titleBar") instanceof HBox bar) {
                bar.getChildren().removeIf(n -> !(n.getStyleClass().contains("settings-search-box")));
                bar.getStyleClass().add("settings-search-row");
                bar.getChildren().stream().findFirst().ifPresent(n -> HBox.setHgrow(n, Priority.ALWAYS));
                if (bar.lookup(".settings-search-input") instanceof TextField tf) {
                    tf.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(tf, Priority.ALWAYS);
                }
            }
            root.getStyleClass().add("in-chrome");
            if (ThemeService.isFlat()) root.getStyleClass().add("flat");

            if (root instanceof Region r) {
                r.setPrefSize(900, 600);
                r.setMinSize(760, 480);
            }

            // Save at the bottom (footer) with the unsaved-changes counter; no separate "Đóng" (window × closes)
            Button save = controller.getSaveButton();
            Label dirty = controller.getDirtyLabel();
            if (save != null && save.getParent() instanceof Pane p) p.getChildren().remove(save);
            if (dirty != null && dirty.getParent() instanceof Pane p) p.getChildren().remove(dirty);
            if (save != null) {
                save.getStyleClass().setAll("button", "dialog-button", "accent");
                save.setText("Lưu");
                save.setDefaultButton(false);
            }

            DialogChrome chrome = DialogChrome.create(owner, "Cài đặt", "fth-sliders")
                    .rawContent(root);
            if (save != null) chrome.footer(dirty != null ? new Node[]{dirty} : null, save);
            chrome.size(900, 640);

            var scene = chrome.stage().getScene();
            if (scene != null) {
                ThemeService.getInstance().applyThemeToScene(scene);
                var css = SettingsDialog.class.getResource("/css/settings.css");
                if (css != null) {
                    String url = css.toExternalForm();
                    if (!scene.getStylesheets().contains(url)) {
                        scene.getStylesheets().add(url);
                    }
                }
                ThemeService.getInstance().applyShape(scene);
            }
            chrome.ensureStylesheets();
            chrome.onCloseRequest(controller::canClose);

            cachedChrome = chrome;
            cachedController = controller;
            cachedOwner = owner;

            log.info("[settings] SettingsDialog initialized and cached in {} ms", (System.currentTimeMillis() - t0));
        } catch (Exception e) {
            log.error("[settings] Failed to construct SettingsDialog: {}", e.getMessage(), e);
        }
    }

    /**
     * Clears the cached dialog instance (e.g. for testing or theme reset).
     */
    public static void clearCache() {
        if (cachedChrome != null) {
            try {
                cachedChrome.close();
            } catch (Exception ignored) {}
            cachedChrome = null;
            cachedController = null;
            cachedOwner = null;
        }
    }
}
