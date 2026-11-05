package com.lltool.core.application.connection.usecase;

import com.lltool.core.application.connection.command.DuplicateConnectionCommand;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;

public interface DuplicateConnectionUseCase {
    Result<ConnectionProfileDto> execute(DuplicateConnectionCommand command);
}

