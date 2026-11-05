package app.ui.dialogs;

import app.services.ThemeService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog launcher for the Settings Center (Feature 8).
 */
public final class SettingsDialog {

    private static final Logger log = LoggerFactory.getLogger(SettingsDialog.class);

    private SettingsDialog() {}

    public static void show(Stage owner) {
        try {
            FXMLLoader loader = new FXMLLoader(SettingsDialog.class.getResource("/fxml/settings-dialog.fxml"));
            Parent root = loader.load();
            app.controllers.SettingsDialogController controller = loader.getController();

            // Phase 5 (Gate B 2026-11-05): DialogChrome window (R-UI-01); the old title bar keeps only the search box
            if (root.lookup("#titleBar") instanceof javafx.scene.layout.HBox bar) {
                bar.getChildren().removeIf(n -> !(n.getStyleClass().contains("settings-search-box")));
                bar.getStyleClass().add("settings-search-row");
                bar.getChildren().stream().findFirst().ifPresent(n -> javafx.scene.layout.HBox.setHgrow(n, javafx.scene.layout.Priority.ALWAYS));
                if (bar.lookup(".settings-search-input") instanceof javafx.scene.control.TextField tf) {
                    tf.setMaxWidth(Double.MAX_VALUE);
                    javafx.scene.layout.HBox.setHgrow(tf, javafx.scene.layout.Priority.ALWAYS);
                }
            }
            root.getStyleClass().add("in-chrome");
            if (ThemeService.isFlat()) root.getStyleClass().add("flat");

            // a bit smaller than before (user request 2026-11-05)
            if (root instanceof javafx.scene.layout.Region r) {
                r.setPrefSize(900, 600);
                r.setMinSize(760, 480);
            }
            // Save at the bottom (footer) with the unsaved-changes counter; no separate "Đóng" (window × closes)
            javafx.scene.control.Button save = controller.getSaveButton();
            javafx.scene.control.Label dirty = controller.getDirtyLabel();
            if (save != null && save.getParent() instanceof javafx.scene.layout.Pane p) p.getChildren().remove(save);
            if (dirty != null && dirty.getParent() instanceof javafx.scene.layout.Pane p) p.getChildren().remove(dirty);
            if (save != null) {
                save.getStyleClass().setAll("button", "dialog-button", "accent");
                save.setText("Lưu");
                save.setDefaultButton(false);
            }
            DialogChrome chrome = DialogChrome.create(owner, "Cài đặt", "fth-sliders")
                    .rawContent(root);
            if (save != null) chrome.footer(dirty != null ? new javafx.scene.Node[]{dirty} : null, save);
            chrome.size(900, 640);
            var css = SettingsDialog.class.getResource("/css/settings.css");
            if (css != null) chrome.stage().getScene().getStylesheets().add(css.toExternalForm());
            ThemeService.getInstance().applyShape(chrome.stage().getScene());
            chrome.onCloseRequest(controller::canClose);
            chrome.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open Settings Dialog: {}", e.getMessage(), e);
        }
    }
}

