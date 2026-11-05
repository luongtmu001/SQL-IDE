package com.lltool.infrastructure.security;

import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import com.lltool.infrastructure.filesystem.AtomicFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure Credential Storage & Secret Vault (Phase 3.5).
 * Encrypts passwords and sensitive secrets using AES-256-GCM.
 * Replaces plain-text passwords with reference URIs: 'cred://<uuid>'.
 */
public class SecureCredentialVault {

    private static final Logger log = LoggerFactory.getLogger(SecureCredentialVault.class);
    private static final String VAULT_FILENAME = "vault.enc";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final Path vaultFile;
    private final SecretKey masterKey;
    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    public SecureCredentialVault(AppDirectoryManager directoryManager) {
        this(directoryManager, "lltool-default-app-entropy");
    }

    public SecureCredentialVault(AppDirectoryManager directoryManager, String masterSecret) {
        this.vaultFile = directoryManager.getConfigDir().resolve(VAULT_FILENAME);
        this.masterKey = deriveKey(masterSecret);
        load();
    }

    /**
     * Stores a sensitive secret and returns a reference URI.
     */
    public synchronized String storeSecret(String plainSecret) {
        if (plainSecret == null) return null;
        String id = UUID.randomUUID().toString();
        String ref = "cred://" + id;
        credentials.put(ref, plainSecret);
        save();
        log.debug("Stored encrypted credential with reference {}", ref);
        return ref;
    }

    /**
     * Retrieves secret by reference URI, or returns raw string if not a reference.
     */
    public String resolveSecret(String refOrPlain) {
        if (refOrPlain == null) return null;
        if (refOrPlain.startsWith("cred://")) {
            return credentials.getOrDefault(refOrPlain, "");
        }
        return refOrPlain;
    }

    public synchronized void deleteSecret(String ref) {
        if (ref != null && credentials.remove(ref) != null) {
            save();
        }
    }

    private SecretKey deriveKey(String secret) {
        try {
            byte[] salt = "lltool-salt-fixed".getBytes(StandardCharsets.UTF_8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, 65536, 256);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive vault master key", e);
        }
    }

    private void load() {
        if (!Files.exists(vaultFile)) return;
        try {
            byte[] encrypted = Files.readAllBytes(vaultFile);
            if (encrypted.length <= IV_LENGTH) return;

            byte[] iv = Arrays.copyOfRange(encrypted, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(encrypted, IV_LENGTH, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(cipherText);

            String text = new String(decrypted, StandardCharsets.UTF_8);
            credentials.clear();
            for (String line : text.split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    credentials.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
            log.info("Loaded {} encrypted credentials from vault", credentials.size());
        } catch (Exception e) {
            log.error("Failed to decrypt vault file {}: {}", vaultFile, e.getMessage());
        }
    }

    private void save() {
        try {
            StringBuilder sb = new StringBuilder();
            credentials.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));

            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            AtomicFileWriter.writeBytes(vaultFile, combined);
        } catch (Exception e) {
            log.error("Failed to encrypt and save vault: {}", e.getMessage(), e);
        }
    }
}

