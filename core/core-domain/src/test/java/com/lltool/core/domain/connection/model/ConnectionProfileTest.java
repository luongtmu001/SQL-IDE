package com.lltool.core.domain.connection.model;

import com.lltool.core.domain.connection.valueobject.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionProfileTest {

    @Test
    @DisplayName("ConnectionProfile creates and updates properly without exposing plaintext secrets")
    void testConnectionProfileLifecycle() {
        ConnectionId id = ConnectionId.generate();
        ConnectionName name = ConnectionName.of("PGE_BRAVO_DEV");
        Instant now = Instant.now();

        ConnectionProfile profile = ConnectionProfile.create(
                id,
                name,
                "BRAVO-SERVER/POSTGRE",
                "DEV",
                false,
                ConnectionDatabaseType.POSTGRESQL,
                "192.168.135.90",
                25432,
                "B10_PhucLoi",
                "",
                "luonght",
                AuthenticationType.PASSWORD,
                CredentialReference.of("cred://abcd-1234"),
                true,
                15,
                true,
                Map.of("ssl", "true"),
                now
        );

        assertEquals("PGE_BRAVO_DEV", profile.getName().value());
        assertEquals("BRAVO-SERVER/POSTGRE", profile.getGroup());
        assertEquals("DEV", profile.getEnvironment());
        assertFalse(profile.isReadOnly());
        assertEquals("192.168.135.90", profile.getHost());
        assertEquals(25432, profile.getPort());
        assertEquals("B10_PhucLoi", profile.getDatabaseName());
        assertEquals("luonght", profile.getUsername());
        assertEquals("cred://abcd-1234", profile.getCredentialRef().value());
        assertTrue(profile.isSavePassword());
        assertEquals(15, profile.getTimeoutSeconds());
        assertTrue(profile.isTrustServerCertificate());

        // Update
        Instant later = now.plusSeconds(30);
        profile.update(
                ConnectionName.of("Renamed DB"),
                "BRAVO-SERVER/POSTGRE",
                "PROD",
                true,
                ConnectionDatabaseType.POSTGRESQL,
                "db2.internal",
                5433,
                "B10_PhucLoi_v2",
                "",
                "luonght2",
                AuthenticationType.PASSWORD,
                CredentialReference.of("cred://new-token"),
                true,
                20,
                true,
                Map.of(),
                later
        );

        assertEquals("Renamed DB", profile.getName().value());
        assertEquals("PROD", profile.getEnvironment());
        assertTrue(profile.isReadOnly());
        assertEquals("db2.internal", profile.getHost());
        assertEquals(5433, profile.getPort());
        assertEquals("cred://new-token", profile.getCredentialRef().value());
        assertEquals(later, profile.getUpdatedAt());
    }

    @Test
    @DisplayName("ConnectionName validation enforces length and rejects illegal characters")
    void testConnectionNameValidation() {
        assertThrows(IllegalArgumentException.class, () -> ConnectionName.of(""));
        assertThrows(IllegalArgumentException.class, () -> ConnectionName.of("Bad/Name"));
        assertThrows(IllegalArgumentException.class, () -> ConnectionName.of("Bad:Name"));
        assertDoesNotThrow(() -> ConnectionName.of("Standard Database (1)"));
    }
}
