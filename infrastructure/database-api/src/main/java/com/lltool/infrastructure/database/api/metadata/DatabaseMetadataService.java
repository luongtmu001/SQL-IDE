package com.lltool.infrastructure.database.api.metadata;

import com.lltool.core.common.result.Result;
import com.lltool.infrastructure.database.api.DatabaseConnection;

import java.util.List;

/**
 * Metadata retrieval service interface for database providers (Phase 6.3).
 * Decouples controllers from direct metadata queries.
 */
public interface DatabaseMetadataService {

    Result<List<String>> getCatalogs(DatabaseConnection connection);

    Result<List<String>> getSchemas(DatabaseConnection connection, String catalog);

    Result<List<TableMetadata>> getTables(DatabaseConnection connection, String catalog, String schema);

    Result<List<TableMetadata>> getViews(DatabaseConnection connection, String catalog, String schema);

    Result<List<ColumnMetadata>> getColumns(DatabaseConnection connection, String catalog, String schema, String table);

    Result<List<RoutineMetadata>> getRoutines(DatabaseConnection connection, String catalog, String schema);

    Result<List<IndexMetadata>> getIndexes(DatabaseConnection connection, String catalog, String schema, String table);

    Result<List<TriggerMetadata>> getTriggers(DatabaseConnection connection, String catalog, String schema, String table);

    default Result<List<TableMetadata>> getMaterializedViews(DatabaseConnection connection, String catalog, String schema) {
        return Result.success(java.util.Collections.emptyList());
    }

    default Result<List<UserTypeMetadata>> getUserDefinedTypes(DatabaseConnection connection, String catalog, String schema) {
        return Result.success(java.util.Collections.emptyList());
    }

    default Result<List<KeyMetadata>> getKeys(DatabaseConnection connection, String catalog, String schema, String table) {
        return Result.success(java.util.Collections.emptyList());
    }

    default Result<List<ConstraintMetadata>> getConstraints(DatabaseConnection connection, String catalog, String schema, String table) {
        return Result.success(java.util.Collections.emptyList());
    }
}

