package app.framework.connection;

import com.lltool.core.application.connection.port.DatabaseConnectionTesterPort;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;
import com.lltool.core.domain.connection.model.ConnectionProfile;
import com.lltool.core.domain.connection.valueobject.ConnectionDatabaseType;
import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import com.lltool.infrastructure.database.api.DatabaseProvider;
import com.lltool.infrastructure.database.api.DatabaseProviderRegistry;
import com.lltool.infrastructure.database.api.DatabaseType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Desktop Adapter connecting Core DatabaseConnectionTesterPort to Infrastructure DatabaseProviderRegistry.
 */
public class DesktopConnectionTesterAdapter implements DatabaseConnectionTesterPort {

    private final DatabaseProviderRegistry providerRegistry;

    public DesktopConnectionTesterAdapter(DatabaseProviderRegistry providerRegistry) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry cannot be null");
    }

    @Override
    public Result<Boolean> test(ConnectionProfile profile, String resolvedPassword) {
        DatabaseType type = mapDatabaseType(profile.getDatabaseType());
        var optProvider = providerRegistry.getProvider(type);
        if (optProvider.isEmpty()) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "No database provider found for " + type));
        }

        DatabaseProvider provider = optProvider.get();
        Map<String, String> properties = new HashMap<>(profile.getProperties());
        if (profile.isTrustServerCertificate()) {
            properties.put("trustServerCertificate", "true");
        }
        // Windows authentication (SQL Server): tell the driver, otherwise it sends the empty user/password
        if (type == DatabaseType.SQLSERVER && "WINDOWS_AUTH".equals(String.valueOf(profile.getAuthType()))) {
            properties.put("integratedSecurity", "true");
        }
        if (profile.getTimeoutSeconds() > 0) {
            properties.put("loginTimeout", String.valueOf(profile.getTimeoutSeconds()));
            properties.put("connectTimeout", String.valueOf(profile.getTimeoutSeconds()));
        }

        String targetDatabase = profile.getDatabaseType() == ConnectionDatabaseType.SQLITE
                ? profile.getSqliteFilePath()
                : profile.getDatabaseName();

        DatabaseConnectionConfig config = new DatabaseConnectionConfig(
                profile.getId().value(),
                profile.getName().value(),
                type,
                profile.getHost(),
                profile.getPort(),
                targetDatabase,
                profile.getUsername(),
                profile.getCredentialRef() != null ? profile.getCredentialRef().value() : null,
                properties
        );

        return provider.testConnection(config, resolvedPassword);
    }

    private DatabaseType mapDatabaseType(ConnectionDatabaseType domainType) {
        if (domainType == null) {
            return DatabaseType.POSTGRESQL;
        }
        return switch (domainType) {
            case SQLITE -> DatabaseType.SQLITE;
            case SQLSERVER -> DatabaseType.SQLSERVER;
            case POSTGRESQL -> DatabaseType.POSTGRESQL;
        };
    }
}
