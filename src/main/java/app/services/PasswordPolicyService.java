package app.services;

import app.framework.bootstrap.DesktopBootstrap;
import com.lltool.core.api.request.UpdateConnectionRequest;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

/**
 * Saved-password lifetime (Gate B 2026-11-05, Q3 = a): a saved password is stored encrypted by the
 * existing credential vault and is valid for 30 days counted from the last time it was saved.
 * After that — or when no password is saved — the connection asks again (SCR-DLG-002).
 * Passwords that are not saved live only in memory until the app closes.
 */
public final class PasswordPolicyService {

    public static final int VALID_DAYS = 30;
    private static final PasswordPolicyService INSTANCE = new PasswordPolicyService();

    private final Preferences prefs = Preferences.userNodeForPackage(PasswordPolicyService.class).node("password_saved_at");
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

    public long savedAt(String connectionId) {
        return prefs.getLong(key(connectionId), 0L);
    }

    public void markSaved(String connectionId) {
        try {
            prefs.putLong(key(connectionId), System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    public void clear(String connectionId) {
        try {
            prefs.remove(key(connectionId));
        } catch (Exception ignored) {
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
