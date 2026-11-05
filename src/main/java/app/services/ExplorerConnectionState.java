package app.services;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCR-MAIN-002: per-connection status shown in the Object Explorer
 * (icon dimmed / spinner text / bold name / red icon). UI state only.
 */
public final class ExplorerConnectionState {

    public enum Status { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private static final ExplorerConnectionState INSTANCE = new ExplorerConnectionState();

    private final Map<String, Status> statuses = new ConcurrentHashMap<>();
    private final Map<String, String> errors = new ConcurrentHashMap<>();
    private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper(0);

    private ExplorerConnectionState() {
    }

    public static ExplorerConnectionState getInstance() {
        return INSTANCE;
    }

    /** Bumped on every change; the Explorer refreshes its cells when it changes. */
    public ReadOnlyIntegerProperty revisionProperty() {
        return revision.getReadOnlyProperty();
    }

    public Status get(String connectionId) {
        return connectionId == null ? Status.DISCONNECTED : statuses.getOrDefault(connectionId, Status.DISCONNECTED);
    }

    public String getError(String connectionId) {
        return connectionId == null ? null : errors.get(connectionId);
    }

    public void set(String connectionId, Status status) {
        set(connectionId, status, null);
    }

    public void set(String connectionId, Status status, String error) {
        if (connectionId == null) return;
        statuses.put(connectionId, status);
        if (error != null) errors.put(connectionId, error); else errors.remove(connectionId);
        if (Platform.isFxApplicationThread()) {
            revision.set(revision.get() + 1);
        } else {
            Platform.runLater(() -> revision.set(revision.get() + 1));
        }
    }
}
