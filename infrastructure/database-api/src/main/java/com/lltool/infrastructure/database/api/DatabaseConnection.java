package com.lltool.infrastructure.database.api;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Open Database Connection abstraction.
 */
public interface DatabaseConnection extends AutoCloseable {

    DatabaseConnectionConfig getConfig();

    Connection getRawJdbcConnection() throws SQLException;

    boolean isValid(int timeoutSeconds);

    @Override
    void close();
}

