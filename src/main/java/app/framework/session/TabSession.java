package app.framework.session;

import app.framework.backend.BackendFlags;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.DatabaseConnection;

/** State of one editor tab's own database session (see {@link TabSessionManager}). */
public final class TabSession {

    /** LOST: the session was open and broke (server down, network, killed); it is closed and needs the user to reconnect. */
    public enum State { NONE, CONNECTING, CONNECTED, ERROR, LOST }

    private final String tabId;
    private volatile ConnectionProfileDto profile;
    private volatile String database;
    private volatile DatabaseConnection connection;
    private volatile State state = State.NONE;
    private volatile String error;
    private volatile Integer serverPid;
    private volatile long connectedAt;

    TabSession(String tabId) {
        this.tabId = tabId;
    }

    void connecting(ConnectionProfileDto p, String db) {
        profile = p;
        database = db;
        connection = null;
        error = null;
        serverPid = null;
        state = State.CONNECTING;
    }

    void connected(ConnectionProfileDto p, String db, DatabaseConnection c, Integer pid) {
        profile = p;
        database = db;
        connection = c;
        serverPid = pid;
        error = null;
        connectedAt = System.currentTimeMillis();
        state = State.CONNECTED;
    }

    void failed(String message) {
        connection = null;
        error = message;
        state = State.ERROR;
    }

    void lost(String message) {
        connection = null;
        error = message;
        serverPid = null;
        state = State.LOST;
    }

    void reset() {
        connection = null;
        error = null;
        serverPid = null;
        state = State.NONE;
    }

    public String tabId() { return tabId; }
    public ConnectionProfileDto profile() { return profile; }
    public String database() { return database; }
    public DatabaseConnection connection() { return connection; }
    public State state() { return state; }
    public String error() { return error; }
    public Integer serverPid() { return serverPid; }
    public long connectedAt() { return connectedAt; }

    /** Profile read-only, or every connection read-only (user decision 2026-11-05). */
    public boolean readOnly() {
        return BackendFlags.readOnlyAll() || (profile != null && profile.readOnly());
    }

    public String environment() {
        return profile != null ? profile.environment() : null;
    }
}
