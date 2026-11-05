package com.lltool.core.application.connection.command;

public record DuplicateConnectionCommand(
        String sourceConnectionId,
        String newName
) {}

