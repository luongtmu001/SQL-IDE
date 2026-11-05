package app.services;

import app.application.AppContext;
import app.ui.components.ToastHost;
import app.ui.components.ToastHost.Action;
import app.ui.components.ToastHost.Level;
import javafx.application.Platform;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * DLG-011: toast notifications (self-drawn, R-UI-01), bottom-right of the active window.
 * Errors with long text get a "Chi tiết" link that opens the full message.
 */
public class NotificationService {

    private static final NotificationService INSTANCE = new NotificationService();
    private static final int DETAIL_THRESHOLD = 120;

    private NotificationService() {
    }

    public static NotificationService getInstance() {
        return INSTANCE;
    }

    public void showSuccess(String title, String message) {
        show(Level.SUCCESS, title, message);
    }

    public void showInfo(String title, String message) {
        show(Level.INFO, title, message);
    }

    public void showWarning(String title, String message) {
        show(Level.WARNING, title, message);
    }

    public void showError(String title, String message) {
        show(Level.ERROR, title, message);
    }

    /** Toast with link actions, e.g. {@code new Action("Thử lại", this::retry)}. */
    public void show(Level level, String title, String message, Action... actions) {
        runOnFxThread(owner -> {
            List<Action> list = new ArrayList<>(Arrays.asList(actions));
            boolean longText = message != null && (message.length() > DETAIL_THRESHOLD || message.lines().count() > 3);
            if (longText && (level == Level.ERROR || level == Level.WARNING)) {
                list.add(0, new Action("Chi tiết", () -> app.ui.dialogs.MessageDialog.show(owner,
                        level == Level.ERROR ? app.ui.dialogs.MessageDialog.Kind.ERROR : app.ui.dialogs.MessageDialog.Kind.WARNING,
                        title, title, message)));
            }
            ToastHost.of(owner).show(level, title, message, list);
        });
    }

    /** The focused window (dialog or main window) so the toast is never hidden behind a modal. */
    private static Window activeOwner() {
        for (Window w : Window.getWindows()) {
            if (w instanceof PopupWindow) continue;
            if (w.isShowing() && w.isFocused()) return w;
        }
        Stage primary = AppContext.getInstance().getPrimaryStage();
        return primary != null && primary.isShowing() ? primary : null;
    }

    private void runOnFxThread(java.util.function.Consumer<Window> action) {
        Runnable safeAction = () -> {
            try {
                // In headless tests or before the stage is displayed, avoid floating popups
                Window owner = activeOwner();
                if (owner != null) action.accept(owner);
            } catch (Exception ignored) {
            }
        };
        if (Platform.isFxApplicationThread()) {
            safeAction.run();
        } else {
            Platform.runLater(safeAction);
        }
    }
}
