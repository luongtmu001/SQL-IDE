package app.framework.metadata.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Central manager for Database Explorer filters.
 */
public class DesktopFilterManager {

    private static DesktopFilterManager instance;

    // connectionId -> DatabaseFilter
    private final Map<String, DatabaseFilter> databaseFilters = new ConcurrentHashMap<>();

    // connectionId::catalog::schema::objectType -> ObjectFilter
    private final Map<String, ObjectFilter> objectFilters = new ConcurrentHashMap<>();

    // connectionId::catalog -> schemas to show
    private final Map<String, DatabaseFilter> schemaFilters = new ConcurrentHashMap<>();

    private Consumer<Void> onFilterChangedCallback;

    private DesktopFilterManager() {}

    public static synchronized DesktopFilterManager getInstance() {
        if (instance == null) {
            instance = new DesktopFilterManager();
        }
        return instance;
    }

    public void setOnFilterChanged(Consumer<Void> callback) {
        this.onFilterChangedCallback = callback;
    }

    public DatabaseFilter getDatabaseFilter(String connectionId) {
        if (connectionId == null) return new DatabaseFilter();
        return databaseFilters.computeIfAbsent(connectionId, k -> new DatabaseFilter());
    }

    public void setDatabaseFilter(String connectionId, DatabaseFilter filter) {
        if (connectionId == null) return;
        if (filter == null || !filter.isActive()) {
            databaseFilters.remove(connectionId);
        } else {
            databaseFilters.put(connectionId, filter);
        }
        notifyChanged();
    }

    public boolean hasDatabaseFilter(String connectionId) {
        DatabaseFilter f = databaseFilters.get(connectionId);
        return f != null && f.isActive();
    }

    /** Schemas to show under one database (Phase 8 / Explorer parity: LLTool "Filter Schemas"). Not active = all. */
    public DatabaseFilter getSchemaFilter(String connectionId, String catalog) {
        return schemaFilters.computeIfAbsent(schemaKey(connectionId, catalog), k -> new DatabaseFilter());
    }

    public void setSchemaFilter(String connectionId, String catalog, DatabaseFilter filter) {
        String key = schemaKey(connectionId, catalog);
        if (filter == null || !filter.isActive()) {
            schemaFilters.remove(key);
        } else {
            schemaFilters.put(key, filter);
        }
        notifyChanged();
    }

    public boolean hasSchemaFilter(String connectionId, String catalog) {
        DatabaseFilter f = schemaFilters.get(schemaKey(connectionId, catalog));
        return f != null && f.isActive();
    }

    private static String schemaKey(String connectionId, String catalog) {
        return (connectionId != null ? connectionId : "") + "::" + (catalog != null ? catalog.toLowerCase() : "");
    }

    public ObjectFilter getObjectFilter(String connectionId, String catalog, String schema, String objectType) {
        String key = makeKey(connectionId, catalog, schema, objectType);
        return objectFilters.computeIfAbsent(key, k -> new ObjectFilter());
    }

    public void setObjectFilter(String connectionId, String catalog, String schema, String objectType, ObjectFilter filter) {
        String key = makeKey(connectionId, catalog, schema, objectType);
        if (filter == null || !filter.isActive()) {
            objectFilters.remove(key);
        } else {
            objectFilters.put(key, filter);
        }
        notifyChanged();
    }

    public boolean hasObjectFilter(String connectionId, String catalog, String schema, String objectType) {
        String key = makeKey(connectionId, catalog, schema, objectType);
        ObjectFilter f = objectFilters.get(key);
        return f != null && f.isActive();
    }

    public void clearObjectFilter(String connectionId, String catalog, String schema, String objectType) {
        String key = makeKey(connectionId, catalog, schema, objectType);
        objectFilters.remove(key);
        notifyChanged();
    }

    private void notifyChanged() {
        if (onFilterChangedCallback != null) {
            onFilterChangedCallback.accept(null);
        }
    }

    public static String makeKey(String connectionId, String catalog, String schema, String objectType) {
        return (connectionId != null ? connectionId : "") + "::" +
                (catalog != null ? catalog : "") + "::" +
                (schema != null ? schema : "") + "::" +
                (objectType != null ? objectType.toUpperCase() : "");
    }
}

