package com.lltool.core.application.connection.usecase;

import com.lltool.core.application.connection.command.UpdateConnectionCommand;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;

public interface UpdateConnectionUseCase {
    Result<ConnectionProfileDto> execute(UpdateConnectionCommand command);
}

