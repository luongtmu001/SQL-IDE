package com.lltool.core.application.connection.port;

import com.lltool.core.common.result.Result;
import com.lltool.core.domain.connection.model.ConnectionProfile;

/**
 * Outbound Port for testing database connectivity.
 */
public interface DatabaseConnectionTesterPort {

    Result<Boolean> test(ConnectionProfile profile, String resolvedPassword);
}

