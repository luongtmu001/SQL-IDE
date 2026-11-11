package app.ui.dialogs;

import app.framework.backend.BackendFlags;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.session.TabSessionManager;
import com.lltool.core.api.request.UpdateConnectionRequest;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modal dialog presented when a statement or DDL is blocked by Read-Only mode.
 * Provides the user with a direct action to switch the connection to writable.
 */
public final class ReadOnlyGuardDialog {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyGuardDialog.class);

    private ReadOnlyGuardDialog() {}

    /**
     * Shows the Read-Only warning dialog.
     * If the user clicks "Chuyển sang cho phép ghi", the connection profile is updated to readOnly=false,
     * BackendFlags override is set to false, active tab sessions are updated, and returns true.
     *
     * @param owner owner window
     * @param connectionName name of the connection
     * @return true if the connection was converted to writable; false if cancelled
     */
    public static boolean show(Window owner, String connectionName) {
        if (owner == null) {
            javafx.stage.Stage stage = app.application.AppContext.getInstance().getPrimaryStage();
            if (stage != null && stage.getScene() != null) {
                owner = stage.getScene().getWindow();
            }
        }
        boolean[] switched = {false};
        DialogChrome chrome = DialogChrome.create(owner, "Cảnh báo: Kết nối Chỉ Đọc (Read-Only)", "fth-lock")
                .iconStyle("warning");

        Label badge = new Label("CHỈ ĐỌC (READ-ONLY)");
        badge.getStyleClass().addAll("env-badge-chip", "warn");
        HBox badgeBox = new HBox(6, badge);
        badgeBox.setAlignment(Pos.CENTER_LEFT);

        Label msg = new Label("Kết nối này hiện đang được thiết lập ở chế độ CHỈ ĐỌC. Mọi thao tác ghi dữ liệu (INSERT, UPDATE, DELETE) và thay đổi cấu trúc bảng (DDL) đều bị chặn để bảo vệ an toàn dữ liệu.");
        msg.getStyleClass().add("dialog-message");
        msg.setWrapText(true);

        GridPane info = new GridPane();
        info.setHgap(8);
        info.setVgap(4);
        info.getStyleClass().add("prod-info");
        Label l1 = new Label("Kết nối:");
        l1.getStyleClass().add("dialog-label");
        Label val1 = new Label(connectionName != null && !connectionName.isBlank() ? connectionName : "—");
        val1.setStyle("-fx-font-weight: bold;");
        info.addRow(0, l1, val1);

        Label q = new Label("Bạn có muốn chuyển kết nối này sang chế độ CHO PHÉP GHI (Writable) để tiếp tục thực thi không?");
        q.getStyleClass().add("guard-question");
        q.setWrapText(true);

        Button cancel = DialogChrome.cancel("Hủy (Esc)");
        Button switchToWrite = DialogChrome.primary("Chuyển sang cho phép ghi");
        FontIcon unlockIcon = new FontIcon("fth-unlock");
        unlockIcon.setIconSize(13);
        switchToWrite.setGraphic(unlockIcon);

        cancel.setOnAction(e -> chrome.close());
        switchToWrite.setOnAction(e -> {
            boolean ok = unlockConnection(connectionName);
            switched[0] = ok;
            chrome.close();
        });

        VBox body = new VBox(10, badgeBox, msg, info, q);
        chrome.content(body).footer(null, cancel, switchToWrite).size(500, -1);
        Platform.runLater(switchToWrite::requestFocus);
        chrome.showAndWait();

        return switched[0];
    }

    private static boolean unlockConnection(String connectionName) {
        // 1. Reset BackendFlags global override if active
        BackendFlags.setReadOnlyAll(false);

        // 2. Locate connection profile
        ConnectionProfileDto profile = app.framework.connection.ProfileLookup.byName(connectionName);
        if (profile == null) {
            profile = app.framework.connection.ProfileLookup.byId(connectionName);
        }
        if (profile == null) {
            profile = TabSessionManager.profileByName(connectionName);
        }
        if (profile != null) {
            try {
                var api = DesktopBootstrap.getInstance().getConnectionApi();
                api.updateConnection(new UpdateConnectionRequest(
                        profile.id(), profile.name(), profile.group(), profile.environment(),
                        false, // readOnly = false
                        profile.databaseType(), profile.host(), profile.port(), profile.databaseName(),
                        profile.sqliteFilePath(), profile.username(), profile.authType(),
                        null, profile.savePassword(), profile.timeoutSeconds(),
                        profile.trustServerCertificate(), profile.properties()
                ));
            } catch (Exception ex) {
                log.warn("Failed to persist writable mode for profile: {}", ex.getMessage());
            }

            // 3. Invalidate lookup cache
            app.framework.connection.ProfileLookup.invalidate();

            // 4. Update in TabSessionManager sessions
            TabSessionManager.getInstance().setProfileReadOnly(profile.id(), false);
            TabSessionManager.getInstance().setProfileReadOnly(profile.name(), false);

            // 5. Update MainController
            app.controllers.MainController mc = app.application.AppContext.getInstance().getMainController();
            if (mc != null) {
                mc.setConnectionReadOnly(profile.name(), false);
                mc.setConnectionReadOnly(profile.id(), false);
                mc.refreshConnectionDropdown();
                if (mc.getSidebarController() != null) {
                    mc.getSidebarController().loadData();
                }
            }
        }

        return true;
    }
}

