package app.ui.theme;

import app.services.NotificationService;
import app.services.theme.ThemeDefinition;
import app.services.theme.ThemeRegistry;
import app.services.theme.VsixThemeImporter;
import app.ui.dialogs.ConfirmDialog;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * T3/T4: import a VS Code {@code .vsix} and remove imported themes - one flow for the Themes menu and Settings.
 * Files are read / deleted on a virtual thread; {@code onDone} runs on the FX thread afterwards. The Themes menu
 * follows {@link ThemeRegistry#addChangeListener} (and falls back to Primer when the theme in use is removed).
 */
public final class ThemeImportActions {

    private ThemeImportActions() {
    }

    /** File chooser, import of every theme in the package, toast with the result. */
    public static void importVsix(Window owner, Runnable onDone) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn gói theme VS Code (.vsix)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("VS Code extension (*.vsix)", "*.vsix"));
        File file = chooser.showOpenDialog(owner);
        if (file == null) return;
        Path themesDir = ThemeRegistry.themesDir();
        if (themesDir == null) {
            NotificationService.getInstance().showWarning("Import theme", "Không xác định được thư mục dữ liệu.");
            return;
        }
        Thread.ofVirtual().name("vsix-import").start(() -> {
            try {
                List<VsixThemeImporter.Result> results = VsixThemeImporter.importVsix(file.toPath(), themesDir);
                long ok = results.stream().filter(VsixThemeImporter.Result::ok).count();
                String names = results.stream().filter(VsixThemeImporter.Result::ok)
                        .map(r -> r.label() + (r.kind() == ThemeDefinition.Kind.DARK ? " (tối)" : " (sáng)"))
                        .collect(Collectors.joining(", "));
                String failed = results.stream().filter(r -> !r.ok()).map(r -> r.label() + ": " + r.error())
                        .collect(Collectors.joining("; "));
                Platform.runLater(() -> {
                    if (ok > 0) {
                        NotificationService.getInstance().showSuccess("Import theme",
                                "Đã import " + ok + " theme: " + names + ".");
                    }
                    if (!failed.isEmpty()) {
                        NotificationService.getInstance().showWarning("Import theme", "Không import được: " + failed);
                    }
                    if (onDone != null) onDone.run();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    NotificationService.getInstance().showError("Import theme",
                            "Không đọc được " + file.getName() + ": " + ex.getMessage());
                    if (onDone != null) onDone.run();
                });
            }
        });
    }

    /**
     * Asks, then deletes the themes' folders. {@code what} names them in the question
     * ("gói GitHub Theme (9 theme)" / "theme GitHub Dark Default").
     */
    public static void remove(Window owner, String what, List<ThemeDefinition> themes, Runnable onDone) {
        if (themes == null || themes.isEmpty()) return;
        Path themesDir = ThemeRegistry.themesDir();
        if (themesDir == null) return;
        boolean inUse = themes.stream().anyMatch(d ->
                d.id().equals(app.services.ThemeService.getInstance().getCurrentDefinition().id()));
        String detail = "Thư mục của theme trong \"themes\" sẽ bị xóa; có thể import lại từ file .vsix."
                + (inUse ? " Theme đang dùng sẽ được đổi sang Primer " + (themes.get(0).isDark() ? "Dark" : "Light") + "." : "");
        if (!ConfirmDialog.show(owner, "Xóa theme", "Xóa " + what + "?", detail, "Xóa", true)) return;
        List<String> ids = themes.stream().map(ThemeDefinition::id).toList();
        Thread.ofVirtual().name("theme-remove").start(() -> {
            String error = null;
            try {
                VsixThemeImporter.removeAll(themesDir, ids);
            } catch (Exception e) {
                error = e.getMessage();
            }
            String err = error;
            Platform.runLater(() -> {
                if (err == null) NotificationService.getInstance().showInfo("Xóa theme", "Đã xóa " + what + ".");
                else NotificationService.getInstance().showWarning("Xóa theme", "Không xóa hết được: " + err);
                if (onDone != null) onDone.run();
            });
        });
    }
}
