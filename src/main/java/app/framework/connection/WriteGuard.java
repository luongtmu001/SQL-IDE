package app.framework.connection;

import app.framework.backend.BackendFlags;
import app.ui.dialogs.ReadOnlyGuardDialog;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import javafx.stage.Window;

/**
 * Shared write-guard utility ensuring operations only proceed when the target profile is writable (TD-6).
 */
public final class WriteGuard {

    private WriteGuard() {}

    public static boolean ensureWritable(ConnectionProfileDto profile, Window owner) {
        ConnectionProfileDto currentProf = profile != null ? ProfileLookup.byId(profile.id()) : null;
        if (currentProf == null && profile != null) currentProf = ProfileLookup.byName(profile.name());
        if (currentProf == null) currentProf = profile;

        if (BackendFlags.readOnlyAll() || (currentProf != null && currentProf.readOnly())) {
            return ReadOnlyGuardDialog.show(owner, currentProf != null ? currentProf.name() : "");
        }
        return true;
    }
}
