package com.lltool.infrastructure.configuration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AppConfig(
        String appName,
        String environment,
        boolean autoSave,
        int autoSaveIntervalSeconds
) {
    public static AppConfig defaultAppConfig() {
        return new AppConfig("SQL IDE", "production", true, 30);
    }
}

