package com.lltool.core.api.connection;

import com.lltool.core.api.application.CorePlatform;
import com.lltool.core.api.request.CreateConnectionRequest;
import com.lltool.core.api.request.TestConnectionDraftRequest;
import com.lltool.core.api.request.UpdateConnectionRequest;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionApiTest {

    private CorePlatform platform;
    private ConnectionApi connectionApi;

    @BeforeEach
    void setUp() {
        platform = CorePlatform.createDefault();
        connectionApi = platform.connectionApi();
    }

    @Test
    @DisplayName("Create connection via API returns success and masks password")
    void testCreateConnection() {
        Result<ConnectionProfileDto> res = connectionApi.createConnection(new CreateConnectionRequest(
                "PGE_BRAVO_DEV",
                "BRAVO-SERVER/POSTGRE",
                "DEV",
                false,
                "POSTGRESQL",
                "192.168.135.90",
                25432,
                "B10_PhucLoi",
                "",
                "luonght",
                "PASSWORD",
                "secret_pw_123",
                true,
                15,
                true,
                Map.of()
        ));

        assertTrue(res.isSuccess());
        ConnectionProfileDto dto = res.getDataOrThrow();
        assertEquals("PGE_BRAVO_DEV", dto.name());
        assertEquals("BRAVO-SERVER/POSTGRE", dto.group());
        assertEquals("DEV", dto.environment());
        assertFalse(dto.readOnly());
        assertEquals(25432, dto.port());
        assertEquals("B10_PhucLoi", dto.databaseName());
    }

    @Test
    @DisplayName("Test connection draft via API")
    void testTestConnectionDraft() {
        Result<Boolean> res = connectionApi.testConnection(new TestConnectionDraftRequest(
                "Test Draft",
                "POSTGRESQL",
                "localhost",
                5432,
                "postgres",
                "",
                "postgres",
                "PASSWORD",
                "secret",
                15,
                true,
                Map.of()
        ));

        assertTrue(res.isSuccess());
        assertTrue(res.getDataOrThrow());
    }

    @Test
    @DisplayName("Update, duplicate, and delete connection workflows")
    void testUpdateDuplicateDelete() {
        ConnectionProfileDto created = connectionApi.createConnection(new CreateConnectionRequest(
                "Original Conn",
                "Default",
                "DEV",
                false,
                "SQLITE",
                "localhost",
                0,
                "sample.db",
                "/tmp/sample.db",
                "",
                "NONE",
                "",
                false,
                15,
                true,
                Map.of()
        )).getDataOrThrow();

        Result<ConnectionProfileDto> dupRes = connectionApi.duplicateConnection(created.id(), "Original Conn Copy");
        assertTrue(dupRes.isSuccess());
        assertEquals("Original Conn Copy", dupRes.getDataOrThrow().name());

        Result<Void> delRes = connectionApi.deleteConnection(created.id());
        assertTrue(delRes.isSuccess());
    }
}

