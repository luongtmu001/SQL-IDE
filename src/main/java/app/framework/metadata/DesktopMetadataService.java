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
                    lastErrors.put(profile.id(), msg);
                    app.services.PasswordPolicyService.getInstance().forgetSessionPasswordIfAuthError(profile.id(), msg);
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
                        if (type == DatabaseType.POSTGRESQL) {
                            // one row per overload: "name(identity arguments)" (LLTool parity)
                            List<RoutineMetadata> out = new ArrayList<>();
                            try (java.sql.PreparedStatement ps = conn.getRawJdbcConnection().prepareStatement(
                                    "SELECT p.proname, pg_catalog.pg_get_function_identity_arguments(p.oid), p.prokind, "
                                            + "pg_catalog.pg_get_function_result(p.oid) FROM pg_catalog.pg_proc p "
                                            + "JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace "
                                            + "WHERE n.nspname = ? AND p.prokind IN ('f','p') ORDER BY p.proname, p.oid")) {
                                ps.setString(1, schema);
                                try (java.sql.ResultSet rs = ps.executeQuery()) {
                                    while (rs.next()) {
                                        boolean proc = "p".equals(rs.getString(3));
                                        out.add(new RoutineMetadata(rs.getString(1) + "(" + rs.getString(2) + ")", schema, catalog,
                                                proc ? "PROCEDURE" : "FUNCTION", proc ? null : rs.getString(4)));
                                    }
                                }
                            }
                            return out;
                        }
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
    /** Up to {@value #MAX_PER_KEY} reusable read-only connections per profile + database, so Explorer reads can run in parallel. */
    private static final int MAX_PER_KEY = 2;

    private static final class Slot {
        final java.util.ArrayDeque<DatabaseConnection> idle = new java.util.ArrayDeque<>();
        final java.util.Set<DatabaseConnection> all = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final java.util.concurrent.Semaphore permits;
        volatile boolean closed;

        Slot(int max) {
            permits = new java.util.concurrent.Semaphore(max);
        }
    }

    private final Map<String, Slot> pool = new java.util.concurrent.ConcurrentHashMap<>();

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
        // SQLite is a single file/connection: keep it serial
        int max = type == DatabaseType.SQLITE ? 1 : MAX_PER_KEY;
        Slot slot = pool.computeIfAbsent(key, k -> new Slot(max));
        try {
            if (!slot.permits.tryAcquire(60, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("Hết thời gian chờ kết nối Explorer.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị ngắt khi chờ kết nối Explorer.");
        }
        DatabaseConnection conn = null;
        try {
            while (true) {
                DatabaseConnection candidate;
                synchronized (slot.idle) {
                    candidate = slot.idle.pollFirst();
                }
                if (candidate == null) break;
                if (candidate.isValid(2)) {
                    conn = candidate;
                    break;
                }
                slot.all.remove(candidate);
                closeQuietly(candidate);
            }
            if (conn == null) {
                Result<DatabaseConnection> res = ConnectionSessionManager.getInstance().openSession(
                        profile, db.isEmpty() ? null : db, app.framework.session.CredentialResolver.resolve(profile, null));
                if (res.isFailure()) {
                    throw new IllegalStateException(res.getError().map(e -> e.message()).orElse("Không kết nối được."));
                }
                conn = res.getDataOrThrow();
                slot.all.add(conn);
            }
        } catch (RuntimeException e) {
            slot.permits.release();
            throw e;
        }
        return borrowed(slot, conn);
    }

    /**
     * Phase 8 / D2: sequences (the provider API has none, so the "Sequences" folder used to stay empty).
     * PostgreSQL {@code pg_sequences}, SQL Server {@code sys.sequences}. Mock mode: two sample names.
     */
    public void fetchSequences(ConnectionProfileDto profile, String catalog, String schema, Consumer<List<String>> callback) {
        if (MockMetadataProvider.isEnabled()) {
            MockMetadataProvider.deliver(() -> List.of("order_id_seq", "invoice_no_seq"), callback);
            return;
        }
        String cacheKey = "SEQUENCES";
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "");
        List<String> cached = cache.get(profile.id(), cacheKey, path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                    String sql = type == DatabaseType.POSTGRESQL
                            ? "SELECT sequencename FROM pg_catalog.pg_sequences WHERE schemaname = ? ORDER BY 1"
                            : type == DatabaseType.SQLSERVER
                            ? "SELECT sq.name FROM sys.sequences sq JOIN sys.schemas s ON s.schema_id = sq.schema_id WHERE s.name = ? ORDER BY 1"
                            : null;
                    List<String> names = new ArrayList<>();
                    if (sql == null) return names;
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog);
                         java.sql.PreparedStatement ps = conn.getRawJdbcConnection().prepareStatement(sql)) {
                        ps.setString(1, schema);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) names.add(rs.getString(1));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch sequences for {}/{}: {}", profile.name(), path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return names;
                },
                result -> {
                    cache.put(profile.id(), cacheKey, path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    /** One parameter of a procedure / function. {@code mode}: IN, OUT, INOUT, RETURN (may be empty). */
    public record RoutineParam(String name, String type, String mode) { }

    /** SQL Server synonym: name and the object it points to. */
    public record SynonymInfo(String name, String target) { }

    /** SQL Server database user (principal). */
    public record DbUserInfo(String name, String typeDesc, String defaultSchema, boolean disabled) { }

    /** Row of {@code sys.servers} (is_linked = 1). */
    public record LinkedServerInfo(String name, String product, String provider, String dataSource) { }

    /** Table / view of a linked server ({@code sp_tables_ex}). */
    public record RemoteObjectInfo(String catalog, String schema, String name, boolean view) { }

    /** A PostgreSQL role (cluster level). */
    public record RoleInfo(String name, boolean superuser, boolean canLogin) { }

    /**
     * Explorer parity with LLTool: parameters of a procedure / function. PostgreSQL
     * {@code information_schema.parameters}, SQL Server {@code sys.parameters}. Overloaded PostgreSQL routines
     * share one name, so their parameters are listed together.
     */
    public void fetchRoutineParameters(ConnectionProfileDto profile, String catalog, String schema, String routine,
                                       Consumer<List<RoutineParam>> callback) {
        if (MockMetadataProvider.isEnabled()) {
            MockMetadataProvider.deliver(() -> List.of(new RoutineParam("p_period", "integer", "IN"),
                    new RoutineParam("p_company", "text", "IN")), callback);
            return;
        }
        String pure = routine == null ? "" : (routine.contains("(") ? routine.substring(0, routine.indexOf('(')).trim() : routine);
        // PostgreSQL overloads: "name(int, text)" -> match that overload only
        String identityArgs = routine != null && routine.contains("(") && routine.endsWith(")")
                ? routine.substring(routine.indexOf('(') + 1, routine.length() - 1) : null;
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "") + "/" + (routine != null ? routine : "");
        List<RoutineParam> cached = cache.get(profile.id(), "ROUTINE_PARAMS", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                    String sql = type == DatabaseType.POSTGRESQL
                            ? "SELECT COALESCE(p.parameter_name, 'arg' || p.ordinal_position), p.data_type, p.parameter_mode "
                            + "FROM information_schema.parameters p "
                            + "JOIN pg_catalog.pg_proc pr ON p.specific_name = pr.proname || '_' || pr.oid::text "
                            + "JOIN pg_catalog.pg_namespace n ON n.oid = pr.pronamespace "
                            + "WHERE n.nspname = ? AND pr.proname = ? "
                            + (identityArgs != null ? "AND pg_catalog.pg_get_function_identity_arguments(pr.oid) = ? " : "")
                            + "ORDER BY pr.oid, p.ordinal_position"
                            : type == DatabaseType.SQLSERVER
                            ? "SELECT p.name, TYPE_NAME(p.user_type_id), "
                            + "CASE WHEN p.parameter_id = 0 THEN 'RETURN' WHEN p.is_output = 1 THEN 'OUT' ELSE 'IN' END "
                            + "FROM sys.parameters p JOIN sys.objects o ON o.object_id = p.object_id "
                            + "JOIN sys.schemas s ON s.schema_id = o.schema_id "
                            + "WHERE s.name = ? AND o.name = ? ORDER BY p.parameter_id"
                            : null;
                    List<RoutineParam> out = new ArrayList<>();
                    if (sql == null) return out;
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog);
                         java.sql.PreparedStatement ps = conn.getRawJdbcConnection().prepareStatement(sql)) {
                        ps.setString(1, schema);
                        ps.setString(2, pure);
                        if (type == DatabaseType.POSTGRESQL && identityArgs != null) ps.setString(3, identityArgs);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String n = rs.getString(1);
                                out.add(new RoutineParam(n == null || n.isBlank() ? "Return Value" : n, rs.getString(2), rs.getString(3)));
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch parameters for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return out;
                },
                result -> {
                    cache.put(profile.id(), "ROUTINE_PARAMS", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    /**
     * Object counts of every category of one schema in a single round trip (keys: TABLES, VIEWS,
     * MATERIALIZED_VIEWS, PROCEDURES, FUNCTIONS, TRIGGERS, SEQUENCES, USER_TYPES). Delivers {@code null} when
     * the dialect has no aggregate query or it failed; the caller then falls back to the per-category lists.
     * Counts are only the badge: a category still lists (and re-counts) its real objects when it is opened.
     */
    public void fetchSchemaCounts(ConnectionProfileDto profile, String catalog, String schema,
                                  Consumer<java.util.Map<String, Integer>> callback) {
        if (MockMetadataProvider.isEnabled()) { callback.accept(null); return; }
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "");
        java.util.Map<String, Integer> cached = cache.get(profile.id(), "SCHEMA_COUNTS", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
                    String sql;
                    String[] keys;
                    if (type == DatabaseType.POSTGRESQL) {
                        keys = new String[]{"TABLES", "VIEWS", "MATERIALIZED_VIEWS", "SEQUENCES", "PROCEDURES", "FUNCTIONS", "TRIGGERS", "USER_TYPES"};
                        sql = "SELECT "
                                + "(SELECT count(*) FROM pg_catalog.pg_class c WHERE c.relnamespace = n.oid AND c.relkind IN ('r','p')), "
                                + "(SELECT count(*) FROM pg_catalog.pg_class c WHERE c.relnamespace = n.oid AND c.relkind = 'v'), "
                                + "(SELECT count(*) FROM pg_catalog.pg_class c WHERE c.relnamespace = n.oid AND c.relkind = 'm'), "
                                + "(SELECT count(*) FROM pg_catalog.pg_class c WHERE c.relnamespace = n.oid AND c.relkind = 'S'), "
                                + "(SELECT count(*) FROM pg_catalog.pg_proc p WHERE p.pronamespace = n.oid AND p.prokind = 'p'), "
                                + "(SELECT count(*) FROM pg_catalog.pg_proc p WHERE p.pronamespace = n.oid AND p.prokind = 'f'), "
                                + "(SELECT count(*) FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid "
                                + "   WHERE c.relnamespace = n.oid AND NOT t.tgisinternal), "
                                + "(SELECT count(*) FROM pg_catalog.pg_type ty LEFT JOIN pg_catalog.pg_class c ON c.oid = ty.typrelid "
                                + "   WHERE ty.typnamespace = n.oid AND ty.typtype IN ('e','d') OR (ty.typnamespace = n.oid AND ty.typtype = 'c' AND c.relkind = 'c')) "
                                + "FROM pg_catalog.pg_namespace n WHERE n.nspname = ?";
                    } else if (type == DatabaseType.SQLSERVER) {
                        keys = new String[]{"TABLES", "VIEWS", "PROCEDURES", "FUNCTIONS", "TRIGGERS", "SEQUENCES", "SYNONYMS", "USER_TYPES"};
                        sql = "SELECT "
                                + "ISNULL(SUM(CASE WHEN o.type = 'U' THEN 1 ELSE 0 END), 0), "
                                + "ISNULL(SUM(CASE WHEN o.type = 'V' THEN 1 ELSE 0 END), 0), "
                                + "ISNULL(SUM(CASE WHEN o.type IN ('P','PC') THEN 1 ELSE 0 END), 0), "
                                + "ISNULL(SUM(CASE WHEN o.type IN ('FN','IF','TF','FS','FT') THEN 1 ELSE 0 END), 0), "
                                + "ISNULL(SUM(CASE WHEN o.type IN ('TR','TA') THEN 1 ELSE 0 END), 0), "
                                + "ISNULL(SUM(CASE WHEN o.type = 'SO' THEN 1 ELSE 0 END), 0), "
                                + "ISNULL(SUM(CASE WHEN o.type = 'SN' THEN 1 ELSE 0 END), 0), "
                                + "(SELECT count(*) FROM sys.types t WHERE t.is_user_defined = 1 AND t.schema_id = SCHEMA_ID(?)) "
                                + "FROM sys.objects o WHERE o.is_ms_shipped = 0 AND o.schema_id = SCHEMA_ID(?)";
                    } else {
                        return null;
                    }
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog);
                         java.sql.PreparedStatement ps = conn.getRawJdbcConnection().prepareStatement(sql)) {
                        ps.setString(1, schema);
                        if (type == DatabaseType.SQLSERVER) ps.setString(2, schema);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            java.util.Map<String, Integer> out = new java.util.HashMap<>();
                            if (rs.next()) {
                                for (int i = 0; i < keys.length; i++) out.put(keys[i], rs.getInt(i + 1));
                            }
                            return out;
                        }
                    } catch (Exception e) {
                        log.warn("Schema counts unavailable for {}: {}", path, e.getMessage());
                        return null; // caller falls back to the per-category lists
                    }
                },
                result -> {
                    if (result != null) cache.put(profile.id(), "SCHEMA_COUNTS", path, result);
                    callback.accept(result);
                },
                error -> callback.accept(null)
        );
    }

    /** SQL Server synonyms of a schema ({@code sys.synonyms}), as LLTool lists them. */
    public void fetchSynonyms(ConnectionProfileDto profile, String catalog, String schema, Consumer<List<SynonymInfo>> callback) {
        if (MockMetadataProvider.isEnabled()) {
            MockMetadataProvider.deliver(() -> List.of(new SynonymInfo("syn_customer", "[Other].[dbo].[Customer]")), callback);
            return;
        }
        String path = (catalog != null ? catalog : "") + "/" + (schema != null ? schema : "");
        List<SynonymInfo> cached = cache.get(profile.id(), "SYNONYMS", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    List<SynonymInfo> out = new ArrayList<>();
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog);
                         java.sql.PreparedStatement ps = conn.getRawJdbcConnection().prepareStatement(
                                 "SELECT sy.name, sy.base_object_name FROM sys.synonyms sy "
                                         + "JOIN sys.schemas s ON s.schema_id = sy.schema_id WHERE s.name = ? ORDER BY sy.name")) {
                        ps.setString(1, schema);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) out.add(new SynonymInfo(rs.getString(1), rs.getString(2)));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch synonyms for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return out;
                },
                result -> {
                    cache.put(profile.id(), "SYNONYMS", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    /**
     * SQL Server users of one database: the same {@code sys.database_principals} query as LLTool's
     * {@code sqlserver_provider.list_principals} (users of every kind plus database roles, nothing filtered out).
     */
    public void fetchDatabaseUsers(ConnectionProfileDto profile, String catalog, Consumer<List<DbUserInfo>> callback) {
        if (MockMetadataProvider.isEnabled()) {
            MockMetadataProvider.deliver(() -> List.of(new DbUserInfo("dbo", "SQL_USER", "dbo", false),
                    new DbUserInfo("DOMAIN\\tester", "WINDOWS_USER", "dbo", false)), callback);
            return;
        }
        String path = catalog != null ? catalog : "";
        List<DbUserInfo> cached = cache.get(profile.id(), "DB_USERS", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    List<DbUserInfo> out = new ArrayList<>();
                    try (DatabaseConnection conn = openMetadataConnection(profile, catalog);
                         java.sql.Statement st = conn.getRawJdbcConnection().createStatement();
                         java.sql.ResultSet rs = st.executeQuery(
                                 "SELECT dp.name, dp.type_desc, dp.default_schema_name, ISNULL(sp.is_disabled, 0) "
                                         + "FROM sys.database_principals dp LEFT JOIN sys.server_principals sp ON sp.sid = dp.sid "
                                         + "WHERE dp.type IN ('S','U','G','C','K','E','X') OR (dp.principal_id > 0 AND dp.type = 'R') "
                                         + "ORDER BY dp.name")) {
                        while (rs.next()) out.add(new DbUserInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
                    } catch (Exception e) {
                        log.warn("Failed to fetch users for {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return out;
                },
                result -> {
                    cache.put(profile.id(), "DB_USERS", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    /** Explorer parity with LLTool: PostgreSQL roles of the cluster ({@code pg_roles}). */
    public void fetchRoles(ConnectionProfileDto profile, Consumer<List<RoleInfo>> callback) {
        if (MockMetadataProvider.isEnabled()) {
            MockMetadataProvider.deliver(() -> List.of(new RoleInfo("postgres", true, true),
                    new RoleInfo("app_user", false, true), new RoleInfo("readers", false, false)), callback);
            return;
        }
        List<RoleInfo> cached = cache.get(profile.id(), "ROLES", "");
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    List<RoleInfo> out = new ArrayList<>();
                    try (DatabaseConnection conn = openMetadataConnection(profile, null);
                         java.sql.Statement st = conn.getRawJdbcConnection().createStatement();
                         java.sql.ResultSet rs = st.executeQuery("SELECT rolname, rolsuper, rolcanlogin FROM pg_catalog.pg_roles ORDER BY rolname")) {
                        while (rs.next()) {
                            out.add(new RoleInfo(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3)));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch roles for {}: {}", profile.name(), e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return out;
                },
                result -> {
                    cache.put(profile.id(), "ROLES", "", result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }


    // ------------------------------------------------------------------ Linked servers (SQL Server, batch 3)

    /** Linked servers of the instance ({@code sys.servers WHERE is_linked = 1}), as LLTool lists them. */
    public void fetchLinkedServers(ConnectionProfileDto profile, Consumer<List<LinkedServerInfo>> callback) {
        if (MockMetadataProvider.isEnabled()) {
            MockMetadataProvider.deliver(() -> List.of(new LinkedServerInfo("REMOTE01", "SQL Server", "SQLNCLI", "10.0.0.5")), callback);
            return;
        }
        List<LinkedServerInfo> cached = cache.get(profile.id(), "LINKED_SERVERS", "");
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    List<LinkedServerInfo> out = new ArrayList<>();
                    try (DatabaseConnection conn = openMetadataConnection(profile, null);
                         java.sql.Statement st = conn.getRawJdbcConnection().createStatement();
                         java.sql.ResultSet rs = st.executeQuery(
                                 "SELECT name, product, provider, data_source FROM sys.servers WHERE is_linked = 1 ORDER BY name")) {
                        while (rs.next()) out.add(new LinkedServerInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                    } catch (Exception e) {
                        log.warn("Failed to fetch linked servers for {}: {}", profile.name(), e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return out;
                },
                result -> {
                    cache.put(profile.id(), "LINKED_SERVERS", "", result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    /** Catalogs of a linked server ({@code sp_catalogs}); an unreachable server gives an empty list (LLTool). */
    public void fetchRemoteCatalogs(ConnectionProfileDto profile, String server, Consumer<List<String>> callback) {
        if (MockMetadataProvider.isEnabled()) {
            MockMetadataProvider.deliver(() -> List.of("RemoteDb"), callback);
            return;
        }
        List<String> cached = cache.get(profile.id(), "LINKED_CATALOGS", server);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    List<String> out = new ArrayList<>();
                    try (DatabaseConnection conn = openMetadataConnection(profile, null);
                         java.sql.PreparedStatement ps = conn.getRawJdbcConnection().prepareStatement("EXEC sp_catalogs @server_name = ?")) {
                        ps.setString(1, cleanName(server));
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String n = rs.getString(1);
                                if (n != null && !n.isEmpty()) out.add(n);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch catalogs of linked server {}: {}", server, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return out;
                },
                result -> {
                    cache.put(profile.id(), "LINKED_CATALOGS", server, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    /** Tables and views of one catalog of a linked server ({@code sp_tables_ex}, de-duplicated like LLTool). */
    public void fetchRemoteObjects(ConnectionProfileDto profile, String server, String database, Consumer<List<RemoteObjectInfo>> callback) {
        if (MockMetadataProvider.isEnabled()) {
            MockMetadataProvider.deliver(() -> List.of(new RemoteObjectInfo(database, "dbo", "Customer", false),
                    new RemoteObjectInfo(database, "dbo", "vw_Orders", true)), callback);
            return;
        }
        String path = server + "/" + database;
        List<RemoteObjectInfo> cached = cache.get(profile.id(), "LINKED_OBJECTS", path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    List<RemoteObjectInfo> out = new ArrayList<>();
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    try (DatabaseConnection conn = openMetadataConnection(profile, null);
                         java.sql.PreparedStatement ps = conn.getRawJdbcConnection()
                                 .prepareStatement("EXEC sp_tables_ex @table_server = ?, @table_catalog = ?")) {
                        ps.setString(1, cleanName(server));
                        ps.setString(2, cleanName(database));
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String cat = rs.getString(1);
                                String sch = rs.getString(2);
                                String name = rs.getString(3);
                                String type = rs.getString(4);
                                if (name == null || name.isEmpty()) continue;
                                if (!seen.add(name.toLowerCase() + "|" + (sch == null ? "" : sch.toLowerCase()))) continue;
                                out.add(new RemoteObjectInfo(cat != null ? cat : database, sch != null ? sch : "dbo", name,
                                        type != null && type.toUpperCase().contains("VIEW")));
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch objects of {}: {}", path, e.getMessage());
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return out;
                },
                result -> {
                    cache.put(profile.id(), "LINKED_OBJECTS", path, result);
                    callback.accept(result);
                },
                error -> {
                    reportError(profile, error);
                    callback.accept(List.of());
                }
        );
    }

    /** "Test Connection" of a linked server ({@code sp_testlinkedserver}); not cached, no error toast (caller shows the result). */
    public void testLinkedServer(ConnectionProfileDto profile, String server, Runnable onOk, Consumer<String> onError) {
        if (MockMetadataProvider.isEnabled()) {
            javafx.application.Platform.runLater(onOk);
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    try (DatabaseConnection conn = openMetadataConnection(profile, null);
                         java.sql.PreparedStatement ps = conn.getRawJdbcConnection().prepareStatement("EXEC sp_testlinkedserver @servername = ?")) {
                        ps.setString(1, cleanName(server));
                        ps.execute();
                    } catch (Exception e) {
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return Boolean.TRUE;
                },
                ok -> onOk.run(),
                error -> onError.accept(error != null && error.getMessage() != null ? error.getMessage() : String.valueOf(error))
        );
    }

    /** CREATE / DROP script of a linked server, built from {@code sys.servers} (read-only: nothing is executed). */
    public void fetchLinkedServerScript(ConnectionProfileDto profile, String server, LinkedServerScripts.Mode mode,
                                        Consumer<String> callback, Consumer<String> onError) {
        if (MockMetadataProvider.isEnabled()) {
            String mock = LinkedServerScripts.build(
                    new LinkedServerScripts.Info(server, "SQL Server", "SQLNCLI", "10.0.0.5", "", "", ""), mode);
            javafx.application.Platform.runLater(() -> callback.accept(mock));
            return;
        }
        AsyncTaskManager.getInstance().executeAsync(
                () -> {
                    LinkedServerScripts.Info info = new LinkedServerScripts.Info(cleanName(server), null, null, null, null, null, null);
                    try (DatabaseConnection conn = openMetadataConnection(profile, null);
                         java.sql.PreparedStatement ps = conn.getRawJdbcConnection().prepareStatement(
                                 "SELECT name, product, provider, data_source, location, provider_string, catalog FROM sys.servers WHERE name = ?")) {
                        ps.setString(1, cleanName(server));
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                info = new LinkedServerScripts.Info(rs.getString(1), rs.getString(2), rs.getString(3),
                                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
                            }
                        }
                    } catch (Exception e) {
                        throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
                    }
                    return LinkedServerScripts.build(info, mode);
                },
                callback::accept,
                error -> onError.accept(error != null && error.getMessage() != null ? error.getMessage() : String.valueOf(error))
        );
    }

    private static String cleanName(String s) {
        return s == null ? null : s.replaceAll("^[\\[\\] \\t\\r\\n]+|[\\[\\] \\t\\r\\n]+$", "");
    }

    /** Blocking: the Explorer's read-only connection for {@code profile} / {@code catalog} (close() keeps it pooled). */
    public DatabaseConnection explorerConnection(ConnectionProfileDto profile, String catalog) {
        return openMetadataConnection(profile, catalog);
    }

    /** Closes the Explorer connections of a profile (refresh / disconnect / profile edited). */
    public void closeConnections(String connectionId) {
        if (connectionId == null) return;
        pool.entrySet().removeIf(e -> {
            if (!e.getKey().startsWith(connectionId + "|")) return false;
            closeSlot(e.getValue());
            return true;
        });
    }

    public void closeAllConnections() {
        pool.values().forEach(DesktopMetadataService::closeSlot);
        pool.clear();
    }

    private static void closeSlot(Slot slot) {
        slot.closed = true;
        slot.all.forEach(DesktopMetadataService::closeQuietly);
        slot.all.clear();
        synchronized (slot.idle) {
            slot.idle.clear();
        }
    }

    private static void closeQuietly(DatabaseConnection c) {
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    /** close() hands the connection back to the pool instead of closing it. */
    private static DatabaseConnection borrowed(Slot slot, DatabaseConnection c) {
        java.util.concurrent.atomic.AtomicBoolean returned = new java.util.concurrent.atomic.AtomicBoolean(false);
        return new DatabaseConnection() {
            @Override public DatabaseConnectionConfig getConfig() { return c.getConfig(); }
            @Override public java.sql.Connection getRawJdbcConnection() throws java.sql.SQLException { return c.getRawJdbcConnection(); }
            @Override public boolean isValid(int timeout) { return c.isValid(timeout); }
            @Override public void close() {
                if (!returned.compareAndSet(false, true)) return;
                if (slot.closed) {
                    closeQuietly(c);
                } else {
                    synchronized (slot.idle) {
                        slot.idle.addFirst(c);
                    }
                }
                slot.permits.release();
            }
        };
    }

    private static String errorMessage(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null && (t.getMessage() == null || t instanceof java.util.concurrent.CompletionException)) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    private static final Map<String, String> lastErrors = new java.util.concurrent.ConcurrentHashMap<>();

    /** Error of the last failed read for a profile (consumed by the Explorer to show an error row). */
    public String takeError(String connectionId) {
        return connectionId == null ? null : lastErrors.remove(connectionId);
    }

    private static void reportError(ConnectionProfileDto profile, Throwable error) {
        String msg = errorMessage(error);
        lastErrors.put(profile.id(), msg);
        app.services.NotificationService.getInstance().showWarning("Lỗi đọc metadata · " + profile.name(), msg);
    }

    @FunctionalInterface
    private interface MetadataQueryFunction<T> {
        Result<List<T>> query(DatabaseProvider provider, DatabaseConnection conn);
    }
}

