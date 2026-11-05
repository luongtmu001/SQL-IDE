package com.lltool.infrastructure.database.api;

import com.lltool.core.common.result.Result;
import com.lltool.infrastructure.database.api.metadata.DatabaseMetadataService;

/**
 * Strategy interface for pluggable Database Providers (Phase 3.6, 3.7 & Phase 6.3).
 */
public interface DatabaseProvider extends DatabaseMetadataService {

    DatabaseType getDatabaseType();

    String buildJdbcUrl(DatabaseConnectionConfig config);

    Result<DatabaseConnection> connect(DatabaseConnectionConfig config, String resolvedPassword);

    Result<Boolean> testConnection(DatabaseConnectionConfig config, String resolvedPassword);
}

