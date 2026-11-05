package app.framework.metadata;

import app.framework.async.AsyncTaskManager;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.connection.ConnectionSessionManager;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;
import com.lltool.infrastructure.database.api.*;
import com.lltool.infrastructure.database.api.metadata.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Asynchronous Metadata Service for Desktop UI (Phase 6.3).
 * Fetches metadata using DatabaseProvider on managed background threads,
 * transparently caches responses, and delivers results onto the JavaFX Application Thread.
 */
public class DesktopMetadataService {

    private static final Logger log = LoggerFactory.getLogger(DesktopMetadataService.class);
    private static DesktopMetadataService instance;

    private final DesktopMetadataCache cache = new DesktopMetadataCache();

    private DesktopMetadataService() {}

    public static synchronized DesktopMetadataService getInstance() {
        if (instance == null) {
            instance = new DesktopMetadataService();
        }
        return instance;
    }

    public DesktopMetadataCache getCache() {
        return cache;
    }

    public void invalidate(String connectionId) {
        cache.invalidateConnection(connectionId);
        closeConnections(connectionId);
    }

    public void invalidateNode(String connectionId, String path) {
        cache.invalidateNode(connectionId, path);
    }

    public void fetchCatalogs(ConnectionProfileDto profile, Consumer<List<String>> callback) {
        if (MockMetadataProvider.isEnabled()) {
            var states = app.services.ExplorerConnectionState.getInstance();
            if (states.get(profile.id()) != app.services.ExplorerConnectionState.Status.CONNECTED) {
                states.set(profile.id(), app.services.ExplorerConnectionState.Status.CONNECTING);
            }
            MockMetadataProvider.deliver(() -> MockMetadataProvider.catalogs(profile), result -> {
                states.set(profile.id(), app.services.ExplorerConnectionState.Status.CONNECTED);
                callback.accept(result);
            });
            return;
        }

        Objects.requireNonNull(profile, "profile cannot be null");
        String cacheKey = "CATALOGS";
        List<String> cached = cache.get(profile.id(), cacheKey, "");
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        var states = app.services.ExplorerConnectionState.getInstance();
        states.set(profile.id(), app.services.ExplorerConnectionState.Status.CONNECTING);
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile)) {
                        
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<String>> res = provider.get().getCatalogs(conn);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch catalogs for {}: {}", profile.name(), e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<String>of();
                },
                result -> {
                    cache.put(profile.id(), cacheKey, "", result);
                    states.set(profile.id(), app.services.ExplorerConnectionState.Status.CONNECTED);
                    callback.accept(result);
                },
                error -> {
                    String msg = errorMessage(error);
                    states.set(profile.id(), app.services.ExplorerConnectionState.Status.ERROR, msg);
                    app.services.NotificationService.getInstance().showError("Không kết nối được " + profile.name(), msg);
                    callback.accept(List.of());
                }
        );
    }

    public void fetchSchemas(ConnectionProfileDto profile, String catalog, Consumer<List<String>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.schemas(profile, catalog), callback); return; }

        Objects.requireNonNull(profile, "profile cannot be null");
        String cacheKey = "SCHEMAS";
        List<String> cached = cache.get(profile.id(), cacheKey, catalog);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog)) {
                        
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<String>> res = provider.get().getSchemas(conn, catalog);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch schemas for {}/{}: {}", profile.name(), catalog, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<String>of();
                },
                result -> {
                    cache.put(profile.id(), cacheKey, catalog, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    public void fetchTables(ConnectionProfileDto profile, String catalog, String schema, Consumer<List<TableMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.tables(profile, catalog, schema), callback); return; }

        fetchTableObjects(profile, catalog, schema, "TABLES", callback, (provider, conn) -> provider.getTables(conn, catalog, schema));
    }

    public void fetchViews(ConnectionProfileDto profile, String catalog, String schema, Consumer<List<TableMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.views(profile, catalog, schema), callback); return; }

        fetchTableObjects(profile, catalog, schema, "VIEWS", callback, (provider, conn) -> provider.getViews(conn, catalog, schema));
    }

    private void fetchTableObjects(ConnectionProfileDto profile, String catalog, String schema, String typeKey,
                                   Consumer<List<TableMetadata>> callback,
                                   MetadataQueryFunction<TableMetadata> queryFn) {
        Objects.requireNonNull(profile, "profile cannot be null");
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "");
        List<TableMetadata> cached = cache.get(profile.id(), typeKey, path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog)) {
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<TableMetadata>> res = queryFn.query(provider.get(), conn);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch {} for {}/{}: {}", typeKey, profile.name(), path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<TableMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), typeKey, path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    public void fetchColumns(ConnectionProfileDto profile, String catalog, String schema, String table, Consumer<List<ColumnMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.columns(profile, catalog, schema, table), callback); return; }

        Objects.requireNonNull(profile, "profile cannot be null");
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "") + "/" + table;
        List<ColumnMetadata> cached = cache.get(profile.id(), "COLUMNS", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog)) {
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<ColumnMetadata>> res = provider.get().getColumns(conn, catalog, schema, table);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch columns for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<ColumnMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "COLUMNS", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    public void fetchRoutines(ConnectionProfileDto profile, String catalog, String schema, Consumer<List<RoutineMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.routines(profile, catalog, schema), callback); return; }

        Objects.requireNonNull(profile, "profile cannot be null");
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "");
        List<RoutineMetadata> cached = cache.get(profile.id(), "ROUTINES", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog)) {
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<RoutineMetadata>> res = provider.get().getRoutines(conn, catalog, schema);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch routines for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<RoutineMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "ROUTINES", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    public void fetchIndexes(ConnectionProfileDto profile, String catalog, String schema, String table, Consumer<List<IndexMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.indexes(profile, catalog, schema, table), callback); return; }

        Objects.requireNonNull(profile, "profile cannot be null");
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "") + "/" + table;
        List<IndexMetadata> cached = cache.get(profile.id(), "INDEXES", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog)) {
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<IndexMetadata>> res = provider.get().getIndexes(conn, catalog, schema, table);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch indexes for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<IndexMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "INDEXES", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    public void fetchTriggers(ConnectionProfileDto profile, String catalog, String schema, String table, Consumer<List<TriggerMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.triggers(profile, catalog, schema, table), callback); return; }

        Objects.requireNonNull(profile, "profile cannot be null");
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "") + "/" + (table != null ? table : "");
        List<TriggerMetadata> cached = cache.get(profile.id(), "TRIGGERS", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog)) {
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<TriggerMetadata>> res = provider.get().getTriggers(conn, catalog, schema, table);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch triggers for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<TriggerMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "TRIGGERS", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    public void fetchMaterializedViews(ConnectionProfileDto profile, String catalog, String schema, Consumer<List<TableMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.materializedViews(profile, catalog, schema), callback); return; }

        fetchTableObjects(profile, catalog, schema, "MATERIALIZED_VIEWS", callback, (provider, conn) -> provider.getMaterializedViews(conn, catalog, schema));
    }

    public void fetchUserDefinedTypes(ConnectionProfileDto profile, String catalog, String schema, Consumer<List<UserTypeMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.userTypes(profile, catalog, schema), callback); return; }

        Objects.requireNonNull(profile, "profile cannot be null");
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "");
        List<UserTypeMetadata> cached = cache.get(profile.id(), "USER_TYPES", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog)) {
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<UserTypeMetadata>> res = provider.get().getUserDefinedTypes(conn, catalog, schema);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch user defined types for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<UserTypeMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "USER_TYPES", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    public void fetchKeys(ConnectionProfileDto profile, String catalog, String schema, String table, Consumer<List<KeyMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.keys(profile, catalog, schema, table), callback); return; }

        Objects.requireNonNull(profile, "profile cannot be null");
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "") + "/" + table;
        List<KeyMetadata> cached = cache.get(profile.id(), "KEYS", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog)) {
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<KeyMetadata>> res = provider.get().getKeys(conn, catalog, schema, table);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch keys for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<KeyMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "KEYS", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    public void fetchConstraints(ConnectionProfileDto profile, String catalog, String schema, String table, Consumer<List<ConstraintMetadata>> callback) {
        if (MockMetadataProvider.isEnabled()) { MockMetadataProvider.deliver(() -> MockMetadataProvider.constraints(profile, catalog, schema, table), callback); return; }

        Objects.requireNonNull(profile, "profile cannot be null");
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "") + "/" + table;
        List<ConstraintMetadata> cached = cache.get(profile.id(), "CONSTRAINTS", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog)) {
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<ConstraintMetadata>> res = provider.get().getConstraints(conn, catalog, schema, table);
                            if (res.isFailure()) throw new IllegalStateException(res.getError().map(er -> er.message()).orElse("Lỗi đọc metadata"));
                            return res.getDataOrThrow();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch constraints for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return List.<ConstraintMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "CONSTRAINTS", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    private DatabaseConnection openMetadataConnection(ConnectionProfileDto profile) {
        return openMetadataConnection(profile, null);
    }

    // ------------------------------------------------------------------ Explorer connections (Phase 8 / D2)

    /**
     * One reusable read-only connection per profile + database for the Explorer (LLTool keeps one
     * explorer session per connection). Before Phase 8 every node expansion opened and closed a new
     * connection, did not use the in-memory DLG-002 password and hid connection errors.
     * The returned wrapper ignores close(); {@link #invalidate(String)} closes the pooled ones.
     */
    private final Map<String, DatabaseConnection> pool = new java.util.concurrent.ConcurrentHashMap<>();

    private DatabaseConnection openMetadataConnection(ConnectionProfileDto profile, String targetCatalog) {
        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
        if (type == null) {
            throw new IllegalStateException("Loại cơ sở dữ liệu \"" + profile.databaseType() + "\" chưa được hỗ trợ.");
        }
        String db = type == DatabaseType.SQLITE ? "" : (targetCatalog != null && !targetCatalog.isBlank()
                ? targetCatalog : (profile.databaseName() != null ? profile.databaseName() : ""));
        // profile edits (host, user, …) give a new key, so an edited profile never reuses an old server
        String key = profile.id() + "|" + db.toLowerCase(Locale.ROOT) + "|" + profile.host() + ":" + profile.port()
                + "|" + profile.username() + "|" + profile.authType() + "|" + profile.sqliteFilePath();
        synchronized (pool) {
            DatabaseConnection existing = pool.get(key);
            if (existing != null && existing.isValid(2)) return keepOpen(existing);
            if (existing != null) closeQuietly(existing);
            Result<DatabaseConnection> res = ConnectionSessionManager.getInstance().openSession(
                    profile, db.isEmpty() ? null : db, app.framework.session.CredentialResolver.resolve(profile, null));
            if (res.isFailure()) {
                throw new IllegalStateException(res.getError().map(e -> e.message()).orElse("Không kết nối được."));
            }
            DatabaseConnection conn = res.getDataOrThrow();
            pool.put(key, conn);
            return keepOpen(conn);
        }
    }

    /** Closes the Explorer connections of a profile (refresh / disconnect / profile edited). */
    public void closeConnections(String connectionId) {
        if (connectionId == null) return;
        synchronized (pool) {
            pool.entrySet().removeIf(e -> {
                if (!e.getKey().startsWith(connectionId + "|")) return false;
                closeQuietly(e.getValue());
                return true;
            });
        }
    }

    public void closeAllConnections() {
        synchronized (pool) {
            pool.values().forEach(DesktopMetadataService::closeQuietly);
            pool.clear();
        }
    }

    private static void closeQuietly(DatabaseConnection c) {
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    private static DatabaseConnection keepOpen(DatabaseConnection c) {
        return new DatabaseConnection() {
            @Override public DatabaseConnectionConfig getConfig() { return c.getConfig(); }
            @Override public java.sql.Connection getRawJdbcConnection() throws java.sql.SQLException { return c.getRawJdbcConnection(); }
            @Override public boolean isValid(int timeout) { return c.isValid(timeout); }
            @Override public void close() { /* pooled: kept open for the next expansion */ }
        };
    }

    private static String errorMessage(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null && (t.getMessage() == null || t instanceof java.util.concurrent.CompletionException)) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    private static void reportError(ConnectionProfileDto profile, Throwable error) {
        app.services.NotificationService.getInstance().showWarning("Lỗi đọc metadata · " + profile.name(), errorMessage(error));
    }

    @FunctionalInterface
    private interface MetadataQueryFunction<T> {
        Result<List<T>> query(DatabaseProvider provider, DatabaseConnection conn);
    }
}

