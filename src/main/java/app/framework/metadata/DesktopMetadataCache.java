package app.framework.metadata;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe In-Memory Metadata Cache for Database Explorer (Phase 6.3).
 * Prevents redundant roundtrips to the database during tree navigation.
 */
public class DesktopMetadataCache {

    public record CacheKey(String connectionId, String objectType, String parentPath) {
        public CacheKey {
            connectionId = connectionId != null ? connectionId : "";
            objectType = objectType != null ? objectType : "";
            parentPath = parentPath != null ? parentPath : "";
        }
    }

    private final Map<CacheKey, Object> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String connectionId, String objectType, String parentPath) {
        return (T) cache.get(new CacheKey(connectionId, objectType, parentPath));
    }

    public void put(String connectionId, String objectType, String parentPath, Object data) {
        if (data != null) {
            cache.put(new CacheKey(connectionId, objectType, parentPath), data);
        }
    }

    public void invalidateConnection(String connectionId) {
        if (connectionId == null) return;
        cache.keySet().removeIf(k -> k.connectionId().equals(connectionId));
    }

    public void invalidateNode(String connectionId, String parentPath) {
        if (connectionId == null) return;
        cache.keySet().removeIf(k -> k.connectionId().equals(connectionId) && k.parentPath().startsWith(parentPath));
    }

    public void clear() {
        cache.clear();
    }
}

