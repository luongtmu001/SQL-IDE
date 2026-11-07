package app.framework.connection;

import app.framework.bootstrap.DesktopBootstrap;
import app.framework.state.GlobalDesktopState;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import com.lltool.infrastructure.database.api.DatabaseProvider;
import com.lltool.infrastructure.database.api.DatabaseProviderRegistry;
import com.lltool.infrastructure.database.api.DatabaseType;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Manages active live database sessions and catalogs for the desktop client (Phase 6.2).
 */
public class ConnectionSessionManager {

    private static final Logger log = LoggerFactory.getLogger(ConnectionSessionManager.class);
    private static ConnectionSessionManager instance;

    private final ObjectProperty<ConnectionProfileDto> activeProfile = new SimpleObjectProperty<>(null);
    private final ObjectProperty<DatabaseConnection> activeConnection = new SimpleObjectProperty<>(null);
    private final StringProperty activeConnectionName = new SimpleStringProperty("");

    private ConnectionSessionManager() {
        activeProfile.addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                activeConnectionName.set(newVal.name());
                GlobalDesktopState.getInstance().setActiveConnection(newVal.name());
                GlobalDesktopState.getInstance().setActiveDialect(newVal.databaseType());
            } else {
                activeConnectionName.set("");
                GlobalDesktopState.getInstance().setActiveConnection("");
            }
        });
    }

    public static synchronized ConnectionSessionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionSessionManager();
        }
        return instance;
    }

    public ObjectProperty<ConnectionProfileDto> activeProfileProperty() {
        return activeProfile;
    }

    public ConnectionProfileDto getActiveProfile() {
        return activeProfile.get();
    }

    public ObjectProperty<DatabaseConnection> activeConnectionProperty() {
        return activeConnection;
    }

    public DatabaseConnection getActiveConnection() {
        return activeConnection.get();
    }

    public StringProperty activeConnectionNameProperty() {
        return activeConnectionName;
    }

    public boolean isConnected() {
        DatabaseConnection conn = activeConnection.get();
        return conn != null && conn.isValid(2);
    }

    /**
     * Connects to a database using the provided profile and optional explicit password.
     */
    public synchronized Result<DatabaseConnection> connect(ConnectionProfileDto profile, String explicitPassword) {
        Objects.requireNonNull(profile, "profile cannot be null");
        disconnect();

        DatabaseType type = mapDatabaseType(profile.databaseType());
        if (type == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED,
                    "Loại cơ sở dữ liệu \"" + profile.databaseType() + "\" chưa được hỗ trợ."));
        }
        DatabaseProviderRegistry registry = DesktopBootstrap.getInstance().getProviderRegistry();
        Optional<DatabaseProvider> optProvider = registry.getProvider(type);
        if (optProvider.isEmpty()) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "No database provider found for " + type));
        }

        String resolvedPassword = resolvePassword(profile, explicitPassword);
        DatabaseConnectionConfig config = toConfig(profile, type, profile.databaseName());

        Result<DatabaseConnection> connResult = optProvider.get().connect(config, resolvedPassword);
        if (connResult.isSuccess()) {
            DatabaseConnection conn = connResult.getDataOrThrow();
            activeConnection.set(conn);
            activeProfile.set(profile);
            log.info("Live connection established for profile '{}' ({})", profile.name(), type);
            return Result.success(conn);
        } else {
            log.warn("Failed to establish connection for profile '{}': {}", profile.name(), connResult.getError().orElse(null));
            return connResult;
        }
    }

    /**
     * Disconnects any currently active session.
     */
    public synchronized void disconnect() {
        DatabaseConnection existing = activeConnection.get();
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception e) {
                log.warn("Error closing existing connection: {}", e.getMessage());
            }
            activeConnection.set(null);
            activeProfile.set(null);
            log.info("Active database connection closed.");
        }
    }

    /**
     * Queries database names available on the server (for "Tải DBs" button).
     */
    public Result<List<String>> fetchDatabases(ConnectionProfileDto profile, String explicitPassword) {
        Objects.requireNonNull(profile, "profile cannot be null");
        DatabaseType type = mapDatabaseType(profile.databaseType());
        if (type == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED,
                    "Loại cơ sở dữ liệu \"" + profile.databaseType() + "\" chưa được hỗ trợ."));
        }

        if (type == DatabaseType.SQLITE) {
            String db = profile.sqliteFilePath() != null && !profile.sqliteFilePath().isBlank()
                    ? profile.sqliteFilePath()
                    : (profile.databaseName() != null ? profile.databaseName() : "main");
            return Result.success(List.of(db));
        }

        DatabaseProviderRegistry registry = DesktopBootstrap.getInstance().getProviderRegistry();
        Optional<DatabaseProvider> optProvider = registry.getProvider(type);
        if (optProvider.isEmpty()) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "No database provider found for " + type));
        }

        String resolvedPassword = resolvePassword(profile, explicitPassword);
        // For Postgres, connect to 'postgres' or configured db; for SQL Server, connect to 'master' or configured db
        String bootstrapDb = profile.databaseName() != null && !profile.databaseName().isBlank()
                ? profile.databaseName()
                : (type == DatabaseType.POSTGRESQL ? "postgres" : "master");

        DatabaseConnectionConfig config = toConfig(profile, type, bootstrapDb);

        Result<DatabaseConnection> connResult = optProvider.get().connect(config, resolvedPassword);
        if (connResult.isFailure()) {
            return Result.failure(connResult.getError().get());
        }

        List<String> databases = new ArrayList<>();
        try (DatabaseConnection conn = connResult.getDataOrThrow()) {
            Connection rawConn = conn.getRawJdbcConnection();
            if (type == DatabaseType.POSTGRESQL) {
                String sql = "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname;";
                try (Statement stmt = rawConn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        databases.add(rs.getString(1));
                    }
                }
            } else if (type == DatabaseType.SQLSERVER) {
                String sql = "SELECT name FROM sys.databases WHERE state = 0 ORDER BY name;";
                try (Statement stmt = rawConn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        databases.add(rs.getString(1));
                    }
                }
            }

            if (databases.isEmpty()) {
                // Fallback to JDBC metadata catalogs
                try (ResultSet rs = rawConn.getMetaData().getCatalogs()) {
                    while (rs.next()) {
                        databases.add(rs.getString(1));
                    }
                }
            }
            return Result.success(databases);
        } catch (Exception e) {
            log.error("Failed to fetch database names: {}", e.getMessage());
            return Result.failure(AppError.system("Failed to fetch databases: " + e.getMessage(), e));
        }
    }

    /** Schemas of {@code database} for a (possibly unsaved) profile — Connection Manager "↻ schema". */
    public Result<List<String>> fetchSchemas(ConnectionProfileDto profile, String database, String explicitPassword) {
        DatabaseType type = mapDatabaseType(profile.databaseType());
        if (type == DatabaseType.SQLITE) return Result.success(List.of("main"));
        Result<DatabaseConnection> res = openSession(profile, database, resolvePassword(profile, explicitPassword));
        if (res.isFailure()) return Result.failure(res.getError().get());
        try (DatabaseConnection conn = res.getDataOrThrow()) {
            var provider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
            if (provider.isEmpty()) return Result.success(List.of());
            String catalog = database != null && !database.isBlank() ? database : profile.databaseName();
            return provider.get().getSchemas(conn, catalog);
        } catch (Exception e) {
            return Result.failure(AppError.system("Failed to fetch schemas: " + e.getMessage(), e));
        }
    }

    private String resolvePassword(ConnectionProfileDto profile, String explicitPassword) {
        // Phase 8: also uses the in-memory DLG-002 password (was ignored before)
        return app.framework.session.CredentialResolver.resolve(profile, explicitPassword);
    }

    /**
     * Opens a new connection for {@code profile} on {@code database} (Phase 8). Read-only is applied
     * when the profile is read-only or every connection is (BackendFlags.readOnlyAll()).
     */
    public Result<DatabaseConnection> openSession(ConnectionProfileDto profile, String database, String password) {
        Objects.requireNonNull(profile, "profile cannot be null");
        DatabaseType type = mapDatabaseType(profile.databaseType());
        if (type == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED,
                    "Loại cơ sở dữ liệu \"" + profile.databaseType() + "\" chưa được hỗ trợ (SQL Server, PostgreSQL, SQLite)."));
        }
        Optional<DatabaseProvider> optProvider = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
        if (optProvider.isEmpty()) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "No database provider found for " + type));
        }
        String db = database != null && !database.isBlank() ? database : profile.databaseName();
        Result<DatabaseConnection> res = optProvider.get().connect(toConfig(profile, type, db), password);
        if (res.isSuccess()) {
            boolean readOnly = app.framework.backend.BackendFlags.readOnlyAll() || profile.readOnly();
            if (readOnly) applyReadOnly(res.getDataOrThrow(), type);
        }
        return res;
    }

    /** JDBC read-only hint + PostgreSQL READ ONLY session (the server then refuses any write). */
    public static void applyReadOnly(DatabaseConnection conn, DatabaseType type) {
        try {
            Connection raw = conn.getRawJdbcConnection();
            if (type != DatabaseType.SQLITE) {
                try {
                    raw.setReadOnly(true);
                } catch (Exception e) {
                    log.debug("setReadOnly not supported: {}", e.getMessage());
                }
            }
            if (type == DatabaseType.POSTGRESQL) {
                try (Statement st = raw.createStatement()) {
                    st.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY");
                }
            }
        } catch (Exception e) {
            log.warn("Could not make the session read-only: {}", e.getMessage());
        }
    }

    private DatabaseConnectionConfig toConfig(ConnectionProfileDto profile, DatabaseType type, String databaseTarget) {
        Map<String, String> properties = new HashMap<>();
        if (profile.properties() != null) {
            properties.putAll(profile.properties());
        }
        if (profile.trustServerCertificate()) {
            properties.put("trustServerCertificate", "true");
        }
        // Windows authentication (SQL Server): the driver must be told, otherwise it sends the (empty) user/password
        if ("WINDOWS_AUTH".equalsIgnoreCase(profile.authType()) && type == DatabaseType.SQLSERVER) {
            properties.put("integratedSecurity", "true");
        }
        if (profile.timeoutSeconds() > 0) {
            properties.put("loginTimeout", String.valueOf(profile.timeoutSeconds()));
            properties.put("connectTimeout", String.valueOf(profile.timeoutSeconds()));
        }

        String dbTarget = type == DatabaseType.SQLITE
                ? profile.sqliteFilePath()
                : databaseTarget;

        return new DatabaseConnectionConfig(
                profile.id() != null ? profile.id() : UUID.randomUUID().toString(),
                profile.name() != null ? profile.name() : "Connection",
                type,
                profile.host(),
                profile.port(),
                dbTarget,
                profile.username(),
                profile.credentialRef(),
                properties
        );
    }

    public static DatabaseType mapDatabaseType(String raw) {
        if (raw == null) return DatabaseType.POSTGRESQL;
        String s = raw.trim().toUpperCase().replace(" ", "").replace("-", "").replace("_", "");
        if (s.contains("SQLITE")) return DatabaseType.SQLITE;
        if (s.contains("SQLSERVER") || s.contains("MSSQL")) return DatabaseType.SQLSERVER;
        if (s.contains("POSTGRE") || s.equals("PG")) return DatabaseType.POSTGRESQL;
        // Phase 8: other engines (MySQL, Oracle…) have no provider; they used to fall through to PostgreSQL
        return null;
    }
}
