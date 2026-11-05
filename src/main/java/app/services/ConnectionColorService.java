package app.services;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;

import java.util.List;
import java.util.prefs.Preferences;

/**
 * SCR-MAIN-005: per-connection status bar colour (approved 2026-11-05: one colour per
 * connection, default blue). UI preference only, stored locally with java.util.prefs;
 * it never touches the connection profile or the database.
 */
public final class ConnectionColorService {

    public record ConnectionColor(String key, String label, String hex) {
    }

    public static final ConnectionColor DEFAULT = new ConnectionColor("blue", "Xanh dương (mặc định)", "#007acc");

    public static final List<ConnectionColor> PALETTE = List.of(
            DEFAULT,
            new ConnectionColor("purple", "Tím", "#68217a"),
            new ConnectionColor("teal", "Xanh ngọc", "#0e7c7b"),
            new ConnectionColor("green", "Xanh lá", "#16825d"),
            new ConnectionColor("orange", "Cam", "#c25e00"),
            new ConnectionColor("red", "Đỏ", "#c42b1c"),
            new ConnectionColor("pink", "Hồng", "#b0306a"),
            new ConnectionColor("brown", "Nâu", "#7a5230"),
            new ConnectionColor("gray", "Xám", "#4d5561"));

    private static final ConnectionColorService INSTANCE = new ConnectionColorService();

    private final Preferences prefs = Preferences.userNodeForPackage(ConnectionColorService.class).node("connection_colors");
    /** Bumped on every change so views can refresh. */
    private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper(0);

    private ConnectionColorService() {
    }

    public static ConnectionColorService getInstance() {
        return INSTANCE;
    }

    public ReadOnlyIntegerProperty revisionProperty() {
        return revision.getReadOnlyProperty();
    }

    public ConnectionColor colorFor(String connectionKey) {
        if (connectionKey == null || connectionKey.isBlank()) {
            return DEFAULT;
        }
        String stored = prefs.get(sanitize(connectionKey), DEFAULT.key());
        return PALETTE.stream().filter(c -> c.key().equals(stored)).findFirst().orElse(DEFAULT);
    }

    public void setColor(String connectionKey, ConnectionColor color) {
        if (connectionKey == null || connectionKey.isBlank()) {
            return;
        }
        try {
            if (color == null || color.equals(DEFAULT)) {
                prefs.remove(sanitize(connectionKey));
            } else {
                prefs.put(sanitize(connectionKey), color.key());
            }
        } catch (Exception ignored) {
            // preference store unavailable: keep in-session behaviour only
        }
        revision.set(revision.get() + 1);
    }

    private static String sanitize(String key) {
        String k = key.replace('/', '_');
        return k.length() > Preferences.MAX_KEY_LENGTH ? k.substring(0, Preferences.MAX_KEY_LENGTH) : k;
    }
}
