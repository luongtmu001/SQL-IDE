package com.lltool.core.application.connection.service;

import com.lltool.core.application.connection.command.*;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.connection.mapper.ConnectionMapper;
import com.lltool.core.application.connection.port.CredentialVaultPort;
import com.lltool.core.application.connection.port.DatabaseConnectionTesterPort;
import com.lltool.core.application.connection.query.GetConnectionDetailQuery;
import com.lltool.core.application.connection.query.GetConnectionListQuery;
import com.lltool.core.application.connection.usecase.*;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.event.EventPublisher;
import com.lltool.core.common.result.Result;
import com.lltool.core.common.time.TimeProvider;
import com.lltool.core.domain.connection.event.ConnectionCreatedDomainEvent;
import com.lltool.core.domain.connection.event.ConnectionDeletedDomainEvent;
import com.lltool.core.domain.connection.event.ConnectionUpdatedDomainEvent;
import com.lltool.core.domain.connection.model.ConnectionProfile;
import com.lltool.core.domain.connection.repository.ConnectionRepository;
import com.lltool.core.domain.connection.valueobject.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service coordinating connection CRUD, secure credential vaulting, and testing.
 */
public class ConnectionApplicationService implements
        CreateConnectionUseCase,
        UpdateConnectionUseCase,
        DeleteConnectionUseCase,
        DuplicateConnectionUseCase,
        GetConnectionDetailUseCase,
        GetConnectionListUseCase,
        TestConnectionDraftUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConnectionApplicationService.class);

    private final ConnectionRepository repository;
    private final CredentialVaultPort vaultPort;
    private final DatabaseConnectionTesterPort testerPort;
    private final EventPublisher eventPublisher;
    private final TimeProvider timeProvider;

    public ConnectionApplicationService(
            ConnectionRepository repository,
            CredentialVaultPort vaultPort,
            DatabaseConnectionTesterPort testerPort,
            EventPublisher eventPublisher,
            TimeProvider timeProvider) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.vaultPort = vaultPort; // Optional in purely mocked / in-memory environments
        this.testerPort = testerPort;
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
    }

    @Override
    public Result<ConnectionProfileDto> execute(CreateConnectionCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<ConnectionName> nameRes = ConnectionName.create(command.name());
        if (nameRes.isFailure()) return Result.failure(nameRes.getError().get());

        ConnectionName name = nameRes.getDataOrThrow();
        if (repository.existsByName(name)) {
            return Result.failure(AppError.business(AppError.CONNECTION_ALREADY_EXISTS,
                    "A connection with name '" + name.value() + "' already exists."));
        }

        Instant now = timeProvider.now();
        ConnectionId id = ConnectionId.generate();
        ConnectionDatabaseType dbType = ConnectionDatabaseType.fromString(command.databaseType());
        AuthenticationType authType = AuthenticationType.fromString(command.authType());

        // Secure password vaulting
        CredentialReference credRef = CredentialReference.empty();
        if (command.savePassword() && command.password() != null && !command.password().isBlank() && vaultPort != null) {
            String token = vaultPort.storeSecret(command.password());
            credRef = CredentialReference.of(token);
        }

        ConnectionProfile profile = ConnectionProfile.create(
                id,
                name,
                command.group(),
                command.environment(),
                command.readOnly(),
                dbType,
                command.host(),
                command.port(),
                command.databaseName(),
                command.sqliteFilePath(),
                command.username(),
                authType,
                credRef,
                command.savePassword(),
                command.timeoutSeconds(),
                command.trustServerCertificate(),
                command.properties(),
                now
        );

        repository.save(profile);
        log.info("Created connection profile: {} (id={}, type={})", name.value(), id.value(), dbType);

        eventPublisher.publish(new ConnectionCreatedDomainEvent(id, name, dbType, now));

        return Result.success(ConnectionMapper.toDto(profile));
    }

    @Override
    public Result<ConnectionProfileDto> execute(UpdateConnectionCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<ConnectionId> idRes = ConnectionId.create(command.connectionId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        Result<ConnectionName> nameRes = ConnectionName.create(command.name());
        if (nameRes.isFailure()) return Result.failure(nameRes.getError().get());

        ConnectionId id = idRes.getDataOrThrow();
        ConnectionName newName = nameRes.getDataOrThrow();

        Optional<ConnectionProfile> existing = repository.findByName(newName);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            return Result.failure(AppError.business(AppError.CONNECTION_ALREADY_EXISTS,
                    "A connection with name '" + newName.value() + "' already exists."));
        }

        Optional<ConnectionProfile> profileOpt = repository.findById(id);
        if (profileOpt.isEmpty()) {
            return Result.failure(AppError.resourceNotFound(AppError.CONNECTION_NOT_FOUND,
                    "Connection not found with id: " + command.connectionId()));
        }

        ConnectionProfile profile = profileOpt.get();
        Instant now = timeProvider.now();
        ConnectionDatabaseType dbType = ConnectionDatabaseType.fromString(command.databaseType());
        AuthenticationType authType = AuthenticationType.fromString(command.authType());

        // Update password in vault if new password provided or toggled
        CredentialReference credRef = profile.getCredentialRef();
        if (command.savePassword()) {
            if (command.password() != null && !command.password().isBlank() && vaultPort != null) {
                if (credRef.isPresent()) {
                    vaultPort.deleteSecret(credRef.value());
                }
                String token = vaultPort.storeSecret(command.password());
                credRef = CredentialReference.of(token);
            }
        } else {
            if (credRef.isPresent() && vaultPort != null) {
                vaultPort.deleteSecret(credRef.value());
            }
            credRef = CredentialReference.empty();
        }

        profile.update(
                newName,
                command.group(),
                command.environment(),
                command.readOnly(),
                dbType,
                command.host(),
                command.port(),
                command.databaseName(),
                command.sqliteFilePath(),
                command.username(),
                authType,
                credRef,
                command.savePassword(),
                command.timeoutSeconds(),
                command.trustServerCertificate(),
                command.properties(),
                now
        );

        repository.save(profile);
        log.info("Updated connection profile: {} (id={})", newName.value(), id.value());

        eventPublisher.publish(new ConnectionUpdatedDomainEvent(id, newName, dbType, now));

        return Result.success(ConnectionMapper.toDto(profile));
    }

    @Override
    public Result<Void> execute(DeleteConnectionCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<ConnectionId> idRes = ConnectionId.create(command.connectionId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        ConnectionId id = idRes.getDataOrThrow();
        Optional<ConnectionProfile> profileOpt = repository.findById(id);
        if (profileOpt.isEmpty()) {
            return Result.failure(AppError.resourceNotFound(AppError.CONNECTION_NOT_FOUND,
                    "Connection not found with id: " + command.connectionId()));
        }

        ConnectionProfile profile = profileOpt.get();
        if (profile.getCredentialRef().isPresent() && vaultPort != null) {
            vaultPort.deleteSecret(profile.getCredentialRef().value());
        }

        repository.delete(id);
        log.info("Deleted connection profile: {}", id.value());

        eventPublisher.publish(new ConnectionDeletedDomainEvent(id, timeProvider.now()));

        return Result.success(null);
    }

    @Override
    public Result<ConnectionProfileDto> execute(DuplicateConnectionCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        Result<ConnectionId> idRes = ConnectionId.create(command.sourceConnectionId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        Result<ConnectionName> nameRes = ConnectionName.create(command.newName());
        if (nameRes.isFailure()) return Result.failure(nameRes.getError().get());

        Optional<ConnectionProfile> sourceOpt = repository.findById(idRes.getDataOrThrow());
        if (sourceOpt.isEmpty()) {
            return Result.failure(AppError.resourceNotFound(AppError.CONNECTION_NOT_FOUND,
                    "Source connection not found: " + command.sourceConnectionId()));
        }

        ConnectionName newName = nameRes.getDataOrThrow();
        if (repository.existsByName(newName)) {
            return Result.failure(AppError.business(AppError.CONNECTION_ALREADY_EXISTS,
                    "A connection with name '" + newName.value() + "' already exists."));
        }

        ConnectionProfile src = sourceOpt.get();
        Instant now = timeProvider.now();
        ConnectionId newId = ConnectionId.generate();

        CredentialReference newCredRef = CredentialReference.empty();
        if (src.getCredentialRef().isPresent() && vaultPort != null) {
            Optional<String> secretOpt = vaultPort.getSecret(src.getCredentialRef().value());
            if (secretOpt.isPresent()) {
                String token = vaultPort.storeSecret(secretOpt.get());
                newCredRef = CredentialReference.of(token);
            }
        }

        ConnectionProfile copy = ConnectionProfile.create(
                newId,
                newName,
                src.getGroup(),
                src.getEnvironment(),
                src.isReadOnly(),
                src.getDatabaseType(),
                src.getHost(),
                src.getPort(),
                src.getDatabaseName(),
                src.getSqliteFilePath(),
                src.getUsername(),
                src.getAuthType(),
                newCredRef,
                src.isSavePassword(),
                src.getTimeoutSeconds(),
                src.isTrustServerCertificate(),
                src.getProperties(),
                now
        );

        repository.save(copy);
        log.info("Duplicated connection {} as {}", src.getId().value(), newId.value());

        eventPublisher.publish(new ConnectionCreatedDomainEvent(newId, newName, copy.getDatabaseType(), now));

        return Result.success(ConnectionMapper.toDto(copy));
    }

    @Override
    public Result<Boolean> execute(TestConnectionDraftCommand command) {
        if (command == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Command cannot be null"));
        }

        if (testerPort == null) {
            return Result.success(true); // Mock fallback
        }

        ConnectionDatabaseType dbType = ConnectionDatabaseType.fromString(command.databaseType());
        AuthenticationType authType = AuthenticationType.fromString(command.authType());

        ConnectionProfile draft = ConnectionProfile.create(
                ConnectionId.generate(),
                ConnectionName.of(command.name() != null && !command.name().isBlank() ? command.name() : "Draft"),
                "Draft",
                "DEV",
                false,
                dbType,
                command.host(),
                command.port(),
                command.databaseName(),
                command.sqliteFilePath(),
                command.username(),
                authType,
                CredentialReference.empty(),
                false,
                command.timeoutSeconds(),
                command.trustServerCertificate(),
                command.properties(),
                timeProvider.now()
        );

        return testerPort.test(draft, command.password());
    }

    @Override
    public Result<ConnectionProfileDto> execute(GetConnectionDetailQuery query) {
        if (query == null) {
            return Result.failure(AppError.validation(AppError.VALIDATION_FAILED, "Query cannot be null"));
        }

        Result<ConnectionId> idRes = ConnectionId.create(query.connectionId());
        if (idRes.isFailure()) return Result.failure(idRes.getError().get());

        return repository.findById(idRes.getDataOrThrow())
                .map(ConnectionMapper::toDto)
                .map(Result::success)
                .orElseGet(() -> Result.failure(AppError.resourceNotFound(AppError.CONNECTION_NOT_FOUND,
                        "Connection not found with id: " + query.connectionId())));
    }

    @Override
    public Result<List<ConnectionProfileDto>> execute(GetConnectionListQuery query) {
        List<ConnectionProfileDto> dtos = repository.findAll().stream()
                .map(ConnectionMapper::toDto)
                .toList();
        return Result.success(dtos);
    }
}

