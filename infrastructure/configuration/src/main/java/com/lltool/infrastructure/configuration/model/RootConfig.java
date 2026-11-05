package com.lltool.infrastructure.configuration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RootConfig(
        int configVersion,
        AppConfig app,
        UiConfig ui
) {
    public static RootConfig defaultRootConfig() {
        return new RootConfig(1, AppConfig.defaultAppConfig(), UiConfig.defaultUiConfig());
    }
}

