package com.lltool.core.api.connection;

import com.lltool.core.api.request.CreateConnectionRequest;
import com.lltool.core.api.request.TestConnectionDraftRequest;
import com.lltool.core.api.request.UpdateConnectionRequest;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;

import java.util.List;

/**
 * Public Core Contract for Database Connection operations (Phase 6.2).
 */
public interface ConnectionApi {

    Result<ConnectionProfileDto> createConnection(CreateConnectionRequest request);

    Result<ConnectionProfileDto> updateConnection(UpdateConnectionRequest request);

    Result<Void> deleteConnection(String connectionId);

    Result<ConnectionProfileDto> duplicateConnection(String sourceConnectionId, String newName);

    Result<ConnectionProfileDto> getConnection(String connectionId);

    Result<List<ConnectionProfileDto>> getConnections();

    Result<Boolean> testConnection(TestConnectionDraftRequest request);
}

