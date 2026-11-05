package com.lltool.core.domain.workspace.valueobject;

import java.util.Objects;

/**
 * Value Object representing an open SQL Editor Tab state in the Workspace.
 */
public record TabState(
        String tabId,
        String title,
        String filePath,
        String connectionId,
        boolean active
) {
    public TabState {
        Objects.requireNonNull(tabId, "tabId cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
    }

    public static TabState of(String tabId, String title, String filePath, String connectionId, boolean active) {
        return new TabState(tabId, title, filePath, connectionId, active);
    }
}

