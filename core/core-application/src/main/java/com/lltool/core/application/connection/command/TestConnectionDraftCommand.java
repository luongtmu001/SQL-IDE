package com.lltool.core.application.connection.command;

import java.util.Map;

public record TestConnectionDraftCommand(
        String name,
        String databaseType,
        String host,
        int port,
        String databaseName,
        String sqliteFilePath,
        String username,
        String authType,
        String password,
        int timeoutSeconds,
        boolean trustServerCertificate,
        Map<String, String> properties
) {}

