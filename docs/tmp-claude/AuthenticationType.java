package com.lltool.core.domain.connection.valueobject;

public enum AuthenticationType {
    PASSWORD,
    NONE,
    WINDOWS_AUTH;

    public static AuthenticationType fromString(String raw) {
        if (raw == null) return PASSWORD;
        String s = raw.trim().toUpperCase();
        if (s.contains("WINDOWS") || s.contains("INTEGRATED") || s.contains("TRUSTED")) {
            return WINDOWS_AUTH;
        }
        if (s.contains("NONE")) {
            return NONE;
        }
        return PASSWORD;
    }
}

