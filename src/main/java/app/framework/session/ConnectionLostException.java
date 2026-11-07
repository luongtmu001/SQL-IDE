package app.framework.session;

import java.sql.SQLException;

/**
 * The tab's session was open but is no longer alive (found when a run starts or fails). The session is
 * already marked {@link TabSession.State#LOST}; the UI asks the user to reconnect before running again
 * (user decision 2026-11-06: no silent reconnect, since temp tables / transactions of the old session are gone).
 */
public final class ConnectionLostException extends SQLException {
    public ConnectionLostException(String message) {
        super(message, "08003");
    }
}
