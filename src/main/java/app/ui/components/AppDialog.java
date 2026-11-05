package app.ui.components;

import app.ui.dialogs.MessageDialog;
import javafx.stage.Stage;

/**
 * Standardized message boxes. Since Phase 6 these are {@link MessageDialog}s
 * (DialogChrome + R-UI-01) instead of system Alerts.
 */
public final class AppDialog {

    private AppDialog() {}

    public static boolean confirm(Stage owner, String title, String header, String content) {
        return MessageDialog.confirm(owner, title, header, content, null, false);
    }

    public static void info(Stage owner, String title, String header, String content) {
        MessageDialog.show(owner, MessageDialog.Kind.INFO, title, header, content);
    }

    public static void warning(Stage owner, String title, String header, String content) {
        MessageDialog.show(owner, MessageDialog.Kind.WARNING, title, header, content);
    }

    public static void error(Stage owner, String title, String header, String content) {
        MessageDialog.show(owner, MessageDialog.Kind.ERROR, title, header, content);
    }
}
