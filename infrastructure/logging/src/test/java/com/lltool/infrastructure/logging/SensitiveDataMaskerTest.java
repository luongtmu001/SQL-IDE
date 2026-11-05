package com.lltool.infrastructure.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataMaskerTest {

    @Test
    @DisplayName("SensitiveDataMasker masks passwords in text, JSON and connection strings")
    void testMasking() {
        String input1 = "Connecting with user=admin;password=SecretPassword123;host=localhost";
        String masked1 = SensitiveDataMasker.mask(input1);
        assertFalse(masked1.contains("SecretPassword123"));
        assertTrue(masked1.contains("password=******"));

        String input2 = "apiKey: 'abc-xyz-12345', token: 'jwt.token.here'";
        String masked2 = SensitiveDataMasker.mask(input2);
        assertFalse(masked2.contains("abc-xyz-12345"));
        assertFalse(masked2.contains("jwt.token.here"));
    }
}

