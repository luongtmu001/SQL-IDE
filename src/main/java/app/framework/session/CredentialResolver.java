package app.framework.session;

import app.framework.bootstrap.DesktopBootstrap;
import app.services.PasswordPolicyService;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;

/**
 * One place that answers "which password does this profile use right now" (Phase 8 / D1).
 * Order (LLTool connection_manager.py): password typed for this connect → password kept in memory
 * for this run (DLG-002 without "save") → saved encrypted password from the vault.
 * Before Phase 8 the in-memory DLG-002 password was never used when connecting.
 */
public final class CredentialResolver {

    private CredentialResolver() {
    }

    public static String resolve(ConnectionProfileDto profile, String explicitPassword) {
        if (explicitPassword != null && !explicitPassword.isEmpty()) return explicitPassword;
        if (profile == null) return "";
        String session = PasswordPolicyService.getInstance().sessionPassword(profile.id());
        if (session != null && !session.isEmpty()) return session;
        if (profile.credentialRef() != null && !profile.credentialRef().isBlank()) {
            try {
                String saved = DesktopBootstrap.getInstance().getCredentialVault().resolveSecret(profile.credentialRef());
                if (saved != null) return saved;
            } catch (Exception ignored) {
                // vault unavailable: fall through to "no password"
            }
        }
        return "";
    }
}
