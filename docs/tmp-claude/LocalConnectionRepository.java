package com.lltool.infrastructure.storage.local;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lltool.core.domain.connection.model.ConnectionProfile;
import com.lltool.core.domain.connection.repository.ConnectionRepository;
import com.lltool.core.domain.connection.valueobject.*;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import com.lltool.infrastructure.filesystem.AtomicFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Local File System JSON Adapter for ConnectionRepository (Phase 6.2).
 * Stores each connection profile in ~/.lltool/data/connections/{connectionId}.json.
 */
public class LocalConnectionRepository implements ConnectionRepository {

    private static final Logger log = LoggerFactory.getLogger(LocalConnectionRepository.class);

    private final Path connectionsDir;
    private final ObjectMapper mapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConnectionEntityRecord(
            String id,
            String name,
            String group,
            String environment,
            boolean readOnly,
            String databaseType,
            String host,
            int port,
            String databaseName,
            String sqliteFilePath,
            String username,
            String authType,
            String credentialRef,
            boolean savePassword,
            int timeoutSeconds,
            boolean trustServerCertificate,
            Map<String, String> properties,
            String createdAt,
            String updatedAt
    ) {}

    public LocalConnectionRepository(AppDirectoryManager directoryManager) {
        this.connectionsDir = directoryManager.getDataDir().resolve("connections");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(connectionsDir);
        } catch (IOException e) {
            log.error("Failed to create connections directory: {}", connectionsDir, e);
        }
    }

    @Override
    public Optional<ConnectionProfile> findById(ConnectionId id) {
        if (id == null) return Optional.empty();
        Path file = getFile(id);
        if (!Files.exists(file)) return Optional.empty();

        try {
            ConnectionEntityRecord record = mapper.readValue(file.toFile(), ConnectionEntityRecord.class);
            return Optional.of(toDomain(record));
        } catch (IOException e) {
            log.error("Failed to read connection file {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<ConnectionProfile> findByName(ConnectionName name) {
        if (name == null) return Optional.empty();
        return findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst();
    }

    @Override
    public List<ConnectionProfile> findAll() {
        if (!Files.exists(connectionsDir)) return Collections.emptyList();

        List<ConnectionProfile> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(connectionsDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    ConnectionEntityRecord record = mapper.readValue(p.toFile(), ConnectionEntityRecord.class);
                    list.add(toDomain(record));
                } catch (IOException e) {
                    log.warn("Corrupt connection file {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Failed to list connections from {}: {}", connectionsDir, e.getMessage());
        }
        return list;
    }

    @Override
    public void save(ConnectionProfile profile) {
        Objects.requireNonNull(profile, "profile cannot be null");
        Path file = getFile(profile.getId());

        ConnectionEntityRecord record = new ConnectionEntityRecord(
                profile.getId().value(),
                profile.getName().value(),
                profile.getGroup(),
                profile.getEnvironment(),
                profile.isReadOnly(),
                profile.getDatabaseType().name(),
                profile.getHost(),
                profile.getPort(),
                profile.getDatabaseName(),
                profile.getSqliteFilePath(),
                profile.getUsername(),
                profile.getAuthType().name(),
                profile.getCredentialRef().value(),
                profile.isSavePassword(),
                profile.getTimeoutSeconds(),
                profile.isTrustServerCertificate(),
                profile.getProperties(),
                profile.getCreatedAt().toString(),
                profile.getUpdatedAt().toString()
        );

        try {
            String json = mapper.writeValueAsString(record);
            AtomicFileWriter.writeString(file, json);
            log.debug("Saved connection {} to {}", profile.getId().value(), file);
        } catch (IOException e) {
            log.error("Failed to write connection {}: {}", file, e.getMessage(), e);
            throw new RuntimeException("Cannot persist connection to local storage", e);
        }
    }

    @Override
    public void delete(ConnectionId id) {
        if (id == null) return;
        Path file = getFile(id);
        try {
            Files.deleteIfExists(file);
            log.debug("Deleted connection file {}", file);
        } catch (IOException e) {
            log.error("Failed to delete connection file {}: {}", file, e.getMessage());
        }
    }

    @Override
    public boolean existsById(ConnectionId id) {
        return id != null && Files.exists(getFile(id));
    }

    @Override
    public boolean existsByName(ConnectionName name) {
        return findByName(name).isPresent();
    }

    private Path getFile(ConnectionId id) {
        return connectionsDir.resolve(id.value() + ".json");
    }

    private ConnectionProfile toDomain(ConnectionEntityRecord r) {
        return new ConnectionProfile(
                ConnectionId.of(r.id()),
                ConnectionName.of(r.name()),
                r.group(),
                r.environment(),
                r.readOnly(),
                ConnectionDatabaseType.fromString(r.databaseType()),
                r.host(),
                r.port(),
                r.databaseName(),
                r.sqliteFilePath(),
                r.username(),
                AuthenticationType.fromString(r.authType()),
                CredentialReference.of(r.credentialRef()),
                r.savePassword(),
                r.timeoutSeconds(),
                r.trustServerCertificate(),
                r.properties() != null ? r.properties() : Map.of(),
                Instant.parse(r.createdAt()),
                Instant.parse(r.updatedAt())
        );
    }
}

