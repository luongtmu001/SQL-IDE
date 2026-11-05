package com.lltool.core.application.connection.command;

import java.util.Map;

public record UpdateConnectionCommand(
        String connectionId,
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
        String password,
        boolean savePassword,
        int timeoutSeconds,
        boolean trustServerCertificate,
        Map<String, String> properties
) {}

