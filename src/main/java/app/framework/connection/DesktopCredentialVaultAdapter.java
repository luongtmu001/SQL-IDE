package app.framework.connection;

import com.lltool.core.application.connection.port.CredentialVaultPort;
import com.lltool.infrastructure.security.SecureCredentialVault;

import java.util.Objects;
import java.util.Optional;

/**
 * Desktop Adapter connecting Core CredentialVaultPort to Infrastructure SecureCredentialVault (AES-256-GCM).
 */
public class DesktopCredentialVaultAdapter implements CredentialVaultPort {

    private final SecureCredentialVault vault;

    public DesktopCredentialVaultAdapter(SecureCredentialVault vault) {
        this.vault = Objects.requireNonNull(vault, "vault cannot be null");
    }

    @Override
    public String storeSecret(String secret) {
        return vault.storeSecret(secret);
    }

    @Override
    public Optional<String> getSecret(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Optional.empty();
        }
        String resolved = vault.resolveSecret(credentialRef);
        if (resolved == null || resolved.isEmpty() || resolved.equals(credentialRef)) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    @Override
    public void deleteSecret(String credentialRef) {
        if (credentialRef != null && !credentialRef.isBlank()) {
            vault.deleteSecret(credentialRef);
        }
    }
}

