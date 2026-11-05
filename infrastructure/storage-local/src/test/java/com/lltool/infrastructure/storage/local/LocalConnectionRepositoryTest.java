package com.lltool.infrastructure.storage.local;

import com.lltool.core.domain.connection.model.ConnectionProfile;
import com.lltool.core.domain.connection.valueobject.*;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalConnectionRepositoryTest {

    @Test
    @DisplayName("LocalConnectionRepository persists connection profile to JSON and retrieves it")
    void testSaveAndRetrieveConnection(@TempDir Path tempDir) {
        AppDirectoryManager dirManager = AppDirectoryManager.createIsolated(tempDir);
        LocalConnectionRepository repository = new LocalConnectionRepository(dirManager);

        ConnectionId id = ConnectionId.generate();
        Instant now = Instant.now();

        ConnectionProfile profile = ConnectionProfile.create(
                id,
                ConnectionName.of("PGE_BRAVO_DEV"),
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
                Map.of("application_name", "LLTool IDE"),
                now
        );

        repository.save(profile);

        assertTrue(repository.existsById(id));
        assertTrue(repository.existsByName(ConnectionName.of("PGE_BRAVO_DEV")));

        Optional<ConnectionProfile> foundOpt = repository.findById(id);
        assertTrue(foundOpt.isPresent());
        ConnectionProfile found = foundOpt.get();

        assertEquals("PGE_BRAVO_DEV", found.getName().value());
        assertEquals("BRAVO-SERVER/POSTGRE", found.getGroup());
        assertEquals("DEV", found.getEnvironment());
        assertFalse(found.isReadOnly());
        assertEquals(25432, found.getPort());
        assertEquals("B10_PhucLoi", found.getDatabaseName());
        assertEquals("luonght", found.getUsername());
        assertEquals("cred://abcd-1234", found.getCredentialRef().value());
        assertEquals(15, found.getTimeoutSeconds());
        assertTrue(found.isTrustServerCertificate());

        // Delete
        repository.delete(id);
        assertFalse(repository.existsById(id));
    }
}

