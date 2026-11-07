package app.services;

import app.framework.bootstrap.DesktopBootstrap;
import com.lltool.core.api.request.UpdateConnectionRequest;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

/**
 * Saved-password lifetime (Gate B 2026-11-05, Q3 = a): a saved password is stored encrypted by the
 * existing credential vault and is valid for 30 days counted from the last time it was saved.
 * After that — or when no password is saved — the connection asks again (SCR-DLG-002).
 * Passwords that are not saved live only in memory until the app closes.
 *
 * <p>The "saved at" times live in {@code config/password-saved-at.json} of the user's data folder
 * (older versions kept them in the Windows registry; they are copied over once).
 */
public final class PasswordPolicyService {

    public static final int VALID_DAYS = 30;
    private static final PasswordPolicyService INSTANCE = new PasswordPolicyService();

    private final Preferences prefs = Preferences.userNodeForPackage(PasswordPolicyService.class).node("password_saved_at");
    private final Map<String, Long> savedAtMap = new ConcurrentHashMap<>();
    private Path file;
    private boolean loaded;
    private final Map<String, String> sessionPasswords = new ConcurrentHashMap<>();

    private PasswordPolicyService() {
    }

    public static PasswordPolicyService getInstance() {
        return INSTANCE;
    }

    private static String key(String id) {
        String k = id == null ? "" : id.replace('/', '_');
        return k.length() > Preferences.MAX_KEY_LENGTH ? k.substring(0, Preferences.MAX_KEY_LENGTH) : k;
    }

    /** Loads the times on first use (the data folder is not known when this class is created). */
    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            file = DesktopBootstrap.getInstance().getDirectoryManager().getConfigDir().resolve("password-saved-at.json");
        } catch (Exception e) {
            file = null;   // no data folder: fall back to the registry like before
        }
        if (file != null && Files.exists(file)) {
            try {
                Map<String, Number> m = new com.fasterxml.jackson.databind.ObjectMapper().readValue(file.toFile(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Number>>() { });
                m.forEach((k, v) -> savedAtMap.put(k, v.longValue()));
                return;
            } catch (Exception ignored) {
                // unreadable: start again from the registry copy below
            }
        }
        try {
            for (String k : prefs.keys()) {
                long v = prefs.getLong(k, 0L);
                if (v > 0) {
                    savedAtMap.put(k, v);
                }
            }
        } catch (Exception ignored) {
        }
        persist();
    }

    private synchronized void persist() {
        if (file == null) {
            return;
        }
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(new java.util.TreeMap<>(savedAtMap));
            com.lltool.infrastructure.filesystem.AtomicFileWriter.writeString(file, json);
        } catch (Exception ignored) {
            // the time is kept in memory; it will be written by the next change
        }
    }

    public long savedAt(String connectionId) {
        ensureLoaded();
        return savedAtMap.getOrDefault(key(connectionId), 0L);
    }

    public void markSaved(String connectionId) {
        ensureLoaded();
        savedAtMap.put(key(connectionId), System.currentTimeMillis());
        persist();
    }

    public void clear(String connectionId) {
        ensureLoaded();
        if (savedAtMap.remove(key(connectionId)) != null) {
            persist();
        }
        sessionPasswords.remove(connectionId);
    }

    /** Days left before the saved password expires (0 when expired or unknown). */
    public int daysLeft(String connectionId) {
        long at = savedAt(connectionId);
        if (at <= 0) return VALID_DAYS;
        long days = Duration.ofMillis(System.currentTimeMillis() - at).toDays();
        return (int) Math.max(0, VALID_DAYS - days);
    }

    public boolean isExpired(String connectionId) {
        long at = savedAt(connectionId);
        return at > 0 && Duration.ofMillis(System.currentTimeMillis() - at).toDays() >= VALID_DAYS;
    }

    public boolean usesPassword(ConnectionProfileDto p) {
        if (p == null) return false;
        String type = p.databaseType() != null ? p.databaseType().toUpperCase() : "";
        if (type.contains("SQLITE")) return false;
        return !"WINDOWS_AUTH".equalsIgnoreCase(p.authType()) && !"NONE".equalsIgnoreCase(p.authType());
    }

    /** True when connecting must ask for the password first. */
    public boolean needsPrompt(ConnectionProfileDto p) {
        if (!usesPassword(p)) return false;
        if (sessionPasswords.containsKey(p.id())) return false;
        boolean hasSaved = p.savePassword() && p.credentialRef() != null && !p.credentialRef().isBlank();
        if (!hasSaved) return true;
        if (savedAt(p.id()) <= 0) {
            // saved before this policy existed: start the 30-day clock now instead of asking at once
            markSaved(p.id());
            return false;
        }
        return isExpired(p.id());
    }

    /**
     * Phase 8: the server refused the login, so the in-memory password is wrong: forget it so the next
     * connect asks again (DLG-002). Saved passwords stay; the user can retype and save over them.
     */
    public void forgetSessionPasswordIfAuthError(String connectionId, String errorMessage) {
        if (connectionId == null || errorMessage == null) return;
        String m = errorMessage.toLowerCase();
        if (m.contains("password") || m.contains("login failed") || m.contains("authentication")
                || m.contains("mật khẩu") || m.contains("28p01") || m.contains("18456")) {
            sessionPasswords.remove(connectionId);
        }
    }

    public String sessionPassword(String connectionId) {
        return sessionPasswords.get(connectionId);
    }

    /** Applies the DLG-002 answer: save encrypted for 30 days, or keep in memory only. */
    public String applyPrompt(ConnectionProfileDto p, String password, boolean save) {
        if (!save) {
            sessionPasswords.put(p.id(), password);
            return null;
        }
        try {
            var req = new UpdateConnectionRequest(
                    p.id(), p.name(), p.group(), p.environment(), p.readOnly(), p.databaseType(),
                    p.host(), p.port(), p.databaseName(), p.sqliteFilePath(), p.username(), p.authType(),
                    password, true, p.timeoutSeconds(), p.trustServerCertificate(),
                    p.properties() != null ? p.properties() : Map.of());
            var res = DesktopBootstrap.getInstance().getConnectionApi().updateConnection(req);
            if (res.isSuccess()) {
                markSaved(p.id());
                sessionPasswords.put(p.id(), password);
                return null;
            }
            sessionPasswords.put(p.id(), password);
            return res.getError().map(e -> e.message()).orElse("Không lưu được mật khẩu.");
        } catch (Exception e) {
            sessionPasswords.put(p.id(), password);
            return "Không lưu được mật khẩu: " + e.getMessage();
        }
    }
}
