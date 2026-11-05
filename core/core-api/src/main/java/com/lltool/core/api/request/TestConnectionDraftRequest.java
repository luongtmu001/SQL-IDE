package com.lltool.core.api.request;

import java.util.Map;

public record TestConnectionDraftRequest(
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

