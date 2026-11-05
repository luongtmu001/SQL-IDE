package app.ui.dialogs;

import app.controllers.ConnectionManagerController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * SCR-DLG-001 launcher. Uses {@link DialogChrome} (custom title bar, rounded or flat).
 * Every method returns the id of the connection the user asked to open with "Lưu &amp; kết nối",
 * or {@code null}.
 */
public final class ConnectionManagerDialog {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManagerDialog.class);

    private ConnectionManagerDialog() {}

    public static String show(Stage owner) {
        return open(owner, c -> c.selectConnectionByIdOrName(null));
    }

    public static String show(Stage owner, String targetConnectionIdOrName) {
        return open(owner, c -> c.selectConnectionByIdOrName(targetConnectionIdOrName));
    }

    public static String showNew(Stage owner) {
        return open(owner, ConnectionManagerController::startNewConnection);
    }

    /** Empty form with the group pre-filled (Explorer group menu → "Kết nối mới"). */
    public static String showNew(Stage owner, String groupPath) {
        return open(owner, c -> c.startNewConnectionInGroup(groupPath));
    }

    private static String open(Stage owner, Consumer<ConnectionManagerController> prepare) {
        try {
            FXMLLoader loader = new FXMLLoader(ConnectionManagerDialog.class.getResource("/fxml/connection-manager-dialog.fxml"));
            Parent root = loader.load();
            ConnectionManagerController controller = loader.getController();

            DialogChrome chrome = DialogChrome.create(owner, "Quản lý kết nối", "fth-database")
                    .subtitle("Dữ liệu mẫu — chưa kết nối database thật")
                    .rawContent(root)
                    .size(880, 560);
            var css = ConnectionManagerDialog.class.getResource("/css/connection-manager.css");
            if (css != null) chrome.stage().getScene().getStylesheets().add(css.toExternalForm());
            app.services.ThemeService.getInstance().applyShape(chrome.stage().getScene()); // R-UI-01 last

            prepare.accept(controller);
            chrome.showAndWait();
            return controller.getConnectRequestedId();
        } catch (Exception e) {
            log.error("Failed to open Connection Manager: {}", e.getMessage(), e);
            return null;
        }
    }
}
