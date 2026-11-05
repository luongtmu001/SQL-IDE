package com.lltool.core.application.connection.usecase;

import com.lltool.core.application.connection.command.CreateConnectionCommand;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;

public interface CreateConnectionUseCase {
    Result<ConnectionProfileDto> execute(CreateConnectionCommand command);
}

