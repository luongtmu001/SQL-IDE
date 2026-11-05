package com.lltool.core.domain.connection.model;

import com.lltool.core.domain.connection.valueobject.*;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Database Connection Profile Aggregate Root (Phase 6.2).
 * Represents stored configuration for connecting to a database.
 * NEVER holds plaintext passwords - only secure credential references.
 */
public class ConnectionProfile {

    private final ConnectionId id;
    private ConnectionName name;
    private String group;
    private String environment;
    private boolean readOnly;
    private ConnectionDatabaseType databaseType;
    private String host;
    private int port;
    private String databaseName;
    private String sqliteFilePath;
    private String username;
    private AuthenticationType authType;
    private CredentialReference credentialRef;
    private boolean savePassword;
    private int timeoutSeconds;
    private boolean trustServerCertificate;
    private Map<String, String> properties;
    private final Instant createdAt;
    private Instant updatedAt;

    public ConnectionProfile(
            ConnectionId id,
            ConnectionName name,
            String group,
            String environment,
            boolean readOnly,
            ConnectionDatabaseType databaseType,
            String host,
            int port,
            String databaseName,
            String sqliteFilePath,
            String username,
            AuthenticationType authType,
            CredentialReference credentialRef,
            boolean savePassword,
            int timeoutSeconds,
            boolean trustServerCertificate,
            Map<String, String> properties,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "ConnectionId cannot be null");
        this.name = Objects.requireNonNull(name, "ConnectionName cannot be null");
        this.group = group != null && !group.isBlank() ? group.trim() : "Default";
        this.environment = environment != null && !environment.isBlank() ? environment.trim().toUpperCase() : "DEV";
        this.readOnly = readOnly;
        this.databaseType = Objects.requireNonNull(databaseType, "databaseType cannot be null");
        this.host = host != null ? host : "localhost";
        this.port = port > 0 ? port : databaseType.getDefaultPort();
        this.databaseName = databaseName != null ? databaseName : "";
        this.sqliteFilePath = sqliteFilePath != null ? sqliteFilePath : "";
        this.username = username != null ? username : "";
        this.authType = authType != null ? authType : AuthenticationType.PASSWORD;
        this.credentialRef = credentialRef != null ? credentialRef : CredentialReference.empty();
        this.savePassword = savePassword;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 15;
        this.trustServerCertificate = trustServerCertificate;
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public static ConnectionProfile create(
            ConnectionId id,
            ConnectionName name,
            String group,
            String environment,
            boolean readOnly,
            ConnectionDatabaseType databaseType,
            String host,
            int port,
            String databaseName,
            String sqliteFilePath,
            String username,
            AuthenticationType authType,
            CredentialReference credentialRef,
            boolean savePassword,
            int timeoutSeconds,
            boolean trustServerCertificate,
            Map<String, String> properties,
            Instant now) {
        return new ConnectionProfile(
                id, name, group, environment, readOnly, databaseType, host, port, databaseName, sqliteFilePath,
                username, authType, credentialRef, savePassword, timeoutSeconds, trustServerCertificate, properties, now, now
        );
    }

    public void update(
            ConnectionName newName,
            String newGroup,
            String newEnv,
            boolean newReadOnly,
            ConnectionDatabaseType newType,
            String newHost,
            int newPort,
            String newDatabaseName,
            String newSqliteFilePath,
            String newUsername,
            AuthenticationType newAuthType,
            CredentialReference newCredRef,
            boolean newSavePassword,
            int newTimeoutSeconds,
            boolean newTrustCert,
            Map<String, String> newProperties,
            Instant now) {
        this.name = Objects.requireNonNull(newName);
        this.group = newGroup != null && !newGroup.isBlank() ? newGroup.trim() : "Default";
        this.environment = newEnv != null && !newEnv.isBlank() ? newEnv.trim().toUpperCase() : "DEV";
        this.readOnly = newReadOnly;
        this.databaseType = Objects.requireNonNull(newType);
        this.host = newHost != null ? newHost : "localhost";
        this.port = newPort > 0 ? newPort : newType.getDefaultPort();
        this.databaseName = newDatabaseName != null ? newDatabaseName : "";
        this.sqliteFilePath = newSqliteFilePath != null ? newSqliteFilePath : "";
        this.username = newUsername != null ? newUsername : "";
        this.authType = newAuthType != null ? newAuthType : AuthenticationType.PASSWORD;
        this.credentialRef = newCredRef != null ? newCredRef : CredentialReference.empty();
        this.savePassword = newSavePassword;
        this.timeoutSeconds = newTimeoutSeconds > 0 ? newTimeoutSeconds : 15;
        this.trustServerCertificate = newTrustCert;
        this.properties = newProperties != null ? Map.copyOf(newProperties) : Map.of();
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void updateCredentialRef(CredentialReference newRef, Instant now) {
        this.credentialRef = newRef != null ? newRef : CredentialReference.empty();
        this.updatedAt = Objects.requireNonNull(now);
    }

    // Getters
    public ConnectionId getId() {
        return id;
    }

    public ConnectionName getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public ConnectionDatabaseType getDatabaseType() {
        return databaseType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getSqliteFilePath() {
        return sqliteFilePath;
    }

    public String getUsername() {
        return username;
    }

    public AuthenticationType getAuthType() {
        return authType;
    }

    public CredentialReference getCredentialRef() {
        return credentialRef;
    }

    public boolean isSavePassword() {
        return savePassword;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public boolean isTrustServerCertificate() {
        return trustServerCertificate;
    }

    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionProfile that = (ConnectionProfile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ConnectionProfile{id=" + id.value() + ", name=" + name.value() + ", type=" + databaseType + "}";
    }
}
