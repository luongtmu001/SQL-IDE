package com.lltool.core.domain.connection.valueobject;

/**
 * State lifecycle for active connection sessions (Phase 6.2).
 */
public enum ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    RECONNECTING,
    ERROR;

    public boolean isOnline() {
        return this == CONNECTED;
    }

    public boolean isBusy() {
        return this == CONNECTING || this == DISCONNECTING || this == RECONNECTING;
    }
}

