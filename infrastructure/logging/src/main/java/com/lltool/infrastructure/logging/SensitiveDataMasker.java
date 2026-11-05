package com.lltool.infrastructure.logging;

import java.util.regex.Pattern;

/**
 * Sensitive Data Masking Utility (Phase 3.3).
 * Prevents passwords, tokens, API keys, and connection credentials from leaking into log files.
 */
public final class SensitiveDataMasker {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|pwd|secret|token|apikey)\\s*[:=]\\s*['\"]?([^'\"\\s;,]+)['\"]?");
    private static final Pattern JDBC_PASSWORD_PATTERN = Pattern.compile("(?i)(password|pwd)=([^;&]+)");

    private SensitiveDataMasker() {}

    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String masked = PASSWORD_PATTERN.matcher(input).replaceAll("$1=******");
        masked = JDBC_PASSWORD_PATTERN.matcher(masked).replaceAll("$1=******");
        return masked;
    }
}

