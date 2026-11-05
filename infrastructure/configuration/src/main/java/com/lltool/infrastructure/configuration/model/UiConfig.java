package com.lltool.infrastructure.configuration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UiConfig(
        String theme,
        double editorFontSize,
        String density,
        boolean showLineNumbers,
        boolean wordWrap
) {
    public static UiConfig defaultUiConfig() {
        return new UiConfig("Primer Dark", 13.0, "compact", true, false);
    }
}

