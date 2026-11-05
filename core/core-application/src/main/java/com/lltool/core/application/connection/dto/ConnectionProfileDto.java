package com.lltool.core.application.connection.dto;

import java.util.Map;

public record ConnectionProfileDto(
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

