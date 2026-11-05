package com.lltool.core.application.connection.port;

import java.util.Optional;

/**
 * Outbound Port for secure credential vault integration.
 * In Clean Architecture, Application layer does not depend on specific crypto infrastructure.
 */
public interface CredentialVaultPort {

    String storeSecret(String secret);

    Optional<String> getSecret(String credentialRef);

    void deleteSecret(String credentialRef);
}

