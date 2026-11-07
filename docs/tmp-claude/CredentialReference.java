package com.lltool.core.domain.connection.valueobject;

import java.io.Serializable;
import java.util.Objects;

/**
 * Safe reference to an encrypted secret in the Secure Credential Vault (e.g. cred://&lt;uuid&gt;).
 * Prevents password leakage into connection profiles, logs, or UI state.
 */
public record CredentialReference(String value) implements Serializable {

    public static final String PREFIX = "cred://";
    private static final CredentialReference EMPTY = new CredentialReference("");

    public CredentialReference {
        value = value != null ? value.trim() : "";
    }

    public static CredentialReference empty() {
        return EMPTY;
    }

    public static CredentialReference of(String value) {
        if (value == null || value.isBlank()) return EMPTY;
        return new CredentialReference(value);
    }

    public boolean isPresent() {
        return value != null && !value.isBlank();
    }
}

