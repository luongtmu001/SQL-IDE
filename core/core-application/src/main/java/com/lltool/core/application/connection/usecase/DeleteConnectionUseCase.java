package com.lltool.core.application.connection.usecase;

import com.lltool.core.application.connection.command.DeleteConnectionCommand;
import com.lltool.core.common.result.Result;

public interface DeleteConnectionUseCase {
    Result<Void> execute(DeleteConnectionCommand command);
}

