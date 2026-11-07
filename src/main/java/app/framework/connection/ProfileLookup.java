package app.framework.connection;

import app.framework.bootstrap.DesktopBootstrap;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;

import java.util.List;

/**
 * The saved connection profiles, read at most once every {@value #TTL_MS} ms.
 * Tab decorations and the per-tab session lookups asked the store (all profile files on disk) several
 * times on every tab switch; a short cache removes that without ever going stale for long.
 */
public final class ProfileLookup {

    private static final long TTL_MS = 2000;
    private static volatile List<ConnectionProfileDto> cached = List.of();
    private static volatile long readAt;

    private ProfileLookup() {
    }

    public static List<ConnectionProfileDto> all() {
        long now = System.currentTimeMillis();
        if (now - readAt < TTL_MS) return cached;
        try {
            var res = DesktopBootstrap.getInstance().getConnectionApi().getConnections();
            if (res.isSuccess()) {
                cached = List.copyOf(res.getDataOrThrow());
                readAt = now;
            }
        } catch (Exception ignored) {
            // store unavailable: keep the last list
        }
        return cached;
    }

    /** Profile by name or id (case-insensitive), or null. */
    public static ConnectionProfileDto byName(String name) {
        if (name == null || name.isBlank()) return null;
        for (ConnectionProfileDto p : all()) {
            if (name.equalsIgnoreCase(p.name()) || name.equalsIgnoreCase(p.id())) return p;
        }
        return null;
    }

    /** After a profile was added, changed or removed. */
    public static void invalidate() {
        readAt = 0;
    }
}
