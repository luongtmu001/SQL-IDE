package com.lltool.core.application.connection.mapper;

import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.domain.connection.model.ConnectionProfile;
import com.lltool.core.domain.connection.valueobject.*;

import java.time.Instant;

public final class ConnectionMapper {

    private ConnectionMapper() {}

    public static ConnectionProfileDto toDto(ConnectionProfile p) {
        if (p == null) return null;
        return new ConnectionProfileDto(
                p.getId().value(),
                p.getName().value(),
                p.getGroup(),
                p.getEnvironment(),
                p.isReadOnly(),
                p.getDatabaseType().name(),
                p.getHost(),
                p.getPort(),
                p.getDatabaseName(),
                p.getSqliteFilePath(),
                p.getUsername(),
                p.getAuthType().name(),
                p.getCredentialRef().value(),
                p.isSavePassword(),
                p.getTimeoutSeconds(),
                p.isTrustServerCertificate(),
                p.getProperties(),
                p.getCreatedAt().toString(),
                p.getUpdatedAt().toString()
        );
    }

    public static ConnectionProfile toDomain(ConnectionProfileDto dto) {
        if (dto == null) return null;
        return new ConnectionProfile(
                ConnectionId.of(dto.id()),
                ConnectionName.of(dto.name()),
                dto.group(),
                dto.environment(),
                dto.readOnly(),
                ConnectionDatabaseType.fromString(dto.databaseType()),
                dto.host(),
                dto.port(),
                dto.databaseName(),
                dto.sqliteFilePath(),
                dto.username(),
                AuthenticationType.fromString(dto.authType()),
                CredentialReference.of(dto.credentialRef()),
                dto.savePassword(),
                dto.timeoutSeconds(),
                dto.trustServerCertificate(),
                dto.properties(),
                Instant.parse(dto.createdAt()),
                Instant.parse(dto.updatedAt())
        );
    }
}

