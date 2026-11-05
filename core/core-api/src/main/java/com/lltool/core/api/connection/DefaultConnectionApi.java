package com.lltool.core.api.connection;

import com.lltool.core.api.request.CreateConnectionRequest;
import com.lltool.core.api.request.TestConnectionDraftRequest;
import com.lltool.core.api.request.UpdateConnectionRequest;
import com.lltool.core.application.connection.command.*;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.connection.query.GetConnectionDetailQuery;
import com.lltool.core.application.connection.query.GetConnectionListQuery;
import com.lltool.core.application.connection.usecase.*;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;

import java.util.List;
import java.util.Objects;

/**
 * Standard implementation of ConnectionApi delegating requests to Use Cases.
 */
public class DefaultConnectionApi implements ConnectionApi {

    private final CreateConnectionUseCase createUseCase;
    private final UpdateConnectionUseCase updateUseCase;
    private final DeleteConnectionUseCase deleteUseCase;
    private final DuplicateConnectionUseCase duplicateUseCase;
    private final GetConnectionDetailUseCase detailUseCase;
    private final GetConnectionListUseCase listUseCase;
    private final TestConnectionDraftUseCase testDraftUseCase;

    public DefaultConnectionApi(
            CreateConnectionUseCase createUseCase,
            UpdateConnectionUseCase updateUseCase,
            DeleteConnectionUseCase deleteUseCase,
            DuplicateConnectionUseCase duplicateUseCase,
            GetConnectionDetailUseCase detailUseCase,
            GetConnectionListUseCase listUseCase,
            TestConnectionDraftUseCase testDraftUseCase) {
        this.createUseCase = Objects.requireNonNull(createUseCase);
        this.updateUseCase = Objects.requireNonNull(updateUseCase);
        this.deleteUseCase = Objects.requireNonNull(deleteUseCase);
        this.duplicateUseCase = Objects.requireNonNull(duplicateUseCase);
        this.detailUseCase = Objects.requireNonNull(detailUseCase);
        this.listUseCase = Objects.requireNonNull(listUseCase);
        this.testDraftUseCase = Objects.requireNonNull(testDraftUseCase);
    }

    @Override
    public Result<ConnectionProfileDto> createConnection(CreateConnectionRequest request) {
        if (request == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Request cannot be null"));
        }
        return createUseCase.execute(new CreateConnectionCommand(
                request.name(),
                request.group(),
                request.environment(),
                request.readOnly(),
                request.databaseType(),
                request.host(),
                request.port(),
                request.databaseName(),
                request.sqliteFilePath(),
                request.username(),
                request.authType(),
                request.password(),
                request.savePassword(),
                request.timeoutSeconds(),
                request.trustServerCertificate(),
                request.properties()
        ));
    }

    @Override
    public Result<ConnectionProfileDto> updateConnection(UpdateConnectionRequest request) {
        if (request == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Request cannot be null"));
        }
        return updateUseCase.execute(new UpdateConnectionCommand(
                request.connectionId(),
                request.name(),
                request.group(),
                request.environment(),
                request.readOnly(),
                request.databaseType(),
                request.host(),
                request.port(),
                request.databaseName(),
                request.sqliteFilePath(),
                request.username(),
                request.authType(),
                request.password(),
                request.savePassword(),
                request.timeoutSeconds(),
                request.trustServerCertificate(),
                request.properties()
        ));
    }

    @Override
    public Result<Void> deleteConnection(String connectionId) {
        return deleteUseCase.execute(new DeleteConnectionCommand(connectionId));
    }

    @Override
    public Result<ConnectionProfileDto> duplicateConnection(String sourceConnectionId, String newName) {
        return duplicateUseCase.execute(new DuplicateConnectionCommand(sourceConnectionId, newName));
    }

    @Override
    public Result<ConnectionProfileDto> getConnection(String connectionId) {
        return detailUseCase.execute(new GetConnectionDetailQuery(connectionId));
    }

    @Override
    public Result<List<ConnectionProfileDto>> getConnections() {
        return listUseCase.execute(GetConnectionListQuery.all());
    }

    @Override
    public Result<Boolean> testConnection(TestConnectionDraftRequest request) {
        if (request == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Request cannot be null"));
        }
        return testDraftUseCase.execute(new TestConnectionDraftCommand(
                request.name(),
                request.databaseType(),
                request.host(),
                request.port(),
                request.databaseName(),
                request.sqliteFilePath(),
                request.username(),
                request.authType(),
                request.password(),
                request.timeoutSeconds(),
                request.trustServerCertificate(),
                request.properties()
        ));
    }
}

