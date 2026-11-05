package com.lltool.infrastructure.database.jdbc;

import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public class JdbcDatabaseConnection implements DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(JdbcDatabaseConnection.class);

    private final DatabaseConnectionConfig config;
    private final Connection connection;

    public JdbcDatabaseConnection(DatabaseConnectionConfig config, Connection connection) {
        this.config = Objects.requireNonNull(config);
        this.connection = Objects.requireNonNull(connection);
    }

    @Override
    public DatabaseConnectionConfig getConfig() {
        return config;
    }

    @Override
    public Connection getRawJdbcConnection() {
        return connection;
    }

    @Override
    public boolean isValid(int timeoutSeconds) {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(timeoutSeconds);
        } catch (SQLException e) {
            log.warn("Connection validity check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.debug("Closed connection for {}", config.name());
            }
        } catch (SQLException e) {
            log.error("Error closing database connection: {}", e.getMessage());
        }
    }
}

