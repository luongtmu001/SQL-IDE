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

        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile)) {
                        if (conn == null) return List.<String>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<String>> res = provider.get().getCatalogs(conn);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<String>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch catalogs for {}: {}", profile.name(), e.getMessage());
                    }
                    return List.<String>of();
                },
                result -> {
                    cache.put(profile.id(), cacheKey, "", result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
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
                        if (conn == null) return List.<String>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<String>> res = provider.get().getSchemas(conn, catalog);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<String>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch schemas for {}/{}: {}", profile.name(), catalog, e.getMessage());
                    }
                    return List.<String>of();
                },
                result -> {
                    cache.put(profile.id(), cacheKey, catalog, result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
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
                        if (conn == null) return List.<TableMetadata>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<TableMetadata>> res = queryFn.query(provider.get(), conn);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<TableMetadata>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch {} for {}/{}: {}", typeKey, profile.name(), path, e.getMessage());
                    }
                    return List.<TableMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), typeKey, path, result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
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
                        if (conn == null) return List.<ColumnMetadata>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<ColumnMetadata>> res = provider.get().getColumns(conn, catalog, schema, table);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<ColumnMetadata>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch columns for {}: {}", path, e.getMessage());
                    }
                    return List.<ColumnMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "COLUMNS", path, result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
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
                        if (conn == null) return List.<RoutineMetadata>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<RoutineMetadata>> res = provider.get().getRoutines(conn, catalog, schema);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<RoutineMetadata>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch routines for {}: {}", path, e.getMessage());
                    }
                    return List.<RoutineMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "ROUTINES", path, result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
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
                        if (conn == null) return List.<IndexMetadata>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<IndexMetadata>> res = provider.get().getIndexes(conn, catalog, schema, table);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<IndexMetadata>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch indexes for {}: {}", path, e.getMessage());
                    }
                    return List.<IndexMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "INDEXES", path, result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
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
                        if (conn == null) return List.<TriggerMetadata>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<TriggerMetadata>> res = provider.get().getTriggers(conn, catalog, schema, table);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<TriggerMetadata>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch triggers for {}: {}", path, e.getMessage());
                    }
                    return List.<TriggerMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "TRIGGERS", path, result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
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
                        if (conn == null) return List.<UserTypeMetadata>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<UserTypeMetadata>> res = provider.get().getUserDefinedTypes(conn, catalog, schema);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<UserTypeMetadata>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch user defined types for {}: {}", path, e.getMessage());
                    }
                    return List.<UserTypeMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "USER_TYPES", path, result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
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
                        if (conn == null) return List.<KeyMetadata>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<KeyMetadata>> res = provider.get().getKeys(conn, catalog, schema, table);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<KeyMetadata>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch keys for {}: {}", path, e.getMessage());
                    }
                    return List.<KeyMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "KEYS", path, result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
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
                        if (conn == null) return List.<ConstraintMetadata>of();
                        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                        var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
                        if (provider.isPresent()) {
                            Result<List<ConstraintMetadata>> res = provider.get().getConstraints(conn, catalog, schema, table);
                            return res.isSuccess() ? res.getDataOrThrow() : List.<ConstraintMetadata>of();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch constraints for {}: {}", path, e.getMessage());
                    }
                    return List.<ConstraintMetadata>of();
                },
                result -> {
                    cache.put(profile.id(), "CONSTRAINTS", path, result);
                    callback.accept(result);
                },
                error -> callback.accept(List.of())
        );
    }

    private DatabaseConnection openMetadataConnection(ConnectionProfileDto profile) {
        return openMetadataConnection(profile, null);
    }

    private DatabaseConnection openMetadataConnection(ConnectionProfileDto profile, String targetCatalog) {
        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
        var optProvider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
        if (optProvider.isEmpty()) return null;

        String db = (targetCatalog != null && !targetCatalog.isBlank())
                ? targetCatalog
                : (profile.databaseName() != null ? profile.databaseName() : "");

        String resolvedPassword = "";
        if (profile.credentialRef() != null && !profile.credentialRef().isBlank()) {
            resolvedPassword = DesktopBootstrap.getInstance().getCredentialVault().resolveSecret(profile.credentialRef());
        }

        DatabaseConnectionConfig config = new DatabaseConnectionConfig(
                profile.id(),
                profile.name(),
                type,
                profile.host(),
                profile.port(),
                type == DatabaseType.SQLITE ? profile.sqliteFilePath() : db,
                profile.username(),
                profile.credentialRef(),
                profile.properties() != null ? profile.properties() : Collections.emptyMap()
        );

        Result<DatabaseConnection> connRes = optProvider.get().connect(config, resolvedPassword);
        return connRes.isSuccess() ? connRes.getDataOrThrow() : null;
    }

    @FunctionalInterface
    private interface MetadataQueryFunction<T> {
        Result<List<T>> query(DatabaseProvider provider, DatabaseConnection conn);
    }
}

