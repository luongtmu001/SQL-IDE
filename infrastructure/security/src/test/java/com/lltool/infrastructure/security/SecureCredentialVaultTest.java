package com.lltool.infrastructure.security;

import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SecureCredentialVaultTest {

    @Test
    @DisplayName("SecureCredentialVault encrypts secret and returns ref URI that can be resolved")
    void testStoreAndResolveSecret(@TempDir Path tempDir) {
        AppDirectoryManager dirManager = AppDirectoryManager.createIsolated(tempDir);
        SecureCredentialVault vault = new SecureCredentialVault(dirManager, "master-test-password-123");

        String secret = "SuperSecretDbPassword!@#$";
        String ref = vault.storeSecret(secret);

        assertTrue(ref.startsWith("cred://"));
        assertNotEquals(secret, ref);

        // Resolve
        String resolved = vault.resolveSecret(ref);
        assertEquals(secret, resolved);

        // Non-reference strings pass through unchanged
        assertEquals("plainPassword", vault.resolveSecret("plainPassword"));

        // New vault instance reads encrypted file and decrypts correctly
        SecureCredentialVault reloadedVault = new SecureCredentialVault(dirManager, "master-test-password-123");
        assertEquals(secret, reloadedVault.resolveSecret(ref));
    }
}

