package com.lltool.infrastructure.security;

import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import com.lltool.infrastructure.filesystem.AtomicFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Per-installation master secret of the credential vault.
 *
 * <p>A random 256-bit secret is generated on first start and stored in {@code config/vault.key}:
 * on Windows protected with DPAPI (current user only), elsewhere as {@code RAW1:} (plain, so protect the
 * folder with OS permissions). Replaces the constant that used to be compiled into the application.
 */
final class VaultKeyProvider {

    static final String KEY_FILE = "vault.key";
    private static final Logger log = LoggerFactory.getLogger(VaultKeyProvider.class);
    private static final String DPAPI = "DPAPI1:";
    private static final String RAW = "RAW1:";

    private VaultKeyProvider() {
    }

    /** Secret to derive the AES key from. Never null. */
    static String resolveSecret(AppDirectoryManager dm) {
        Path file = dm.getConfigDir().resolve(KEY_FILE);
        if (Files.exists(file)) {
            try {
                String secret = decode(Files.readString(file, StandardCharsets.UTF_8).trim());
                if (secret != null) {
                    return secret;
                }
            } catch (IOException | RuntimeException e) {
                log.error("Cannot read vault key {}: {}", file, e.getMessage());
            }
            // Unreadable (other Windows account, corrupt): keep the old files aside, start a fresh vault.
            quarantine(dm, file);
        }
        return createNew(file);
    }

    private static String createNew(Path file) {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String secret = Base64.getEncoder().encodeToString(raw);
        String stored = RAW + secret;
        if (isWindows()) {
            try {
                stored = DPAPI + runDpapi(true, secret);
            } catch (Exception e) {
                log.warn("DPAPI not available ({}); vault key stored without OS protection", e.getMessage());
            }
        }
        try {
            AtomicFileWriter.writeString(file, stored);
        } catch (Exception e) {
            throw new RuntimeException("Cannot save vault key", e);
        }
        return secret;
    }

    private static String decode(String stored) throws IOException {
        if (stored.startsWith(RAW)) {
            return stored.substring(RAW.length());
        }
        if (stored.startsWith(DPAPI)) {
            try {
                return runDpapi(false, stored.substring(DPAPI.length()));
            } catch (Exception e) {
                throw new IOException("DPAPI unprotect failed: " + e.getMessage(), e);
            }
        }
        return null;
    }

    private static void quarantine(AppDirectoryManager dm, Path keyFile) {
        String stamp = String.valueOf(System.currentTimeMillis());
        try {
            Files.move(keyFile, keyFile.resolveSibling(KEY_FILE + ".unreadable-" + stamp));
            Path vault = dm.getConfigDir().resolve("vault.enc");
            if (Files.exists(vault)) {
                Files.move(vault, vault.resolveSibling("vault.enc.unreadable-" + stamp));
            }
            log.warn("Old vault set aside (.unreadable-{}); saved passwords must be entered again", stamp);
        } catch (IOException e) {
            log.error("Cannot set aside unreadable vault key: {}", e.getMessage());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** DPAPI through Windows PowerShell (no extra library). Data goes in via an environment variable. */
    private static String runDpapi(boolean protect, String base64In) throws IOException, InterruptedException {
        String op = protect ? "Protect" : "Unprotect";
        String script = "Add-Type -AssemblyName System.Security;"
                + "$d=[Convert]::FromBase64String($env:SQLIDE_DPAPI_IN);"
                + "$r=[Security.Cryptography.ProtectedData]::" + op
                + "($d,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);"
                + "[Convert]::ToBase64String($r)";
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", script);
        pb.environment().put("SQLIDE_DPAPI_IN", protect
                ? Base64.getEncoder().encodeToString(base64In.getBytes(StandardCharsets.UTF_8)) : base64In);
        Process p = pb.start();
        p.getOutputStream().close();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("PowerShell timed out");
        }
        String text = new String(out, StandardCharsets.UTF_8).trim();
        if (p.exitValue() != 0 || text.isEmpty()) {
            throw new IOException("PowerShell exit " + p.exitValue());
        }
        // Unprotect returns the original bytes in Base64: that is our secret text, encoded once more.
        return protect ? text : new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
